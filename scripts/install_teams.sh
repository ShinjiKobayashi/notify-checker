#!/bin/bash
set -e

TEAMS_APK="/Teams.apk"
TEAMS_URL="https://www.apkmirror.com/wp-content/themes/APKMirror/download.php?id=10912173&key=c183463db6cef21e09b99e7cd458099c32c0f68b"

echo "Waiting for Redroid to be ready..."
# Loop until we can connect
until adb connect redroid:5555; do
    echo "Redroid not ready, retrying in 5 seconds..."
    sleep 5
done

echo "Connected to Redroid."

# Wait for boot completion
echo "Waiting for boot completion..."
until [ "$(adb -s redroid:5555 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do
    echo "Waiting for sys.boot_completed..."
    sleep 3
done

# Check if Teams is already installed
if adb -s redroid:5555 shell pm list packages | grep -q "com.microsoft.teams"; then
    echo "Microsoft Teams is already installed."
else
    echo "Microsoft Teams not found."

    # Download if not present locally
    if [ ! -f "$TEAMS_APK" ]; then
        echo "Downloading Teams APK..."
        curl -L --user-agent "Mozilla/5.0" \
             --header "Referer: https://www.apkmirror.com/" \
             -o "$TEAMS_APK" "$TEAMS_URL"
    fi

    echo "Installing Microsoft Teams..."
    adb -s redroid:5555 install "$TEAMS_APK"
    echo "Teams installed successfully."
fi

# We can disconnect now
adb disconnect redroid:5555
