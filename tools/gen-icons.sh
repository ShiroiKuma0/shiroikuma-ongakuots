#!/bin/bash
# Regenerate every raster icon of the 白い熊 音楽乙 fork from the one design source,
# design/shiroikuma-ongakuots-icon.svg (which carries the same geometry as the adaptive vector
# androidApp/src/main/res/drawable/ic_launcher_ongakuots_foreground.xml on the black background).
#
# Needs: rsvg-convert (librsvg2-bin), magick (ImageMagick 7).
# Run from the repo root:  bash tools/gen-icons.sh
set -euo pipefail

cd "$(dirname "$0")/.."
SRC=design/shiroikuma-ongakuots-icon.svg
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# Render the master once, big, then derive every size from it.
rsvg-convert -w 1024 -h 1024 "$SRC" -o "$TMP/master.png"

# $1 = size, $2 = shape (round|square), $3 = output path
cut_icon() {
  local size=$1 shape=$2 out=$3 r c
  magick "$TMP/master.png" -resize "${size}x${size}" "$TMP/base.png"
  if [ "$shape" = round ]; then
    c=$(( (size - 1) / 2 ))
    magick -size "${size}x${size}" xc:black -fill white \
      -draw "circle $c,$c $c,0" "$TMP/mask.png"
  else
    # Legacy square icons are drawn as-is by the launcher, so they carry their own
    # rounded corners — ~22.5% of the edge, matching upstream's rasters.
    r=$(awk "BEGIN{printf \"%d\", $size*0.225}")
    magick -size "${size}x${size}" xc:black -fill white \
      -draw "roundrectangle 0,0,$((size-1)),$((size-1)),$r,$r" "$TMP/mask.png"
  fi
  magick "$TMP/base.png" "$TMP/mask.png" -alpha off -compose CopyOpacity -composite "$out"
}

# --- launcher rasters (legacy, pre-adaptive launchers) ---
i=0
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  sizes=(48 72 96 144 192)
  fg_sizes=(108 162 216 324 432)
  s=${sizes[$i]}
  f=${fg_sizes[$i]}
  dir=androidApp/src/main/res/mipmap-$d
  cut_icon "$s" square "$TMP/l.png"       && magick "$TMP/l.png" "$dir/ic_launcher.webp"
  cut_icon "$s" round  "$TMP/r.png"       && magick "$TMP/r.png" "$dir/ic_launcher_round.webp"
  # The adaptive icons point at the vector drawables, so this one is no longer referenced;
  # regenerated anyway so no upstream artwork is left behind in the resource tree.
  magick "$TMP/master.png" -resize "${f}x${f}" "$dir/ic_launcher_foreground.webp"
  i=$((i + 1))
done

# --- in-app icons ---
cut_icon 432 square "$TMP/a.png"
cp "$TMP/a.png" androidApp/src/main/res/drawable/app_icon.png
cp "$TMP/a.png" composeApp/src/commonMain/composeResources/drawable/app_icon.png
cut_icon 432 round composeApp/src/commonMain/composeResources/drawable/circle_app_icon.png

# --- store listing ---
for loc in en-US vi-VN; do
  cut_icon 512 square "fastlane/metadata/android/$loc/images/icon.png"
done

echo "icons regenerated from $SRC"
