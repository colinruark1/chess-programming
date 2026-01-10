#!/bin/bash
cd "$(dirname "$0")"

# Create build directories
mkdir -p build/classes build/libs

# Compile all Java files in the package directory
javac -cp libs/chesslib-1.2.0.jar -d build/classes src/main/java/mybot/*.java

# Create a manifest that sets the main class to mybot.MyBot
echo "Main-Class: mybot.MyBot" > manifest.txt
echo "Class-Path: chesslib-1.2.0.jar" >> manifest.txt
echo "" >> manifest.txt

# Create the JAR with mybot package
jar cfm build/libs/my_bot.jar manifest.txt -C build/classes mybot

# Copy chesslib dependency to build/libs
cp libs/chesslib-1.2.0.jar build/libs/

# Run the bot in UCI mode
java -jar build/libs/my_bot.jar --uci
