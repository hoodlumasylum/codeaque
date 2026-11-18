#!/bin/bash
set -euo pipefail
export PATH="/home/box/android/cmake/3.22.1/bin:$PATH"
NDK=/home/box/android/ndk/27.2.12479018
CMAKE=/home/box/android/cmake/3.22.1/bin/cmake
NINJA=/home/box/android/cmake/3.22.1/bin/ninja
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
SRC=/workspace/atari/android/native
OUT=/workspace/atari/android/native/jniLibs
SHERPA=/tmp/sherpa-android/jniLibs
mkdir -p "$OUT"
JOBS=$(nproc)
for ABI in arm64-v8a armeabi-v7a x86_64; do
  echo "======== nova-jni $ABI ========"
  B=/tmp/nova-jni/$ABI
  rm -rf "$B"; mkdir -p "$B"
  LLAMA=/tmp/llama-android/$ABI/bin
  if [ ! -f "$LLAMA/libllama.so" ]; then echo "missing llama $ABI"; exit 1; fi
  "$CMAKE" -S "$SRC" -B "$B" -G Ninja -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DSHERPA_JNI_DIR="$SHERPA" \
    -DLLAMA_LIB_DIR="$LLAMA" \
    -DLLAMA_INCLUDE_DIR=/tmp/llama.cpp/include -DGGML_INCLUDE_DIR=/tmp/llama.cpp/ggml/include
  "$CMAKE" --build "$B" --config Release -j"$JOBS"
  DEST="$OUT/$ABI"
  mkdir -p "$DEST"
  cp -a "$SHERPA/$ABI/libonnxruntime.so" "$SHERPA/$ABI/libsherpa-onnx-c-api.so" "$DEST/"
  cp -a "$LLAMA/"*.so "$DEST/"
  cp -a "$B/"*.so "$DEST/" 2>/dev/null || true
  find "$B" -name 'libnova-*.so' -exec cp -a {} "$DEST/" \;
  # strip
  for so in "$DEST"/*.so; do
    "$STRIP" --strip-unneeded "$so" || true
  done
  echo "---- $ABI ----"
  ls -lh "$DEST"
done
echo NOVA_JNI_OK
