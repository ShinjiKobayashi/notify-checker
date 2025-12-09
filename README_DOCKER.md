# Docker Environment for Cona

This repository includes a Docker configuration to build and run the Android application.

## Prerequisites

1.  **Linux Host**: Required for `redroid` (Remote Android) support.
2.  **KVM Support**: Your CPU must support virtualization and KVM must be installed/enabled.
    *   Check with `kvm-ok` or `ls /dev/kvm`.
3.  **Docker & Docker Compose**: Installed and running.
4.  **ADB** (Optional): Useful for debugging from the host, though the scripts handle installation.

## Quick Start

1.  **Build and Install**
    Run the helper script to build the APK and install it into the running Redroid container:
    ```bash
    ./scripts/install_and_run.sh
    ```

2.  **Manual Steps**

    *   **Start Android Container:**
        ```bash
        docker-compose up -d redroid
        ```

    *   **Build APK:**
        ```bash
        docker-compose run builder
        ```

    *   **Connect via ADB:**
        The Redroid container exposes ADB on port `5555`.
        ```bash
        adb connect localhost:5555
        ```

## Troubleshooting

*   **Missing /dev/kvm**: If you see errors related to KVM, ensure virtualization is enabled in your BIOS and KVM modules are loaded on your Linux host.
*   **Binder IPC**: Redroid relies on binder modules (`binder_linux`, `ashmem_linux`). If they are not present, you may need to install them or use a kernel that supports them.

## Notes

*   The Android image used is `redroid/redroid:android14.0.0-latest`.
*   The build environment uses `gradle:8.5-jdk17` with Android SDK 34 command line tools.
