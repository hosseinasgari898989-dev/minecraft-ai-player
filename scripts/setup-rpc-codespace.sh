#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/.cache/llama.cpp"
BUILD="$SRC/build-rpc"

if [ ! -d "$SRC/.git" ]; then
  mkdir -p "$ROOT/.cache"
  git clone --depth=1 https://github.com/ggml-org/llama.cpp.git "$SRC"
fi

cmake -S "$SRC" -B "$BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_RPC=ON \
  -DGGML_NATIVE=OFF \
  -DGGML_CPU_ALL_VARIANTS=ON

cmake --build "$BUILD" --config Release --target ggml-rpc-server --parallel 2

echo
echo "RPC server ready:"
echo "$BUILD/bin/ggml-rpc-server"
echo
echo "Run it with:"
echo "$BUILD/bin/ggml-rpc-server -p 50052"
