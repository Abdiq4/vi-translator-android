# The build image: Ubuntu with the JDK, the Android SDK and NDK, and what chiaki-ng's native build
# needs (protoc and Python's protobuf for nanopb; Perl and make for OpenSSL). docker/build.sh uses it.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      openjdk-21-jdk-headless git curl unzip ca-certificates \
      cmake ninja-build make perl pkg-config python3 python3-protobuf protobuf-compiler file \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ARG CMDLINE_TOOLS=13114758
ARG NDK_VERSION=28.2.13676358
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
    && curl -fsSL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip \
    && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm /tmp/tools.zip \
    && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null \
    && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install \
         "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;${NDK_VERSION}" > /dev/null
ENV ANDROID_NDK_HOME=$ANDROID_HOME/ndk/${NDK_VERSION}
ENV PATH=$PATH:$ANDROID_HOME/platform-tools

# (the repository is mounted here; git refuses a checkout owned by another user without this)
RUN git config --global --add safe.directory '*'
WORKDIR /src
