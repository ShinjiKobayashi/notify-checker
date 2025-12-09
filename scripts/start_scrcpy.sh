#!/bin/bash
set -e

# Default to redroid:5555 if not set
ADB_REMOTE=${WS_SCRCPY_ADB_REMOTE:-redroid:5555}

echo "Starting ws-scrcpy..."
echo "Attempting to connect to ADB remote: $ADB_REMOTE"

# Try to connect
adb connect $ADB_REMOTE

# We don't wait indefinitely, as ADB might take a moment.
# ws-scrcpy might also have auto-discovery if adb is running,
# but an explicit connect is safer.
# If connection fails, we just proceed; user can refresh or we can loop.
# But let's loop a few times to be helpful.
for i in {1..5}; do
    if adb devices | grep -q "$ADB_REMOTE"; then
        echo "Connected to $ADB_REMOTE"
        break
    else
        echo "Waiting for connection..."
        sleep 2
        adb connect $ADB_REMOTE
    fi
done

echo "Starting npm..."
exec npm start
