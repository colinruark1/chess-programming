#!/bin/bash
cd "$(dirname "$0")"

JAR="build/libs/my_bot.jar"

if [ ! -f "$JAR" ] || find src/main/java/mybot -name "*.java" -newer "$JAR" | grep -q .; then
    echo "Building engine..." >&2
    mkdir -p build/classes build/libs
    javac -d build/classes src/main/java/mybot/*.java || exit 1
    jar cfe "$JAR" mybot.MyBot -C build/classes mybot
fi

java -jar "$JAR" --uci
