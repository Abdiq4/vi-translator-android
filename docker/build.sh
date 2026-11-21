#!/usr/bin/env bash
# Builds the app in the Docker image, from Windows (Git Bash), macOS or Linux:
#   docker/build.sh            the native libraries, then the debug APK
#   docker/build.sh release    ... the release APK (signed when ANDROID_KEYSTORE* are set)
#   docker/build.sh native     the native libraries only (native/out/jniLibs, also used by
#                              Android Studio builds on Windows)
# The Gradle cache stays in a Docker volume between runs.
set -euo pipefail
cd "$(dirname "$0")/.."
IMAGE=vi-translator-android-build
docker build -t "$IMAGE" docker
WHAT="${1:-debug}"
case "$WHAT" in
  native) CMD="native/build.sh" ;;
  debug) CMD="native/build.sh && ./gradlew --no-daemon assembleDebug -PrequireNative" ;;
  release) CMD="native/build.sh && ./gradlew --no-daemon assembleRelease -PrequireNative" ;;
  *) echo "usage: $0 [debug|release|native]" >&2; exit 2 ;;
esac
ENVS=()
for v in ANDROID_KEYSTORE ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD VIT_CORE_DIR; do
  [ -n "${!v:-}" ] && ENVS+=(-e "$v")
done
# (MSYS_NO_PATHCONV: Git Bash would rewrite /src into a Windows path)
MSYS_NO_PATHCONV=1 docker run --rm "${ENVS[@]}" -v "$PWD:/src" -v vit-android-gradle:/root/.gradle "$IMAGE" bash -c "$CMD"
