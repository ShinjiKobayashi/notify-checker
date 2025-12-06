# Use an image with Gradle and JDK 17 pre-installed
FROM gradle:8.5-jdk17 AS build

# Switch to root to install dependencies
USER root

# Set environment variables for Android SDK
ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH ${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Download and install Android SDK Command Line Tools
WORKDIR /opt/android-sdk
RUN apt-get update && apt-get install -y unzip && \
    curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip cmdline-tools.zip && \
    rm cmdline-tools.zip && \
    mkdir -p cmdline-tools/latest && \
    mv cmdline-tools/bin cmdline-tools/latest/ && \
    mv cmdline-tools/lib cmdline-tools/latest/ && \
    mv cmdline-tools/source.properties cmdline-tools/latest/

# Accept licenses and install necessary SDK components
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Copy project files
WORKDIR /app
COPY . .

# Build the APK
RUN gradle assembleDebug --no-daemon

# Output stage (just to allow copying the APK out easily if needed, though volume mount is better)
CMD ["echo", "Build complete. APK is in /app/app/build/outputs/apk/debug/"]
