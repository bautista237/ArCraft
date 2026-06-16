#!/bin/bash

echo "Stopping Minecraft server and backend..."

# 1. Find the PID listening on port 25565 and kill it
PID=$(ss -ltnp 2>/dev/null | grep 25565 | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2)

if [ -n "$PID" ]; then
    kill -9 "$PID"
    echo "Killed process on port 25565 (PID: $PID)"
else
    echo "No process found on port 25565."
fi

# 2. Kill any lingering Forge server or backend instances by name
pkill -9 -f 'forgeserver|arcraft-backend.jar' 2>/dev/null
echo "Server and backend stopped."
