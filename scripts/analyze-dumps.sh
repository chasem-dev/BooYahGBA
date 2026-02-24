#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! ls dumps/frame-*.txt >/dev/null 2>&1; then
  echo "No dump txt files found in $ROOT_DIR/dumps"
  exit 1
fi

tmp_csv="$(mktemp)"
trap 'rm -f "$tmp_csv"' EXIT

echo "file,frameARGBCRC32,vramBGCRC32,vramOBJCRC32,oamCRC32,BLDCNT,BLDALPHA,WININ,WINOUT,DMA3SAD,DMA3DAD,DMA3CNT_L"
for file in $(ls -1 dumps/frame-*.txt | sort); do
  frame="$(awk -F= '/^frameARGBCRC32=/{print $2}' "$file")"
  vrambg="$(awk -F= '/^vramBGCRC32=/{print $2}' "$file")"
  vramobj="$(awk -F= '/^vramOBJCRC32=/{print $2}' "$file")"
  oam="$(awk -F= '/^oamCRC32=/{print $2}' "$file")"
  bldcnt="$(awk -F= '/^BLDCNT=/{print $2}' "$file")"
  bldalpha="$(awk -F= '/^BLDALPHA=/{print $2}' "$file")"
  winin="$(awk -F= '/^WININ=/{print $2}' "$file")"
  winout="$(awk -F= '/^WINOUT=/{print $2}' "$file")"
  dma3sad="$(awk -F= '/^DMA3SAD=/{print $2}' "$file")"
  dma3dad="$(awk -F= '/^DMA3DAD=/{print $2}' "$file")"
  dma3cnt="$(awk -F= '/^DMA3CNT_L=/{print $2}' "$file")"
  line="$(basename "$file"),$frame,$vrambg,$vramobj,$oam,$bldcnt,$bldalpha,$winin,$winout,$dma3sad,$dma3dad,$dma3cnt"
  echo "$line"
  echo "$line" >> "$tmp_csv"
done

echo
echo "Grouped state signatures (count x vramBG,vramOBJ,oam,BLDCNT,BLDALPHA,DMA3SAD,DMA3DAD):"
awk -F, '{ key=$3","$4","$5","$6","$7","$10","$11; count[key]++ } END { for (k in count) print count[k] "x " k }' "$tmp_csv" | sort -nr
