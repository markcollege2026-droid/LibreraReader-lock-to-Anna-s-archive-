#!/usr/bin/env bash
# Fail fast, including native background jobs: the legacy interactive script does not.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
MUPDF="$ROOT/Builder/mupdf-1.28.5"
NDK="${ANDROID_HOME:?ANDROID_HOME must name the SDK}/ndk/27.2.12479018/ndk-build"
if [[ ! -d "$MUPDF/.git" ]]; then
  git clone --depth 1 --branch 1.28.5 --recurse-submodules --shallow-submodules \
    https://github.com/ArtifexSoftware/mupdf.git "$MUPDF"
fi
if [[ ! -f "$MUPDF/platform/librera/libs/x86_64/libmupdf.so" ]]; then
  # Use the maintained patch/link procedure, but run ndk-build serially and check every ABI.
  mkdir -p "$HOME/.gradle"
  printf '\nlibrera_ndk_dir=%s\n' "$(dirname "$NDK")" >> "$HOME/.gradle/gradle.properties"
  # Generate the headers and resources MuPDF's Android makefiles require.
  make -C "$MUPDF" generate
  mkdir -p "$MUPDF/platform/librera"
  cp -a "$ROOT/Builder/jni" "$MUPDF/platform/librera/"
  cp "$ROOT/Builder/jni/Android-1.28.5.mk" "$MUPDF/platform/librera/jni/Android.mk"
  PATCHES="$ROOT/Builder/jni/~mupdf-1.28.5"
  for f in css-apply.c epub-doc.c html-layout.c html-parse.c; do cp "$PATCHES/$f" "$MUPDF/source/html/"; done
  for f in mucbz.c muimg.c; do cp "$PATCHES/$f" "$MUPDF/source/cbz/"; done
  for f in load-webp.c image.c unzip.c directory.c xml.c list-device.c image-imp.h; do cp "$PATCHES/$f" "$MUPDF/source/fitz/"; done
  cp "$PATCHES/pdf-xref.c" "$MUPDF/source/pdf/"
  for f in compressed-buffer.h context.h; do cp "$PATCHES/$f" "$MUPDF/include/mupdf/fitz/"; done
  for f in j2k.c pi.c; do cp "$PATCHES/$f" "$MUPDF/thirdparty/openjpeg/src/lib/openjp2/"; done
  for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    (cd "$MUPDF/platform/librera" && "$NDK" -j2 NDK_APPLICATION_MK=jni/Application.mk APP_ABI="$abi" APP_PLATFORM=android-24)
  done
fi
for variant in main noMupdf; do
  mkdir -p "$ROOT/app/src/$variant/jniLibs"
  for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    source="$MUPDF/platform/librera/libs/$abi"
    test -f "$source/libmupdf.so"
    if [[ "$variant" == noMupdf ]]; then
      dest="$MUPDF/platform/librera/libs-nomupdf/$abi"
      mkdir -p "$dest"
      for library in "$source"/*.so; do
        [[ "$(basename "$library")" == libmupdf.so ]] || cp "$library" "$dest/"
      done
      source="$dest"
    fi
    ln -sfn "$source" "$ROOT/app/src/$variant/jniLibs/$abi"
  done
done
