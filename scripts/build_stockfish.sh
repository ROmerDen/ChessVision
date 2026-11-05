#!/usr/bin/env bash
# Сборка Stockfish под Android arm64 и копирование в jniLibs как libstockfish.so.
# Порядок соответствует официальной инструкции: make net → make build (ARCH=armv8 COMP=ndk).
# https://official-stockfish.github.io/docs/stockfish-wiki/Compiling-from-source.html
#
# Требуется: Android NDK r26+, make, git, curl или wget (для make net), bash. Linux и macOS; Windows/MSYS не поддерживается.
# Использование:
#   ANDROID_NDK_HOME=/path/to/ndk scripts/build_stockfish.sh [тег]      (по умолчанию sf_17.1, либо STOCKFISH_TAG)
#   SMOKE_ADB=1 ... — после сборки прогнать uci/isready на подключённом по adb устройстве
# Скрипт НЕ запускался в среде, где создан проект: проверьте его на своей машине.
set -euo pipefail
die() { echo "ОШИБКА: $*" >&2; exit 1; }

: "${ANDROID_NDK_HOME:?Укажите ANDROID_NDK_HOME}"
[ -d "$ANDROID_NDK_HOME" ] || die "ANDROID_NDK_HOME не каталог: $ANDROID_NDK_HOME"
TAG="${1:-${STOCKFISH_TAG:-sf_17.1}}"
API="${ANDROID_API:-26}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/third_party/Stockfish"

NDK_REV="$(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_NDK_HOME/source.properties" 2>/dev/null | head -1)"
[ -n "$NDK_REV" ] || die "не удалось определить версию NDK (нет source.properties)"
[ "${NDK_REV%%.*}" -ge 26 ] || die "нужен NDK r26+, найден $NDK_REV"

case "$(uname -s)" in
  Linux)  HOST=linux-x86_64 ;;
  Darwin) HOST=darwin-x86_64 ;;
  *) die "неподдерживаемая ОС сборки (нужны Linux или macOS)" ;;
esac
BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST/bin"
CXX_BIN="$BIN/aarch64-linux-android${API}-clang++"
[ -x "$CXX_BIN" ] || die "нет компилятора $CXX_BIN"
command -v make >/dev/null || die "нужен make"
command -v curl >/dev/null || command -v wget >/dev/null || die "нужен curl или wget (make net скачивает NNUE-сеть)"

if [ ! -d "$SRC" ]; then
  git clone --depth 1 --branch "$TAG" https://github.com/official-stockfish/Stockfish.git "$SRC"
fi
CUR="$(git -C "$SRC" describe --tags --exact-match 2>/dev/null || true)"
[ "$CUR" = "$TAG" ] || die "в $SRC лежит '$CUR', ожидался '$TAG'. Удалите каталог и запустите снова."

JOBS="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 2)"
cd "$SRC/src"
make clean
make net                       # скачивает и проверяет NNUE-сети, которые встраиваются в бинарник
ls nn-*.nnue >/dev/null 2>&1 || die "make net не оставил файлов nn-*.nnue"
# 16 КБ выравнивание сегментов — требование Android 15+ на устройствах с 16 КБ страницей.
make -j"$JOBS" build ARCH=armv8 COMP=ndk CXX="$CXX_BIN" EXTRALDFLAGS="-Wl,-z,max-page-size=16384"

OUT="$SRC/src/stockfish"
[ -f "$OUT" ] || die "сборка не создала $OUT"

# ---- проверки результата ----
READELF="$BIN/llvm-readelf"
echo "=== ELF HEADER ==="
"$READELF" -h "$OUT"
echo "=== FILE TYPE ==="
file "$OUT"

"$READELF" -h "$OUT" | grep -qi 'AArch64' || die "бинарник действительно не AArch64"
if "$READELF" -d "$OUT" | grep NEEDED | grep -q 'libc++_shared'; then
  die "бинарник зависит от libc++_shared.so; он не будет найден при запуске. Нужна статическая libc++."
fi
bad=0
while IFS= read -r line; do
  align="${line##* }"
  [ $((align)) -ge 16384 ] || bad=1
done < <("$READELF" -lW "$OUT" | grep -E '^[[:space:]]+LOAD')
[ "$bad" -eq 0 ] || die "есть LOAD-сегменты с выравниванием < 16 КБ"
command -v file >/dev/null && file "$OUT" || true

"$BIN/llvm-strip" "$OUT" -o "$OUT.stripped"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"
cp "$OUT.stripped" "$DEST/libstockfish.so"
chmod 755 "$DEST/libstockfish.so"
echo "OK: $DEST/libstockfish.so (Stockfish $TAG, NDK $NDK_REV, API $API)"

if [ "${SMOKE_ADB:-0}" = "1" ]; then
  command -v adb >/dev/null || die "adb не найден"
  adb push "$OUT.stripped" /data/local/tmp/sf_smoke >/dev/null
  adb shell chmod 755 /data/local/tmp/sf_smoke
  RESP="$(printf 'uci\nisready\nquit\n' | adb shell /data/local/tmp/sf_smoke | tr -d '\r')"
   echo "$RESP" | grep -qx uciok  || die "смоук-тест: нет uciok"
  echo "$RESP" | grep -qx readyok || die "смоук-тест: нет readyok"
  echo "Смоук-тест на устройстве: OK (это запуск из /data/local/tmp, не из nativeLibraryDir приложения)"
fi
