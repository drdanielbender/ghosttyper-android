# ghosttyper for Android

ghosttyper is an Android keyboard with support for remote speech-to-text transcription.

This project is maintained at [ghosttyper-android](https://github.com/drdanielbender/ghosttyper-android). It is based on [FUTO Keyboard](https://github.com/futo-org/android-keyboard), itself a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME).

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Upstream

Changes from FUTO Keyboard are periodically integrated from the upstream repository. Issues specific to ghosttyper should be reported in the [ghosttyper-android repository](https://github.com/drdanielbender/ghosttyper-android/issues).

## Layouts

If you want to contribute layouts, check out the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://gitlab.futo.org/keyboard/latinime.git
```

If you forgot to specify recursive clone, use this to fetch submodules:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```
