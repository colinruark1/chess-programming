#!/bin/bash
# Launch the Chess Game

cd "$(dirname "$0")"

# Always use explicit flag to avoid AWT loading issues
# Check if DISPLAY is set (GUI available)
if [ -z "$DISPLAY" ]; then
    # No display - use console mode
    java -cp "my_bot_gui.jar:libs/chesslib-1.2.0.jar" mybot.MyBot --console
else
    # Display available - try GUI mode
    java -cp "my_bot_gui.jar:libs/chesslib-1.2.0.jar" mybot.MyBot --gui
fi
