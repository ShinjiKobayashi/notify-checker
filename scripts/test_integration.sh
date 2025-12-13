#!/bin/bash
set -e

echo "Starting integration test..."

# Ensure we can talk to the device
adb wait-for-device

# Forward port 8080 from host to device
echo "Forwarding port 8080..."
adb forward tcp:8080 tcp:8080

# Basic health check: Is the port open?
# We use a dummy ID that definitely won't exist.
# Expected: HTTP 404 "Notification not found" or HTTP 200 "Reply sent" (if we happened to guess an ID, unlikely)
# If the server is NOT running, curl will fail with connection refused.

PAYLOAD='{"id": "test_id_123", "replyText": "Integration Test Reply"}'

echo "Sending POST request to http://localhost:8080/reply..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/reply \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

echo "Response Code: $HTTP_CODE"

if [ "$HTTP_CODE" -eq 404 ]; then
  echo "SUCCESS: Server received request and correctly identified 'Notification not found'."
elif [ "$HTTP_CODE" -eq 200 ]; then
  echo "SUCCESS: Server received request and processed it."
else
  echo "FAILURE: Unexpected response code $HTTP_CODE"
  exit 1
fi

echo "Integration test completed successfully."
