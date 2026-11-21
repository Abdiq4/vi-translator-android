#!/usr/bin/env bash
# Builds the app's native libraries into native/out/jniLibs/<abi>/, where Gradle picks them up:
#   libchiaki-jni.so       PS Remote Play (chiaki-ng, from third-party/chiaki-ng)
#   libvi_translator_core.so  the shared Rust core, when VIT_CORE_DIR points at its crate (cargo-ndk)
# Linux only (chiaki-ng needs protoc and builds OpenSSL from source): run it in the Docker image
# (docker/build.sh) or on an Ubuntu CI runner. Needs ANDROID_NDK_HOME (or ANDROID_HOME with the NDK).
set -euo pipefail
cd "$(dirname "$0")"
NATIVE="$PWD"
ABIS="${ABIS:-arm64-v8a x86_64}"
API=26
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK" ] && [ -n "${ANDROID_HOME:-}" ]; then
  NDK="$ANDROID_HOME/ndk/$(ls "$ANDROID_HOME/ndk" | sort -V | tail -n1)"
fi
[ -d "$NDK" ] || { echo "no NDK: set ANDROID_NDK_HOME" >&2; exit 1; }
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"

# chiaki-ng's own submodules (only those the library needs)
git -C ../third-party/chiaki-ng submodule update --init --depth 1 \
  third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl

for ABI in $ABIS; do
  echo "== chiaki-ng for $ABI"
  cmake -S "$NATIVE" -B "build/$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_BUILD_TYPE=Release
  cmake --build "build/$ABI" --target chiaki-jni
  mkdir -p "out/jniLibs/$ABI"
  "$STRIP" -o "out/jniLibs/$ABI/libchiaki-jni.so" "build/$ABI/libchiaki-jni.so"
done

if [ -n "${VIT_CORE_DIR:-}" ]; then
  echo "== vi-translator-core from $VIT_CORE_DIR"
  ARGS=()
  for ABI in $ABIS; do ARGS+=(-t "$ABI"); done
  (cd "$VIT_CORE_DIR" && cargo ndk "${ARGS[@]}" -P "$API" -o "$NATIVE/out/jniLibs" build --release)
fi

ls -l out/jniLibs/*
