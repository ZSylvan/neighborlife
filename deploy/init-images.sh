#!/bin/sh
set -eu
cd /seed
 
 
find . -type f | while IFS= read -r image; do
  target="/uploads/$image"
  mkdir -p "$(dirname "$target")"
  if [ ! -e "$target" ]; then
    cp -p "$image" "$target"
  fi
done
