# GitHub Setup Guide

## Quick Start (5 minutes)

### 1. Create GitHub Repository
1. Go to https://github.com/new
2. Repository name: `my-chess-bot` (or your choice)
3. Choose **Public** or **Private**
4. **Don't** check any initialization options
5. Click "Create repository"

### 2. Run These Commands

```bash
cd /home/diamo/lichess-bot/lichess-bot/engines/my_bot

# Configure git (first time only)
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Stage all files
git add .

# Create first commit
git commit -m "Initial commit: Custom chess engine with iterative deepening and PST interpolation"

# Connect to GitHub (replace YOUR_USERNAME and YOUR_REPO)
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git

# Push to GitHub
git branch -M main
git push -u origin main
```

## Daily Workflow

### Making Changes and Pushing

```bash
# 1. Make your code changes in VSCode or your editor

# 2. Check what changed
git status

# 3. Add changes (all files)
git add .

# Or add specific files
git add src/main/java/mybot/ChessEngine.java

# 4. Commit with a descriptive message
git commit -m "Add PST interpolation for smooth endgame transitions"

# 5. Push to GitHub
git push
```

### Example Commit Messages

Good commit messages:
- ✅ "Fix time management in sudden death games"
- ✅ "Add continuous phase interpolation to PSTs"
- ✅ "Improve move ordering with killer move heuristic"
- ✅ "Update README with build instructions"

Poor commit messages:
- ❌ "Update"
- ❌ "Fix bug"
- ❌ "Changes"

## Useful Git Commands

### Viewing History
```bash
# See recent commits
git log --oneline

# See what changed in last commit
git show

# See changes not yet committed
git diff
```

### Undoing Changes

```bash
# Undo changes to a file (before git add)
git checkout -- filename.java

# Unstage a file (after git add, before commit)
git reset HEAD filename.java

# Undo last commit but keep changes
git reset --soft HEAD~1

# Undo last commit and discard changes (CAREFUL!)
git reset --hard HEAD~1
```

### Branching (Advanced)

```bash
# Create a new branch for experiments
git checkout -b experiment-new-eval

# Switch back to main
git checkout main

# Merge branch into main
git merge experiment-new-eval

# Delete branch
git branch -d experiment-new-eval
```

## What's Being Tracked?

Your repository includes:

### Source Code
- `src/main/java/mybot/*.java` - All your Java source files
- 17 Java files including ChessEngine, GamePhase, PieceSquareTables, etc.

### Dependencies
- `libs/chesslib-1.2.0.jar` - Chess library

### Scripts
- `run_bot.sh` - Build and run script
- `run_gui.sh` - GUI launcher
- `RUN_TESTS.sh` - Test runner

### Documentation
- `README.md` - Main documentation
- Multiple guide files (GAMEPHASE_USAGE.md, etc.)

### Configuration
- `build.gradle` - Gradle build configuration
- `requirements.txt` - Python dependencies for GUI

### Assets
- `chess_gui.py` - Python chess GUI
- `pieces/` - Chess piece images

### What's Ignored?

- ❌ Compiled `.class` files
- ❌ `build/` directory
- ❌ Built JAR files (except libs)
- ❌ Test and debug scripts
- ❌ Python virtual environment
- ❌ IDE configuration files

## Authentication Options

### Option 1: HTTPS with Personal Access Token (Recommended)

1. Go to GitHub Settings → Developer Settings → Personal Access Tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Name: "Chess Bot Development"
4. Expiration: 90 days (or your choice)
5. Scopes: Check **repo** (full control of private repositories)
6. Click "Generate token"
7. **Copy the token immediately** (you won't see it again!)
8. When you `git push`, use your GitHub username and the token as password

### Option 2: SSH Keys (More Secure)

```bash
# Generate SSH key (if you don't have one)
ssh-keygen -t ed25519 -C "your.email@example.com"

# Copy public key
cat ~/.ssh/id_ed25519.pub

# Add to GitHub:
# Settings → SSH and GPG keys → New SSH key
# Paste the key and save

# Use SSH URL for remote:
git remote set-url origin git@github.com:YOUR_USERNAME/YOUR_REPO.git
```

## Cloning to Another Machine

Once pushed to GitHub, you can clone your repository anywhere:

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO
./run_bot.sh
```

## Troubleshooting

### "Authentication failed"
- Use a Personal Access Token, not your GitHub password
- Make sure the token has 'repo' scope

### "Repository not found"
- Check the URL: `git remote -v`
- Make sure you have access to the repository

### "Changes not showing on GitHub"
- Did you commit? `git commit -m "message"`
- Did you push? `git push`

### "Merge conflicts"
- If you edit on GitHub and locally, you might get conflicts
- Pull first: `git pull`
- Resolve conflicts, then commit and push

## Tips

1. **Commit often**: Small, focused commits are better than large ones
2. **Pull before you push**: Always `git pull` before starting work
3. **Write good commit messages**: Future you will thank you
4. **Use branches for experiments**: Keep main branch stable
5. **Don't commit sensitive data**: API keys, passwords, etc.

## Next Steps

After pushing to GitHub, you can:
- ✅ Share your repository with others
- ✅ Clone it to other machines
- ✅ Create a GitHub Pages site for documentation
- ✅ Set up GitHub Actions for automated testing
- ✅ Accept contributions from others (if public)

Happy coding! 🚀
