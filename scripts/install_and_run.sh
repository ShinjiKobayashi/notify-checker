#!/bin/bash
set -e

echo "Starting build..."
docker-compose up builder

echo "Waiting for Redroid to be ready..."
# Ensure Redroid is running
docker-compose up -d redroid

# Wait loop for ADB connection
echo "Connecting to Redroid..."
MAX_RETRIES=30
for i in $(seq 1 $MAX_RETRIES); do
    if docker exec cona-redroid adb shell getprop sys.boot_completed | grep -q "1"; then
        echo "Redroid is ready."
        break
    fi
    echo "Waiting for boot... ($i/$MAX_RETRIES)"
    sleep 2
done

echo "Installing APK..."
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_PATH" ]; then
    # Install using adb inside the container or via host adb if available.
    # We'll use 'adb install' from the host assuming the user has adb,
    # OR we can exec into the redroid container to install it if the file is mounted.
    # Since we didn't mount the build output to redroid in compose, we'll stream it.

    # Stream install via docker exec
    cat "$APK_PATH" | docker exec -i cona-redroid pm install -S $(stat -c%s "$APK_PATH") /dev/stdin

    echo "App installed successfully."
    echo "Launch package: com.nextlab.cona"
else
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi
