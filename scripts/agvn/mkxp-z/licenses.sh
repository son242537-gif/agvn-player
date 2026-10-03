#!/usr/bin/env bash
# AGVN Player - the licenses of mkxp-z and of every library linked into libmkxp-z.so, in one text file for the app's
# "Giấy phép mã nguồn mở" screen. Usage: licenses.sh <mkxp-z source dir after the build> <output file>
# Copyright (c) 2026 agvn.io.vn - MIT License.
set -euo pipefail
SRC="$1"
OUT="$2"
S="$SRC/subprojects"

# name | what it is | license | files (relative to the mkxp-z source dir)
ENTRIES=(
"mkxp-z|RPG Maker XP/VX/VX Ace engine (https://github.com/mkxp-z/mkxp-z)|GPL-3.0-or-later (built with its GPLv3 shaders)|assets/LICENSE.mkxp-z-with-https.txt"
"WenQuanYi Micro Hei|font built into mkxp-z|Apache-2.0, or GPL-3.0 with the font embedding exception|"
"Ruby 3.1.3|the Ruby that runs the game's scripts|Ruby License or BSD-2-Clause; third-party parts in LEGAL|subprojects/ruby-3_1_3/COPYING subprojects/ruby-3_1_3/BSDL subprojects/ruby-3_1_3/LEGAL"
"libffi 3.5.2|used by Ruby's Fiddle and mkxp-z's MiniFFI|MIT|subprojects/libffi-3.5.2/LICENSE"
"libyaml 0.2.5|used by Ruby's Psych|MIT|subprojects/libyaml-0.2.5/License"
"SDL 2.28.1|window, input, OpenGL ES|zlib|subprojects/SDL2-2.28.1/LICENSE.txt"
"SDL_image 2.6.3|images (with stb_image, public domain or MIT)|zlib|subprojects/sdl2_image/LICENSE.txt"
"libjxl|JPEG XL images in SDL_image|BSD-3-Clause|subprojects/sdl2_image/external/libjxl/LICENSE subprojects/sdl2_image/external/libjxl/PATENTS"
"Brotli|used by libjxl|MIT|subprojects/sdl2_image/external/libjxl/third_party/brotli/LICENSE"
"Highway|used by libjxl|Apache-2.0 or BSD-3-Clause|subprojects/sdl2_image/external/libjxl/third_party/highway/LICENSE"
"SDL_sound 2.0.4|audio files (with dr_flac, dr_mp3 and libmodplug, public domain)|zlib|subprojects/SDL2_sound-2.0.4/LICENSE.txt"
"SDL_ttf 2.0.18|text|zlib|subprojects/SDL_ttf-release-2.0.18/COPYING.txt"
"FreeType 2.14.3|fonts|FreeType License (FTL)|subprojects/freetype-VER-2-14-3/LICENSE.TXT subprojects/freetype-VER-2-14-3/docs/FTL.TXT"
"OpenAL Soft 1.24.3|sound|LGPL-2.1-or-later|subprojects/openal-soft-1.24.3/COPYING subprojects/openal-soft-1.24.3/LICENSE-pffft"
"FluidSynth 2.5.0|MIDI music|LGPL-2.1-or-later|subprojects/fluidsynth-2.5.0/LICENSE"
"gcem 1.18.0|used by FluidSynth|Apache-2.0|subprojects/gcem-1.18.0/LICENSE subprojects/gcem-1.18.0/NOTICE.txt"
"PhysicsFS 3.2.0|game archives and folders|zlib|subprojects/icculus-physfs-eb3383b/LICENSE.txt"
"pixman 0.46.4|pixel operations|MIT|subprojects/pixman-0.46.4/COPYING"
"libogg 1.3.6, libvorbis 1.3.7, libtheora 1.2.0|Ogg audio and video|BSD-3-Clause|subprojects/libogg-1.3.6/COPYING subprojects/libvorbis-1.3.7/COPYING subprojects/libtheora-1.2.0/COPYING"
"TheoraPlay|video playback|zlib|subprojects/theoraplay-ec1aad1ee43592f216946cdc03b1ee4c4e1e2e59/LICENSE.txt"
"uchardet 0.0.8|text encoding detection|MPL-1.1, GPL-2.0-or-later or LGPL-2.1-or-later|subprojects/uchardet-0.0.8/COPYING"
"{fmt} 12.1.0|used by OpenAL Soft|MIT|subprojects/fmt-12.1.0/LICENSE"
"zlib 1.3.1|compression|zlib|subprojects/zlib-1.3.1/LICENSE"
)

{
    echo "RPG Maker XP/VX/VX Ace (\"Chạy nhẹ\"): mkxp-z and the libraries in libmkxp-z.so"
    echo "Source code and build script: scripts/agvn/mkxp-z in the AGVN Player repository, at the tag of this release."
    echo
    for entry in "${ENTRIES[@]}"; do
        IFS='|' read -r name what license files <<< "$entry"
        echo "------------------------------------------------------------------------"
        echo "$name - $what"
        echo "License: $license"
        for f in $files; do
            [ -f "$SRC/$f" ] || { echo "[AGVN mkxp-z] LỖI: thiếu $f" >&2; exit 1; }
            echo
            cat "$SRC/$f"
        done
        echo
    done
} > "$OUT"
echo "[AGVN mkxp-z] $(basename "$OUT"): ${#ENTRIES[@]} thành phần"
