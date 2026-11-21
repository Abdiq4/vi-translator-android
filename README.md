<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="VI Translator logo" width="160" />
</p>

<h1 align="center">VI Translator - Android</h1>

<p align="center">Live translations of your console game on your TV, with only a phone: no PC.</p>

---

## What it does

The phone reads the game from your console over the home network, and translates its subtitles and
objectives. Only the translated text goes to the TV, where a VI Translator TV app
([Android TV](https://github.com/vi-translator/vi-translator-android-tv), LG webOS, Samsung Tizen)
draws it over the game. The TV keeps showing the console's own HDMI picture at full quality.

- **PlayStation 5:** PS Remote Play, through [chiaki-ng](https://github.com/streetpea/chiaki-ng).
  The stream is decoded straight into memory, never shown, with no sound; about 4 frames a second
  are read.
- **Xbox:** Microsoft's own Remote Play web player (`play.xbox.com/remoteplay`) inside the app; its
  picture is copied about 4 times a second.
- **Reading:** Google ML Kit text recognition, on the phone (Latin script).
- **Translating and the TVs:** VI Translator's shared core (the `vi-translator-core` Rust crate),
  which also serves the TV page on the home network, so the TV apps connect to the phone just as
  they do to the PC app.

## Setup

1. **Console.**
   - *PS5:* turn on Remote Play on the PS5 (Settings › System › Remote Play). In the app, find the
     PS5, sign in to PSN **with a second account, not the one you play with** (Remote Play signs it
     in on the PS5; the app keeps only its account ID), and type the 8-digit PIN the PS5 shows under
     *Link Device*.
   - *Xbox:* turn on remote features on the Xbox. *Start translating* opens Microsoft's player; sign
     in there with your Microsoft account and connect to your console.
2. **TV:** open VI Translator on the TV: it shows a 6-digit code. In the app, *Find TVs*, pick it,
   type the code, *Connect*.
3. **Game and language**, then **Start translating**. Keep the app open while playing (with a PS5
   the phone's screen can be off; the Xbox player needs it on).

## Build

The PS Remote Play library (chiaki-ng, in `third-party/chiaki-ng`, a submodule pinned to a release)
is built on Linux only: it needs `protoc` and builds OpenSSL from source. Its Android JNI layer is in
`native/jni` (from chiaki-ng's Android app, with this app's package and no audio).

```sh
git submodule update --init         # chiaki-ng (native/build.sh fetches its own submodules)
docker/build.sh                     # in Docker: the native libraries, then the debug APK
docker/build.sh native              # the native libraries only, into native/out/jniLibs
./gradlew assembleDebug             # (any OS) the app, with native/out/jniLibs if it is there
```

Without `native/out/jniLibs`, the app still builds and runs: PS Remote Play then says it isn't in
this build. `-PrequireNative` makes Gradle refuse to build without it (CI and releases).

On Linux with the NDK, `native/build.sh` runs without Docker (`ANDROID_NDK_HOME`, plus `ninja`,
`protoc` and Python's `protobuf`). `ABIS="arm64-v8a"` builds one ABI (default: `arm64-v8a x86_64`).

### The core

The app talks to the core through `core/Core.kt`. Until the core's Android build is in, a stand-in
(`StubCore`) answers: the real TV connection and TV page, but no translation memory (lines are
marked with the language). With `VIT_CORE_DIR` pointing at the core crate, `native/build.sh` also
builds `libvi_translator_core.so` with `cargo ndk`. The app switches to the real core once
`core/RustCore.kt` (over the core's UniFFI bindings) is added: `Cores.create` picks it up by name.

### Testing on emulators

Two emulators can't see each other (each is behind its own NAT): go through the PC with adb.

```sh
adb -s <tv> forward tcp:18792 tcp:8792      # the phone reaches the TV app at 10.0.2.2:18792
adb -s <phone> forward tcp:8791 tcp:8791    # the TV reaches the phone's page at 10.0.2.2:8791
adb -s <phone> shell am start -n app.vitranslator.phone/.ui.MainActivity --es advertise 10.0.2.2:8791
```

(`advertise`, debug builds only: the address the phone gives the TV for its page.)

## Releases

Pushing a tag like `v1.2.3` builds the signed APK and attaches it to a GitHub Release
(`.github/workflows/release.yml`); the app offers each newer release and hands it to Android's
installer, which only installs it over the app when it is signed with the same key. The key is in
the repository's secrets: `ANDROID_KEYSTORE_B64` (the keystore, base64), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. Keep a backup of the keystore.

## License

[GNU AGPL v3](LICENSE), as it includes chiaki-ng (AGPL-3.0, with an OpenSSL exception); chiaki-ng's
own files keep their license notices. PlayStation, PS5 and PS Remote Play are trademarks of Sony
Interactive Entertainment; Xbox is a trademark of Microsoft. This app isn't made or endorsed by
either.
