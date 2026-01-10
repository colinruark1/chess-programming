#!/bin/bash
# Quick launcher for debug tests

cd "$(dirname "$0")"

echo "╔════════════════════════════════════════╗"
echo "║   Chess Engine Debug Test Launcher    ║"
echo "╚════════════════════════════════════════╝"
echo ""
echo "Available test scripts:"
echo ""
echo "  1) debug_test.sh      - Quick automated test (fast)"
echo "  2) debug_deep.sh      - Interactive deep analysis (detailed)"
echo "  3) debug_focused.sh   - Targeted issue hunter (saves logs)"
echo ""
echo "  4) View DEBUG_GUIDE.md - Read full debugging guide"
echo "  5) Exit"
echo ""
read -p "Select option (1-5): " choice

case $choice in
    1)
        echo ""
        echo "Running quick test..."
        ./debug_test.sh
        ;;
    2)
        echo ""
        echo "Starting deep analysis (press Enter between tests)..."
        ./debug_deep.sh
        ;;
    3)
        echo ""
        echo "Running focused debug (output saved to /tmp/test*.log)..."
        ./debug_focused.sh
        ;;
    4)
        echo ""
        if command -v less &> /dev/null; then
            less DEBUG_GUIDE.md
        elif command -v more &> /dev/null; then
            more DEBUG_GUIDE.md
        else
            cat DEBUG_GUIDE.md
        fi
        ;;
    5)
        echo "Exiting..."
        exit 0
        ;;
    *)
        echo "Invalid option. Exiting."
        exit 1
        ;;
esac

echo ""
echo "Test complete!"
echo ""
echo "For more information, run: cat DEBUG_GUIDE.md"
