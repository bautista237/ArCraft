
#!/bin/bash

SRV="/home/overseer/arcraft-test-server"
LOG="/tmp/arcraft-realserver.log"

echo "Starting Minecraft server..."

# Navigate to the server directory
cd "$SRV" || { echo "Directory not found!"; exit 1; }

# Clear the old log file
rm -f "$LOG"

# Run the Forge server script in the background
nohup ./run.sh nogui > "$LOG" 2>&1 &

echo "Server started in the background (PID $!)."
echo "To watch the live console, run: tail -f $LOG"