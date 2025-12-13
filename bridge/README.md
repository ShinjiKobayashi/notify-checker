# Matrix Teams Bridge (Redroid Sidecar)

This service bridges Microsoft Teams notifications from an Android instance (Redroid) to a Matrix network. It allows you to receive Teams messages in Matrix and reply to them directly.

## Prerequisites

*   A Matrix Homeserver (e.g., Synapse, Dendrite) that you control.
*   The Redroid Docker environment set up (as per the root `README_DOCKER.md`).

## Setup & Connection

### 1. Registration

Before the bridge can connect to your Matrix Homeserver, it must be registered as an Application Service.

1.  **Generate a Registration File:**
    The `registration.yaml` file defines how the Homeserver communicates with the bridge. You can start with the provided example:

    ```bash
    cp registration.yaml.example registration.yaml
    ```

2.  **Edit `registration.yaml`:**
    *   **Tokens:** Replace `as_token` and `hs_token` with secure random strings.
    *   **URL:** Ensure the `url` matches the Docker service name and port if your Homeserver is in the same Docker network (e.g., `http://bridge:8090`). If your Homeserver is external, you may need to expose the bridge port in `docker-compose.yml` and use your public IP/domain (e.g., `http://your-server-ip:8090`).

    *Example:*
    ```yaml
    id: teams-bridge
    url: http://bridge:8090  # Internal Docker URL
    as_token: "secure_random_as_token_123"
    hs_token: "secure_random_hs_token_456"
    sender_localpart: teams_bot
    namespaces:
      users:
        - exclusive: true
          regex: "@teams_.*"
      aliases:
        - exclusive: true
          regex: "#teams_.*"
      rooms: []
    ```

3.  **Register with Homeserver:**
    *   Copy this `registration.yaml` to your Matrix Homeserver's configuration directory.
    *   Add the path to this file in your Homeserver's config (e.g., `homeserver.yaml` for Synapse):

        ```yaml
        app_service_config_files:
          - /path/to/registration.yaml
        ```
    *   **Restart your Matrix Homeserver.**

### 2. Configuration

The bridge needs to know how to reach your Homeserver. You can configure this via environment variables in `docker-compose.yml` or by editing `config.yaml`.

**Environment Variables (Recommended via Docker):**
In the root `docker-compose.yml`, update the `bridge` service:

```yaml
  bridge:
    # ...
    environment:
      - MATRIX_USER_ID=@your_username:example.com  # The user who will receive Teams messages
      - MATRIX_HOMESERVER_URL=http://synapse:8008  # URL reachable from the bridge container
      - MATRIX_DOMAIN=example.com                  # Your Matrix domain
```

**Note:** The `registration.yaml` must be mounted into the container at `/app/registration.yaml`. The provided `docker-compose.yml` handles this mount.

### 3. Running

Start the Docker environment:

```bash
docker-compose up -d
```

### 4. Verification

1.  **Check Logs:**
    ```bash
    docker-compose logs -f bridge
    ```
    You should see "AppService initialized" and successful connection messages.

2.  **Receive a Message:**
    *   Login to Teams in the Redroid container (via VNC/Scrcpy).
    *   Wait for a notification.
    *   The bridge should create a new room in Matrix (e.g., `Teams - Sender Name`) and invite your `MATRIX_USER_ID`.

3.  **Reply:**
    *   Join the room.
    *   Send a message.
    *   The message should appear in the Teams chat on the Android device.

## Troubleshooting

*   **"M_UNKNOWN_TOKEN"**: Check that the `as_token` in `registration.yaml` matches what the Homeserver loaded.
*   **Bridge crashes on start**: Ensure `registration.yaml` exists and is valid YAML.
*   **No messages in Matrix**:
    *   Ensure the Android app has notification permissions.
    *   Check `bridge` logs for "Received notification" events.
    *   Check if the NotificationService in Android is running (Logcat: `adb logcat -s NotificationService`).
