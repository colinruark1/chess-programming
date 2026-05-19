#!/bin/bash
cd "$(dirname "$0")"

JAR="build/libs/my_bot.jar"

if [ ! -f "$JAR" ] || find src/main/java/mybot -name "*.java" -newer "$JAR" | grep -q .; then
    echo "Building engine..."
    mkdir -p build/classes build/libs
    javac -d build/classes src/main/java/mybot/*.java || exit 1
    jar cfe "$JAR" mybot.MyBot -C build/classes mybot
    echo "Build complete: $JAR"
else
    echo "Already up to date."
fi
