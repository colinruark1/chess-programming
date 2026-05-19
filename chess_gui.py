#!/usr/bin/env python3
"""
Chess GUI for playing against MyBot engine.
Supports: Human vs Human, Human vs Computer, Computer vs Computer
With initial setup screen for player types and time controls.
"""

import pygame
import chess
import chess.svg
import subprocess
import threading
import queue
import time
from pathlib import Path
from typing import Optional, Tuple, List
from enum import Enum


class PlayerType(Enum):
    HUMAN = "human"
    COMPUTER = "computer"


class GameMode(Enum):
    HUMAN_VS_HUMAN = "Human vs Human"
    HUMAN_VS_COMPUTER = "Human vs Computer"
    COMPUTER_VS_HUMAN = "Computer vs Human"
    COMPUTER_VS_COMPUTER = "Computer vs Computer"


class TimeControl:
    """Manages time controls for timed games.

    Runs its own background thread so the countdown is independent of both
    the draw loop and the computer's thinking thread.
    """

    def __init__(self, minutes_per_player: int = 5, increment_seconds: int = 0):
        self.enabled = minutes_per_player > 0
        self.white_time_ms = minutes_per_player * 60 * 1000
        self.black_time_ms = minutes_per_player * 60 * 1000
        self.increment_ms = increment_seconds * 1000
        self.active = False
        self._active_side = None   # chess.WHITE or chess.BLACK
        self._last_tick = None
        self._lock = threading.Lock()
        self._stop_event = threading.Event()
        self._thread = None

    def start_clock(self, side: bool = chess.WHITE):
        """Start the clock for the given side on a background thread."""
        with self._lock:
            self._active_side = side
            self._last_tick = time.time()
            self.active = True
        self._stop_event.clear()
        if self._thread is None or not self._thread.is_alive():
            self._thread = threading.Thread(target=self._run, daemon=True)
            self._thread.start()

    def stop_clock(self):
        """Stop the clock and shut down the background thread."""
        with self._lock:
            self.active = False
        self._stop_event.set()

    def _run(self):
        """Background thread: tick every 100 ms."""
        while not self._stop_event.wait(0.1):
            with self._lock:
                if not self.active or self._last_tick is None or self._active_side is None:
                    continue
                now = time.time()
                elapsed_ms = int((now - self._last_tick) * 1000)
                self._last_tick = now
                if self._active_side == chess.WHITE:
                    self.white_time_ms = max(0, self.white_time_ms - elapsed_ms)
                else:
                    self.black_time_ms = max(0, self.black_time_ms - elapsed_ms)

    def switch_side(self, new_side: bool, moved_side: bool):
        """After a move: add increment to the side that moved, start the other side's clock."""
        with self._lock:
            if moved_side == chess.WHITE:
                self.white_time_ms += self.increment_ms
            else:
                self.black_time_ms += self.increment_ms
            self._active_side = new_side
            self._last_tick = time.time()

    def get_times(self) -> Tuple[int, int]:
        """Thread-safe snapshot of (white_time_ms, black_time_ms)."""
        with self._lock:
            return self.white_time_ms, self.black_time_ms

    def format_time(self, time_ms: int) -> str:
        """Format time in MM:SS.d format."""
        if time_ms < 0:
            time_ms = 0

        total_seconds = time_ms // 1000
        tenths = (time_ms % 1000) // 100
        minutes = total_seconds // 60
        seconds = total_seconds % 60

        return f"{minutes:02d}:{seconds:02d}.{tenths}"


class EngineInterface:
    """Manages communication with the UCI chess engine."""

    def __init__(self, jar_path: str):
        self.jar_path = jar_path
        self.process = None
        self.output_queue = queue.Queue()
        self.reader_thread = None
        self.debug_info = {
            "depth": 0,
            "score_cp": 0,
            "time_ms": 0,
            "nodes": 0,
            "nps": 0,
            "pv": "",
            "tt_hits": 0,
            "tt_misses": 0,
            "tt_hit_rate": 0.0
        }

    def start(self):
        """Start the chess engine process."""
        self.process = subprocess.Popen(
            ["java", "-jar", self.jar_path, "--uci"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        self.reader_thread = threading.Thread(target=self._read_output, daemon=True)
        self.reader_thread.start()

        # Initialize engine
        self.send_command("uci")
        self.wait_for("uciok")
        self.send_command("isready")
        self.wait_for("readyok")

    def _read_output(self):
        """Read engine output in a separate thread."""
        while self.process and self.process.poll() is None:
            line = self.process.stdout.readline()
            if line:
                self.output_queue.put(line.strip())

    def send_command(self, cmd: str):
        """Send a command to the engine."""
        if self.process:
            self.process.stdin.write(cmd + "\n")
            self.process.stdin.flush()

    def wait_for(self, expected: str, timeout: float = 5.0) -> Optional[str]:
        """Wait for a specific response from the engine."""
        import time
        start = time.time()
        while time.time() - start < timeout:
            try:
                line = self.output_queue.get(timeout=0.1)
                if expected in line:
                    return line
            except queue.Empty:
                continue
        return None

    def get_all_output(self) -> List[str]:
        """Get all queued output."""
        lines = []
        while not self.output_queue.empty():
            try:
                lines.append(self.output_queue.get_nowait())
            except queue.Empty:
                break
        return lines

    def set_position(self, fen: str, moves: List[str] = None):
        """Set the current board position."""
        if moves:
            moves_str = " moves " + " ".join(moves)
        else:
            moves_str = ""
        self.send_command(f"position fen {fen}{moves_str}")

    def reset_game(self):
        """Reset the engine for a new game - single command to initialize everything."""
        self.send_command("ucinewgame")
        self.send_command("position startpos")
        # Reset debug info
        self.debug_info = {
            "depth": 0,
            "score_cp": 0,
            "time_ms": 0,
            "nodes": 0,
            "nps": 0,
            "pv": "",
            "tt_hits": 0,
            "tt_misses": 0,
            "tt_hit_rate": 0.0
        }

    def parse_info_line(self, line: str):
        """Parse UCI info line and update debug_info."""
        if not line.startswith("info"):
            return

        tokens = line.split()
        i = 1
        while i < len(tokens):
            if tokens[i] == "depth" and i + 1 < len(tokens):
                self.debug_info["depth"] = int(tokens[i + 1])
                i += 2
            elif tokens[i] == "score" and i + 2 < len(tokens):
                if tokens[i + 1] == "cp":
                    self.debug_info["score_cp"] = int(tokens[i + 2])
                    i += 3
                elif tokens[i + 1] == "mate":
                    mate_in = int(tokens[i + 2])
                    self.debug_info["score_cp"] = 10000 * (1 if mate_in > 0 else -1)
                    i += 3
                else:
                    i += 1
            elif tokens[i] == "time" and i + 1 < len(tokens):
                self.debug_info["time_ms"] = int(tokens[i + 1])
                i += 2
            elif tokens[i] == "nodes" and i + 1 < len(tokens):
                self.debug_info["nodes"] = int(tokens[i + 1])
                i += 2
            elif tokens[i] == "nps" and i + 1 < len(tokens):
                self.debug_info["nps"] = int(tokens[i + 1])
                i += 2
            elif tokens[i] == "pv":
                # Everything after "pv" is the principal variation
                self.debug_info["pv"] = " ".join(tokens[i + 1:i + 6])  # First 5 moves
                break
            elif tokens[i] == "string":
                # Parse custom info strings like "TT hits: ..."
                rest = " ".join(tokens[i + 1:])
                if "TT hits:" in rest:
                    import re
                    hits_match = re.search(r'hits:\s*(\d+)', rest)
                    misses_match = re.search(r'misses:\s*(\d+)', rest)
                    rate_match = re.search(r'hit rate:\s*([\d.]+)', rest)
                    if hits_match:
                        self.debug_info["tt_hits"] = int(hits_match.group(1))
                    if misses_match:
                        self.debug_info["tt_misses"] = int(misses_match.group(1))
                    if rate_match:
                        self.debug_info["tt_hit_rate"] = float(rate_match.group(1))
                break
            else:
                i += 1

    def get_best_move(self, timeout_ms: int = 5000) -> Optional[str]:
        """Get the best move from the engine."""
        self.send_command(f"go movetime {timeout_ms}")

        # Wait for bestmove response
        import time
        start = time.time()
        while time.time() - start < (timeout_ms / 1000.0 + 2.0):
            try:
                line = self.output_queue.get(timeout=0.1)
                # Parse info lines for debug display
                if line.startswith("info"):
                    self.parse_info_line(line)
                elif line.startswith("bestmove"):
                    parts = line.split()
                    if len(parts) >= 2:
                        return parts[1]
            except queue.Empty:
                continue
        return None

    def stop(self):
        """Stop the engine process."""
        if self.process:
            self.send_command("quit")
            self.process.wait(timeout=2.0)
            self.process = None


class GameSetupScreen:
    """Initial setup screen for configuring the game."""

    WINDOW_WIDTH = 600
    WINDOW_HEIGHT = 500

    COLOR_BACKGROUND = (50, 50, 50)
    COLOR_TEXT = (255, 255, 255)
    COLOR_BUTTON = (70, 130, 180)
    COLOR_BUTTON_HOVER = (100, 160, 210)
    COLOR_BUTTON_SELECTED = (50, 100, 150)
    COLOR_PANEL = (40, 40, 40)

    def __init__(self):
        pygame.init()
        self.screen = pygame.display.set_mode((self.WINDOW_WIDTH, self.WINDOW_HEIGHT))
        pygame.display.set_caption("Chess Game Setup")

        self.font_title = pygame.font.SysFont('Arial', 32, bold=True)
        self.font_label = pygame.font.SysFont('Arial', 18)
        self.font_button = pygame.font.SysFont('Arial', 16)

        # Game settings
        self.white_player = PlayerType.HUMAN
        self.black_player = PlayerType.COMPUTER
        self.use_time_control = False
        self.time_minutes = 5
        self.increment_seconds = 0

        self.done = False
        self.start_game = False

    def draw_button(self, rect: pygame.Rect, text: str, selected: bool = False) -> bool:
        """Draw a button and return True if hovered."""
        mouse_pos = pygame.mouse.get_pos()
        hovered = rect.collidepoint(mouse_pos)

        if selected:
            color = self.COLOR_BUTTON_SELECTED
        elif hovered:
            color = self.COLOR_BUTTON_HOVER
        else:
            color = self.COLOR_BUTTON

        pygame.draw.rect(self.screen, color, rect, border_radius=5)
        pygame.draw.rect(self.screen, self.COLOR_TEXT, rect, 2, border_radius=5)

        text_surface = self.font_button.render(text, True, self.COLOR_TEXT)
        text_rect = text_surface.get_rect(center=rect.center)
        self.screen.blit(text_surface, text_rect)

        return hovered

    def draw_option_buttons(self, y: int, label: str, options: List[Tuple[str, any]], current_value: any) -> Optional[any]:
        """Draw a row of option buttons and return the selected value if clicked."""
        label_surface = self.font_label.render(label, True, self.COLOR_TEXT)
        self.screen.blit(label_surface, (50, y))

        button_width = 120
        button_height = 35
        button_spacing = 10
        start_x = 50
        buttons_y = y + 30

        clicked_value = None

        for i, (option_text, option_value) in enumerate(options):
            x = start_x + i * (button_width + button_spacing)
            rect = pygame.Rect(x, buttons_y, button_width, button_height)

            selected = (option_value == current_value)
            self.draw_button(rect, option_text, selected)

            # Check for click
            if pygame.mouse.get_pressed()[0] and rect.collidepoint(pygame.mouse.get_pos()):
                if not hasattr(self, '_last_click_time') or time.time() - self._last_click_time > 0.2:
                    clicked_value = option_value
                    self._last_click_time = time.time()

        return clicked_value

    def draw_number_input(self, y: int, label: str, value: int, min_val: int, max_val: int) -> Optional[int]:
        """Draw a number input with +/- buttons."""
        label_surface = self.font_label.render(label, True, self.COLOR_TEXT)
        self.screen.blit(label_surface, (50, y))

        button_size = 35
        center_x = 200
        buttons_y = y + 30

        # Minus button
        minus_rect = pygame.Rect(center_x - 60, buttons_y, button_size, button_size)
        self.draw_button(minus_rect, "-")

        # Value display
        value_rect = pygame.Rect(center_x - 20, buttons_y, 80, button_size)
        pygame.draw.rect(self.screen, self.COLOR_PANEL, value_rect, border_radius=5)
        pygame.draw.rect(self.screen, self.COLOR_TEXT, value_rect, 2, border_radius=5)
        value_text = self.font_button.render(str(value), True, self.COLOR_TEXT)
        value_text_rect = value_text.get_rect(center=value_rect.center)
        self.screen.blit(value_text, value_text_rect)

        # Plus button
        plus_rect = pygame.Rect(center_x + 65, buttons_y, button_size, button_size)
        self.draw_button(plus_rect, "+")

        # Check for clicks
        new_value = value
        if pygame.mouse.get_pressed()[0]:
            if not hasattr(self, '_last_click_time') or time.time() - self._last_click_time > 0.2:
                mouse_pos = pygame.mouse.get_pos()
                if minus_rect.collidepoint(mouse_pos) and value > min_val:
                    new_value = value - 1
                    self._last_click_time = time.time()
                elif plus_rect.collidepoint(mouse_pos) and value < max_val:
                    new_value = value + 1
                    self._last_click_time = time.time()

        return new_value if new_value != value else None

    def run(self) -> Optional[dict]:
        """Run the setup screen and return game settings."""
        clock = pygame.time.Clock()

        while not self.done:
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    self.done = True
                    return None

            self.screen.fill(self.COLOR_BACKGROUND)

            # Title
            title = self.font_title.render("Chess Game Setup", True, self.COLOR_TEXT)
            title_rect = title.get_rect(center=(self.WINDOW_WIDTH // 2, 40))
            self.screen.blit(title, title_rect)

            # White player selection
            y = 100
            white_result = self.draw_option_buttons(
                y, "White Player:",
                [("Human", PlayerType.HUMAN), ("Computer", PlayerType.COMPUTER)],
                self.white_player
            )
            if white_result is not None:
                self.white_player = white_result

            # Black player selection
            y += 90
            black_result = self.draw_option_buttons(
                y, "Black Player:",
                [("Human", PlayerType.HUMAN), ("Computer", PlayerType.COMPUTER)],
                self.black_player
            )
            if black_result is not None:
                self.black_player = black_result

            # Time control toggle
            y += 90
            time_result = self.draw_option_buttons(
                y, "Time Control:",
                [("Untimed", False), ("Timed", True)],
                self.use_time_control
            )
            if time_result is not None:
                self.use_time_control = time_result

            # Time settings (only if timed is selected)
            if self.use_time_control:
                y += 90
                time_change = self.draw_number_input(y, "Minutes per player:", self.time_minutes, 1, 60)
                if time_change is not None:
                    self.time_minutes = time_change

                y += 90
                inc_change = self.draw_number_input(y, "Increment (seconds):", self.increment_seconds, 0, 60)
                if inc_change is not None:
                    self.increment_seconds = inc_change

            # Start button
            start_y = self.WINDOW_HEIGHT - 70
            start_rect = pygame.Rect(self.WINDOW_WIDTH // 2 - 100, start_y, 200, 50)
            self.draw_button(start_rect, "Start Game")

            if pygame.mouse.get_pressed()[0] and start_rect.collidepoint(pygame.mouse.get_pos()):
                if not hasattr(self, '_last_click_time') or time.time() - self._last_click_time > 0.2:
                    self.start_game = True
                    self.done = True
                    self._last_click_time = time.time()

            pygame.display.flip()
            clock.tick(60)

        if self.start_game:
            return {
                'white_player': self.white_player,
                'black_player': self.black_player,
                'use_time_control': self.use_time_control,
                'time_minutes': self.time_minutes if self.use_time_control else 0,
                'increment_seconds': self.increment_seconds if self.use_time_control else 0
            }

        return None


class ChessGUI:
    """Main GUI class for the chess game."""

    # Configuration - easy to modify for UI changes
    SQUARE_SIZE = 80
    BOARD_SIZE = SQUARE_SIZE * 8
    INFO_PANEL_WIDTH = 300
    WINDOW_WIDTH = BOARD_SIZE + INFO_PANEL_WIDTH
    WINDOW_HEIGHT = BOARD_SIZE + 100  # Extra space for controls

    # Colors
    COLOR_LIGHT_SQUARE = (240, 217, 181)
    COLOR_DARK_SQUARE = (181, 136, 99)
    COLOR_HIGHLIGHT = (186, 202, 68, 128)
    COLOR_SELECTED = (246, 246, 105, 180)
    COLOR_BACKGROUND = (50, 50, 50)
    COLOR_TEXT = (255, 255, 255)
    COLOR_BUTTON = (70, 70, 70)
    COLOR_BUTTON_HOVER = (90, 90, 90)

    # Piece images configuration (placeholder for now)
    PIECE_IMAGES = {}

    def __init__(self, settings: dict = None):
        pygame.init()
        self.screen = pygame.display.set_mode((self.WINDOW_WIDTH, self.WINDOW_HEIGHT))
        pygame.display.set_caption("MyBot Chess GUI")

        self.board = chess.Board()
        self.selected_square = None
        self.legal_moves = []
        self.move_history = []

        # Game settings from setup screen
        if settings:
            self.white_player = settings.get('white_player', PlayerType.HUMAN)
            self.black_player = settings.get('black_player', PlayerType.COMPUTER)
            time_minutes = settings.get('time_minutes', 0)
            increment_seconds = settings.get('increment_seconds', 0)
        else:
            self.white_player = PlayerType.HUMAN
            self.black_player = PlayerType.COMPUTER
            time_minutes = 0
            increment_seconds = 0

        # Determine game mode
        if self.white_player == PlayerType.HUMAN and self.black_player == PlayerType.HUMAN:
            self.current_mode = GameMode.HUMAN_VS_HUMAN
        elif self.white_player == PlayerType.HUMAN and self.black_player == PlayerType.COMPUTER:
            self.current_mode = GameMode.HUMAN_VS_COMPUTER
        elif self.white_player == PlayerType.COMPUTER and self.black_player == PlayerType.HUMAN:
            self.current_mode = GameMode.COMPUTER_VS_HUMAN
        else:
            self.current_mode = GameMode.COMPUTER_VS_COMPUTER

        # Time control - store original settings for reset
        self.initial_time_minutes = time_minutes
        self.initial_increment_seconds = increment_seconds
        self.time_control = TimeControl(time_minutes, increment_seconds)

        # Engine
        jar_path = Path(__file__).parent / "build/libs/my_bot.jar"
        self.engine = EngineInterface(str(jar_path))
        self.engine_thinking = False

        # UI state
        self.flipped = False
        self.show_legal_moves = True
        self.font = pygame.font.SysFont('Arial', 16)
        self.font_large = pygame.font.SysFont('Arial', 20, bold=True)

        # Debug info
        self.debug_text = []

        # Game over flag
        self.game_over_reason = None

        # Load piece images (simple colored circles for now, easy to replace)
        self.load_piece_images()

    def load_piece_images(self):
        """Load piece images from PNG files."""
        pieces_dir = Path(__file__).parent / "pieces"

        # Mapping from chess piece symbols to PNG filenames
        piece_mapping = {
            'P': 'white-pawn.png',
            'N': 'white-knight.png',
            'B': 'white-bishop.png',
            'R': 'white-rook.png',
            'Q': 'white-queen.png',
            'K': 'white-king.png',
            'p': 'black-pawn.png',
            'n': 'black-knight.png',
            'b': 'black-bishop.png',
            'r': 'black-rook.png',
            'q': 'black-queen.png',
            'k': 'black-king.png',
        }

        for piece_symbol, filename in piece_mapping.items():
            image_path = pieces_dir / filename
            if image_path.exists():
                # Load the image and scale it to fit the square size
                image = pygame.image.load(str(image_path))
                image = pygame.transform.smoothscale(image, (self.SQUARE_SIZE, self.SQUARE_SIZE))
                self.PIECE_IMAGES[piece_symbol] = image
            else:
                print(f"Warning: Piece image not found: {image_path}")

    def start_engine(self):
        """Start the chess engine."""
        self.engine.start()

    def stop_engine(self):
        """Stop the chess engine."""
        self.engine.stop()

    def get_square_from_pos(self, pos: Tuple[int, int]) -> Optional[int]:
        """Convert screen coordinates to chess square."""
        x, y = pos
        if x >= self.BOARD_SIZE or y >= self.BOARD_SIZE:
            return None

        file = x // self.SQUARE_SIZE
        rank = 7 - (y // self.SQUARE_SIZE)

        if self.flipped:
            file = 7 - file
            rank = 7 - rank

        return chess.square(file, rank)

    def get_square_rect(self, square: int) -> pygame.Rect:
        """Get the screen rectangle for a chess square."""
        file = chess.square_file(square)
        rank = chess.square_rank(square)

        if self.flipped:
            file = 7 - file
            rank = 7 - rank

        x = file * self.SQUARE_SIZE
        y = (7 - rank) * self.SQUARE_SIZE

        return pygame.Rect(x, y, self.SQUARE_SIZE, self.SQUARE_SIZE)

    def draw_board(self):
        """Draw the chess board."""
        for square in chess.SQUARES:
            rect = self.get_square_rect(square)
            color = self.COLOR_LIGHT_SQUARE if (chess.square_file(square) + chess.square_rank(square)) % 2 == 0 else self.COLOR_DARK_SQUARE
            pygame.draw.rect(self.screen, color, rect)

            # Highlight selected square
            if square == self.selected_square:
                s = pygame.Surface((self.SQUARE_SIZE, self.SQUARE_SIZE), pygame.SRCALPHA)
                s.fill(self.COLOR_SELECTED)
                self.screen.blit(s, rect)

            # Highlight legal move destinations
            if self.show_legal_moves and self.selected_square is not None:
                for move in self.legal_moves:
                    if move.to_square == square:
                        s = pygame.Surface((self.SQUARE_SIZE, self.SQUARE_SIZE), pygame.SRCALPHA)
                        s.fill(self.COLOR_HIGHLIGHT)
                        self.screen.blit(s, rect)

    def draw_pieces(self):
        """Draw the chess pieces."""
        for square in chess.SQUARES:
            piece = self.board.piece_at(square)
            if piece:
                rect = self.get_square_rect(square)
                piece_symbol = piece.symbol()
                if piece_symbol in self.PIECE_IMAGES:
                    self.screen.blit(self.PIECE_IMAGES[piece_symbol], rect)

    def draw_info_panel(self):
        """Draw the information panel on the right side."""
        panel_x = self.BOARD_SIZE
        panel_rect = pygame.Rect(panel_x, 0, self.INFO_PANEL_WIDTH, self.WINDOW_HEIGHT)
        pygame.draw.rect(self.screen, self.COLOR_BACKGROUND, panel_rect)

        y_offset = 20

        # Time controls (if enabled)
        if self.time_control.enabled:
            white_time, black_time = self.time_control.get_times()

            # White's time
            white_time_text = f"White: {self.time_control.format_time(white_time)}"
            white_color = (255, 255, 0) if self.board.turn == chess.WHITE else self.COLOR_TEXT
            text = self.font_large.render(white_time_text, True, white_color)
            self.screen.blit(text, (panel_x + 10, y_offset))
            y_offset += 30

            # Black's time
            black_time_text = f"Black: {self.time_control.format_time(black_time)}"
            black_color = (255, 255, 0) if self.board.turn == chess.BLACK else self.COLOR_TEXT
            text = self.font_large.render(black_time_text, True, black_color)
            self.screen.blit(text, (panel_x + 10, y_offset))
            y_offset += 40

            # Check for time forfeit
            if white_time == 0 and self.game_over_reason is None:
                self.game_over_reason = "White ran out of time! Black wins!"
                self.time_control.stop_clock()
            elif black_time == 0 and self.game_over_reason is None:
                self.game_over_reason = "Black ran out of time! White wins!"
                self.time_control.stop_clock()

        # Game mode
        text = self.font_large.render("Game Mode", True, self.COLOR_TEXT)
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 30

        text = self.font.render(self.current_mode.value, True, self.COLOR_TEXT)
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 40

        # Turn indicator
        turn_text = "White to move" if self.board.turn == chess.WHITE else "Black to move"
        text = self.font.render(turn_text, True, self.COLOR_TEXT)
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 30

        # Game status
        if self.game_over_reason:
            status_text = self.game_over_reason
        elif self.board.is_checkmate():
            winner = "Black" if self.board.turn == chess.WHITE else "White"
            status_text = f"Checkmate! {winner} wins!"
            if self.game_over_reason is None:
                self.game_over_reason = status_text
                self.time_control.stop_clock()
        elif self.board.is_stalemate():
            status_text = "Stalemate!"
            if self.game_over_reason is None:
                self.game_over_reason = status_text
                self.time_control.stop_clock()
        elif self.board.is_check():
            status_text = "Check!"
        else:
            status_text = "In progress"

        text = self.font.render(status_text, True, self.COLOR_TEXT)
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 40

        # Engine thinking indicator
        if self.engine_thinking:
            text = self.font.render("Engine thinking...", True, (255, 255, 0))
            self.screen.blit(text, (panel_x + 10, y_offset))
            y_offset += 30

        # Engine Analysis section
        text = self.font_large.render("Engine Analysis", True, self.COLOR_TEXT)
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 30

        # Display structured debug info
        info = self.engine.debug_info

        # Depth completed
        depth_text = f"Depth: {info['depth']}"
        text = self.font.render(depth_text, True, (200, 255, 200))
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 22

        # Evaluation (convert to pawns for display)
        eval_pawns = info['score_cp'] / 100.0
        eval_color = (255, 200, 200) if eval_pawns < 0 else (200, 255, 200)
        if abs(eval_pawns) > 50:  # Mate score
            eval_text = "Eval: Mate" if eval_pawns > 0 else "Eval: -Mate"
        else:
            eval_text = f"Eval: {eval_pawns:+.2f}"
        text = self.font.render(eval_text, True, eval_color)
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 22

        # Time taken
        time_sec = info['time_ms'] / 1000.0
        time_text = f"Time: {time_sec:.2f}s"
        text = self.font.render(time_text, True, (200, 200, 255))
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 22

        # Nodes searched
        nodes = info['nodes']
        if nodes >= 1_000_000:
            nodes_text = f"Nodes: {nodes/1_000_000:.1f}M"
        elif nodes >= 1_000:
            nodes_text = f"Nodes: {nodes/1_000:.1f}K"
        else:
            nodes_text = f"Nodes: {nodes}"
        text = self.font.render(nodes_text, True, (200, 200, 200))
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 22

        # Nodes per second
        nps = info['nps']
        if nps >= 1_000_000:
            nps_text = f"Speed: {nps/1_000_000:.1f}M nps"
        elif nps >= 1_000:
            nps_text = f"Speed: {nps/1_000:.0f}K nps"
        else:
            nps_text = f"Speed: {nps} nps"
        text = self.font.render(nps_text, True, (200, 200, 200))
        self.screen.blit(text, (panel_x + 10, y_offset))
        y_offset += 22

        # TT hit rate
        if info['tt_hits'] > 0 or info['tt_misses'] > 0:
            tt_text = f"TT Hit: {info['tt_hit_rate']:.1f}%"
            text = self.font.render(tt_text, True, (255, 255, 150))
            self.screen.blit(text, (panel_x + 10, y_offset))
            y_offset += 22

        # Principal Variation
        if info['pv']:
            y_offset += 5
            text = self.font.render("PV:", True, self.COLOR_TEXT)
            self.screen.blit(text, (panel_x + 10, y_offset))
            y_offset += 20

            # Display PV moves (may wrap to multiple lines)
            pv_moves = info['pv'].split()
            for i in range(0, len(pv_moves), 3):  # 3 moves per line
                pv_line = " ".join(pv_moves[i:i+3])
                text = self.font.render(pv_line, True, (180, 180, 180))
                self.screen.blit(text, (panel_x + 15, y_offset))
                y_offset += 18

    def draw_controls(self):
        """Draw control buttons at the bottom."""
        y = self.BOARD_SIZE + 10
        button_height = 40
        button_width = 120
        spacing = 10

        buttons = [
            ("New Game", self.new_game),
            ("Flip Board", self.flip_board),
            ("Undo Move", self.undo_move),
        ]

        for i, (label, callback) in enumerate(buttons):
            x = 10 + i * (button_width + spacing)
            rect = pygame.Rect(x, y, button_width, button_height)

            # Check if mouse is hovering
            mouse_pos = pygame.mouse.get_pos()
            color = self.COLOR_BUTTON_HOVER if rect.collidepoint(mouse_pos) else self.COLOR_BUTTON

            pygame.draw.rect(self.screen, color, rect)
            pygame.draw.rect(self.screen, self.COLOR_TEXT, rect, 2)

            text = self.font.render(label, True, self.COLOR_TEXT)
            text_rect = text.get_rect(center=rect.center)
            self.screen.blit(text, text_rect)

            # Store rect for click detection
            if not hasattr(self, 'buttons'):
                self.buttons = {}
            self.buttons[label] = (rect, callback)

    def handle_click(self, pos: Tuple[int, int]):
        """Handle mouse click events."""
        # Check if clicking on control buttons
        for label, (rect, callback) in getattr(self, 'buttons', {}).items():
            if rect.collidepoint(pos):
                callback()
                return

        # Check if clicking on board
        square = self.get_square_from_pos(pos)
        if square is None:
            return

        # Determine if current player is human
        is_human_turn = (
            (self.board.turn == chess.WHITE and self.white_player == PlayerType.HUMAN) or
            (self.board.turn == chess.BLACK and self.black_player == PlayerType.HUMAN)
        )

        if not is_human_turn:
            return

        # Handle piece selection and movement
        if self.selected_square is None:
            # Select a piece
            piece = self.board.piece_at(square)
            if piece and piece.color == self.board.turn:
                self.selected_square = square
                self.legal_moves = [m for m in self.board.legal_moves if m.from_square == square]
        else:
            # Try to make a move
            move = None
            for m in self.legal_moves:
                if m.to_square == square:
                    move = m
                    break

            if move:
                self.make_move(move)
                self.selected_square = None
                self.legal_moves = []
            else:
                # Deselect or select a different piece
                piece = self.board.piece_at(square)
                if piece and piece.color == self.board.turn:
                    self.selected_square = square
                    self.legal_moves = [m for m in self.board.legal_moves if m.from_square == square]
                else:
                    self.selected_square = None
                    self.legal_moves = []

    def make_move(self, move: chess.Move):
        """Make a move on the board."""
        self.move_history.append(self.board.fen())

        # Get the color that's moving (before the move is made)
        moving_color = self.board.turn

        self.board.push(move)

        # Hand the clock to the next player and add increment for the one who just moved
        if self.time_control.enabled:
            self.time_control.switch_side(self.board.turn, moving_color)

        # Update engine position
        self.engine.set_position(self.board.fen())

        # Add to debug
        self.debug_text.append(f"Move: {move.uci()}")

    def computer_move(self):
        """Request and make a computer move."""
        if self.engine_thinking:
            return

        self.engine_thinking = True

        def get_move_async():
            # Get all engine output first (clear any debug messages)
            output = self.engine.get_all_output()
            for line in output:
                if not line.startswith("bestmove"):
                    self.debug_text.append(line[:50])

            # Get best move (10s cap for untimed games, 2s for timed)
            think_ms = 2000 if self.time_control.enabled else 10000
            best_move_str = self.engine.get_best_move(timeout_ms=think_ms)

            if best_move_str and best_move_str != "(none)":
                try:
                    move = chess.Move.from_uci(best_move_str)
                    if move in self.board.legal_moves:
                        self.make_move(move)
                except Exception as e:
                    self.debug_text.append(f"Error: {e}")

            self.engine_thinking = False

        # Run in thread to not block UI
        thread = threading.Thread(target=get_move_async, daemon=True)
        thread.start()

    def new_game(self):
        """Start a new game."""
        # Reset board state
        self.board = chess.Board()
        self.selected_square = None
        self.legal_moves = []
        self.move_history = []
        self.game_over_reason = None
        self.debug_text = ["New game started"]

        # Reset engine - single command to initialize everything
        self.engine.reset_game()

        # Reset time control — stop the old timer thread before replacing
        self.time_control.stop_clock()
        self.time_control = TimeControl(self.initial_time_minutes, self.initial_increment_seconds)
        if self.time_control.enabled:
            self.time_control.start_clock(side=chess.WHITE)

    def flip_board(self):
        """Flip the board view."""
        self.flipped = not self.flipped

    def undo_move(self):
        """Undo the last move."""
        if self.move_history:
            last_fen = self.move_history.pop()
            self.board.set_fen(last_fen)
            self.engine.set_position(self.board.fen())
            self.selected_square = None
            self.legal_moves = []

    def run(self):
        """Main game loop."""
        self.start_engine()

        # Initialize the engine for the first game
        self.engine.reset_game()

        # Start the clock if time control is enabled
        if self.time_control.enabled:
            self.time_control.start_clock(side=chess.WHITE)

        clock = pygame.time.Clock()
        running = True

        while running:
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False
                elif event.type == pygame.MOUSEBUTTONDOWN:
                    self.handle_click(event.pos)

            # Check if it's computer's turn
            is_computer_turn = (
                (self.board.turn == chess.WHITE and self.white_player == PlayerType.COMPUTER) or
                (self.board.turn == chess.BLACK and self.black_player == PlayerType.COMPUTER)
            )

            # Only make computer moves if game is not over
            game_over = self.board.is_game_over() or self.game_over_reason is not None
            if is_computer_turn and not self.engine_thinking and not game_over:
                self.computer_move()

            # Draw everything
            self.screen.fill(self.COLOR_BACKGROUND)
            self.draw_board()
            self.draw_pieces()
            self.draw_info_panel()
            self.draw_controls()

            pygame.display.flip()
            clock.tick(60)

        self.stop_engine()
        pygame.quit()


if __name__ == "__main__":
    jar = Path(__file__).parent / "build/libs/my_bot.jar"
    if not jar.exists():
        print(f"Engine JAR not found: {jar}")
        print("Run './build.sh' first to compile the engine.")
        raise SystemExit(1)

    # Show setup screen first
    setup = GameSetupScreen()
    settings = setup.run()

    # If user didn't cancel, start the game
    if settings:
        gui = ChessGUI(settings)
        gui.run()
    else:
        print("Game cancelled")
        pygame.quit()
