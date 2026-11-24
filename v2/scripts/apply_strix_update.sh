#!/data/data/com.termux/files/usr/bin/bash
set -e

SOURCE="/sdcard/StrixMusic"
TARGET="$HOME/Strix-Music"

echo "=== STRIX MUSIC UPDATE ==="

if [ ! -d "$SOURCE/app/src/main" ]; then
    echo "ERROR: Source update tidak ditemukan."
    exit 1
fi

if [ ! -d "$TARGET/.git" ]; then
    echo "ERROR: Repository Git tidak ditemukan."
    exit 1
fi

if [ ! -f "$SOURCE/STRIX-UPDATE-NOTES.md" ]; then
    echo "ERROR: Catatan update tidak ditemukan."
    exit 1
fi

echo "Source : $SOURCE"
echo "Target : $TARGET"
echo
echo "Menyalin source update..."

tar -cf - \
    --exclude='./.git' \
    -C "$SOURCE" . | tar -xf - -C "$TARGET"

echo
echo "Update source selesai."
echo "Memeriksa perubahan Git..."

cd "$TARGET"
git status --short
