# The PS Remote Play library: chiaki-ng's lib (the submodule in third-party/chiaki-ng, pinned to a
# release) and its Android JNI layer (jni/, from chiaki-ng's Android app, with VI Translator's
# package and no audio). Built by build.sh on Linux, once per ABI, with the NDK's toolchain.
cmake_minimum_required(VERSION 3.22)
project(vit-native C CXX)

set(CHIAKI_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_CLI OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_GUI OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_SETSU OFF CACHE STRING "" FORCE)
set(CHIAKI_ENABLE_ANDROID OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_BOREALIS OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_FFMPEG_DECODER OFF CACHE STRING "" FORCE)
set(CHIAKI_ENABLE_PI_DECODER OFF CACHE STRING "" FORCE)
set(CHIAKI_ENABLE_SPEEX OFF CACHE STRING "" FORCE)
set(CHIAKI_LIB_ENABLE_OPUS OFF CACHE BOOL "" FORCE)
set(CHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
set(CHIAKI_LIB_JSONC_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
set(CHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_STEAM_SHORTCUT OFF CACHE BOOL "" FORCE)
set(CHIAKI_ENABLE_STEAMDECK_NATIVE OFF CACHE STRING "" FORCE)

# Not the build machine's pkg-config: its libraries are for Linux, not Android (the external
# projects build json-c and miniupnpc for Android themselves). IGNORE: found, and false.
set(PKG_CONFIG_EXECUTABLE IGNORE CACHE FILEPATH "" FORCE)
set(libevent_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cmake/libevent" CACHE PATH "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../third-party/chiaki-ng chiaki-ng)

add_library(chiaki-jni SHARED
        jni/chiaki-jni.c
        jni/log.c
        jni/video-decoder.c)
target_link_libraries(chiaki-jni chiaki-lib log mediandk android)
# 16 KB pages (Android 15 devices may use them)
target_link_options(chiaki-jni PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
