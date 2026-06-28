#!/bin/bash
# Starts BOTH ArCraft processes in the correct order:
#   1) the Spring Boot web backend (opens the shared H2 file as AUTO_SERVER)
#   2) the NeoForge Minecraft server (the mod connects to the same H2 file)
# Both must run from the SAME server directory so they share ./arcraft-data.
#
# Override the server dir with:  SRV=/path/to/server ./start.sh

SRV="${SRV:-/home/overseer/arcraft-test-server}"
BACKEND_JAR="$SRV/arcraft-web/arcraft-backend.jar"
MC_LOG="$SRV/logs/arcraft-mc.log"
WEB_LOG="$SRV/arcraft-web/arcraft-web.log"

cd "$SRV" || { echo "Server directory not found: $SRV"; exit 1; }

echo "[1/2] Starting web backend..."
if [ ! -f "$BACKEND_JAR" ]; then
    echo "  Backend jar missing: $BACKEND_JAR"; exit 1
fi
# Run from $SRV so it loads ./application.properties and shares ./arcraft-data.
nohup java -jar "$BACKEND_JAR" > "$WEB_LOG" 2>&1 &
echo "  Backend PID $! (log: $WEB_LOG)"

# Give the backend a head start so it owns the H2 AUTO_SERVER before the mod connects.
echo "  Waiting for backend to be ready..."
for i in $(seq 1 60); do
    grep -q "Started ArcraftApplication" "$WEB_LOG" 2>/dev/null && { echo "  Backend up."; break; }
    sleep 2
done

echo "[2/2] Starting Minecraft server..."
nohup ./run.sh nogui > "$MC_LOG" 2>&1 &
echo "  Minecraft PID $! (log: $MC_LOG)"

echo
echo "ArCraft is starting."
echo "  Web:        http://localhost:8080   (and http://<your-public-ip>:8080 once port-forwarded)"
echo "  Minecraft:  localhost:25565"
echo "  Watch MC:   tail -f $MC_LOG"
echo "  Watch web:  tail -f $WEB_LOG"
echo "  Stop both:  ./stop.sh"
