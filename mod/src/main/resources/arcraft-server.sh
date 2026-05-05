#!/bin/bash
# ArCraft Server Startup Script
# Starts the web dashboard alongside the Minecraft server
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "[ArCraft] Starting web dashboard on port 8080..."
java -jar "$SCRIPT_DIR/arcraft-web/arcraft-backend.jar" &
SPRING_PID=$!
echo "[ArCraft] Web dashboard started (PID $SPRING_PID)"
echo "[ArCraft] Starting Minecraft server..."
bash "$SCRIPT_DIR/run.sh"
echo "[ArCraft] Minecraft server stopped. Stopping web dashboard..."
kill $SPRING_PID
