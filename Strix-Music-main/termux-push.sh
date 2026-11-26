#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ZIP_PATH="${1:-}"
if [[ -z "$ZIP_PATH" || ! -f "$ZIP_PATH" ]]; then
  echo "Pakai: bash termux-push.sh /sdcard/Download/Strix-Music-main.zip"
  exit 2
fi

for tool in unzip git tar mktemp find; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "ERROR: Perintah $tool belum tersedia di Termux."
    exit 2
  }
done

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: Jalankan script dari folder hasil git clone repo Strix Music."
  echo "Contoh: cd ~/Strix-Music && bash /path/termux-push.sh /sdcard/Download/Strix-Music-main.zip"
  exit 3
fi

REMOTE_URL="$(git remote get-url origin 2>/dev/null || true)"
if [[ -z "$REMOTE_URL" ]]; then
  read -r -p "Masukkan URL repo GitHub (https://github.com/user/repo.git): " REMOTE_URL
  [[ -n "$REMOTE_URL" ]] || { echo "URL repo kosong."; exit 4; }
  git remote add origin "$REMOTE_URL"
fi

BRANCH="$(git branch --show-current)"
if [[ -z "$BRANCH" ]]; then
  BRANCH="main"
  git checkout -B "$BRANCH"
fi

TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

echo "[1/5] Mengekstrak arsip..."
unzip -q "$ZIP_PATH" -d "$TMP_DIR"
SOURCE_ROOT="$(find "$TMP_DIR" -name settings.gradle.kts -type f -print -quit | xargs -r dirname)"
if [[ -z "$SOURCE_ROOT" || ! -f "$SOURCE_ROOT/app/build.gradle.kts" ]]; then
  echo "ERROR: Struktur project Android tidak ditemukan di dalam ZIP."
  exit 5
fi

echo "[2/5] Menyalin source ke repo lokal (folder .git tetap aman)..."
tar -C "$SOURCE_ROOT" \
  --exclude='./.git' --exclude='./.gradle' --exclude='*/.gradle' \
  --exclude='*/build' --exclude='./build' \
  -cf - . | tar -C "$(pwd)" -xf -

echo "[3/5] Memeriksa perubahan..."
git add -A
git diff --cached --check
if git diff --cached --quiet; then
  echo "Tidak ada perubahan baru untuk di-push."
  exit 0
fi

echo "[4/5] Membuat commit..."
git commit -m "Update Strix Music iOS glass navigation and splash"

echo "[5/5] Push ke GitHub: origin/$BRANCH"
git push -u origin "$BRANCH"
echo "Selesai. Source sudah di-push ke GitHub."
