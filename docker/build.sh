#!/usr/bin/env bash
# Builds the app in the Docker image, from Windows (Git Bash), macOS or Linux:
#   docker/build.sh            the native libraries, then the debug APK
#   docker/build.sh release    ... the release APK (signed when ANDROID_KEYSTORE* are set)
#   docker/build.sh native     the native libraries only (native/out/jniLibs, also used by
#                              Android Studio builds on Windows)
# VIT_CORE_DIR=<the desktop app's repository> also builds the shared core (vi-translator-ffi) and
# copies its Kotlin bindings; without it the core's library already in native/out is kept.
# The Gradle and Cargo caches stay in Docker volumes between runs.
set -euo pipefail
cd "$(dirname "$0")/.."
IMAGE=vi-translator-android-build
docker build -t "$IMAGE" docker
WHAT="${1:-debug}"
case "$WHAT" in
  native) CMD="native/build.sh" ;;
  debug) CMD="native/build.sh && ./gradlew --no-daemon assembleGithubDebug -PrequireNative" ;;
  release) CMD="native/build.sh && ./gradlew --no-daemon assembleGithubRelease bundlePlayRelease -PrequireNative" ;;
  *) echo "usage: $0 [debug|release|native]" >&2; exit 2 ;;
esac
ENVS=()
for v in ANDROID_KEYSTORE ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  [ -n "${!v:-}" ] && ENVS+=(-e "$v")
done
MOUNTS=(-v "$PWD:/src" -v vit-android-gradle:/root/.gradle -v vit-android-cargo:/opt/cargo/registry)
if [ -n "${VIT_CORE_DIR:-}" ]; then
  MOUNTS+=(-v "$(cd "$VIT_CORE_DIR" && pwd):/core")
  ENVS+=(-e VIT_CORE_DIR=/core)
fi
# (MSYS_NO_PATHCONV: Git Bash would rewrite /src into a Windows path)
MSYS_NO_PATHCONV=1 docker run --rm "${ENVS[@]}" "${MOUNTS[@]}" "$IMAGE" bash -c "$CMD"
