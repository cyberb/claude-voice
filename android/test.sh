#!/bin/sh
# Run the Android JVM unit tests. Needs the Android SDK platform to compile
# against, but no signing keystore (unsigned, no device).
set -eu
cd "$(dirname "$0")"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
./gradlew test
