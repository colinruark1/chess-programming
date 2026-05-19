# GitHub Setup

## Initial Setup

### 1. Create a Repository on GitHub

1. Go to [github.com/new](https://github.com/new)
2. Name it `my-chess-bot` (or your choice)
3. Choose Public or Private
4. Do **not** initialize with any files
5. Click **Create repository**

### 2. Push Your Code

```bash
# One-time git configuration
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

git add .
git commit -m "Initial commit: chess engine with iterative deepening and PST interpolation"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git branch -M main
git push -u origin main
```

## Daily Workflow

```bash
git status                                      # see what changed
git add src/main/java/mybot/ChessEngine.java    # stage specific files
git add .                                       # or stage everything
git commit -m "Fix time management in sudden death games"
git push
```

### Good Commit Messages

```
✅ Fix time management in sudden death games
✅ Add continuous phase interpolation to PSTs
✅ Improve move ordering with killer move heuristic
❌ Update
❌ Fix bug
```

## Useful Git Commands

```bash
# History
git log --oneline
git show              # last commit diff
git diff              # uncommitted changes

# Undo
git checkout -- filename.java   # discard file changes (before add)
git reset HEAD filename.java    # unstage (after add, before commit)
git reset --soft HEAD~1         # undo last commit, keep changes
git reset --hard HEAD~1         # undo last commit, discard changes (CAREFUL)

# Branches
git checkout -b experiment-new-eval
git checkout main
git merge experiment-new-eval
git branch -d experiment-new-eval
```

## Authentication

### HTTPS with Personal Access Token (Recommended)

1. GitHub → Settings → Developer Settings → Personal Access Tokens → Tokens (classic)
2. Generate new token, check **repo** scope, copy immediately
3. Use your GitHub username + the token as password when pushing

### SSH Keys

```bash
ssh-keygen -t ed25519 -C "your.email@example.com"
cat ~/.ssh/id_ed25519.pub   # copy this to GitHub → Settings → SSH keys
git remote set-url origin git@github.com:YOUR_USERNAME/YOUR_REPO.git
```

## Cloning to Another Machine

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO
./run_bot.sh
```

## What Is (and Isn't) Tracked

**Tracked:**
- `src/main/java/mybot/*.java` — all Java source
- `libs/chesslib-1.2.0.jar` — chess library
- `chess_gui.py` — Python GUI
- `pieces/` — piece images
- `run_bot.sh`, `run_gui.sh`, `RUN_TESTS.sh` — scripts
- `build.gradle`, `requirements.txt` — build/dependencies
- `docs/` — documentation

**Ignored (via `.gitignore`):**
- `build/` — compiled output
- `*.class` — bytecode
- Python virtual environments
- IDE configuration files
- Test log files (`/tmp/test*.log`)

## Troubleshooting

| Problem | Solution |
|---------|---------|
| Authentication failed | Use a Personal Access Token, not your GitHub password |
| Repository not found | Check URL with `git remote -v` |
| Changes not on GitHub | Did you commit? Did you push? |
| Merge conflicts | `git pull` first, resolve conflicts, then commit and push |

## Tips

- Commit often — small, focused commits beat large ones
- Always `git pull` before starting new work
- Use branches for experiments; keep `main` stable
- Never commit secrets (API keys, tokens)
