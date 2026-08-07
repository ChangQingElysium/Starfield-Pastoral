#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT_DIR/tools/ui_atlas_slices.csv"
GUI_DIR="$ROOT_DIR/src/main/resources/assets/stardewcraft/textures/gui"

atlas_path() {
    case "$1" in
        cursors) printf '%s\n' "$GUI_DIR/cursors.png" ;;
        mouse_cursors2) printf '%s\n' "$GUI_DIR/mouse_cursors2.png" ;;
        cursors_1_6) printf '%s\n' "$GUI_DIR/cursors_1_6.png" ;;
        forge_menu) printf '%s\n' "$GUI_DIR/forge/forge_menu.png" ;;
        menu_tiles) printf '%s\n' "$GUI_DIR/animal_query/menu_tiles.png" ;;
        objects_2) printf '%s\n' "$ROOT_DIR/源文件/Content/TileSheets/Objects_2.png" ;;
        billboard) printf '%s\n' "$GUI_DIR/billboard.png" ;;
        animal_white_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/White Chicken.png" ;;
        animal_baby_white_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyWhite Chicken.png" ;;
        animal_brown_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Brown Chicken.png" ;;
        animal_baby_brown_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyBrown Chicken.png" ;;
        animal_blue_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Blue Chicken.png" ;;
        animal_baby_blue_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyBlue Chicken.png" ;;
        animal_void_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Void Chicken.png" ;;
        animal_baby_void_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyVoid Chicken.png" ;;
        animal_golden_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Golden Chicken.png" ;;
        animal_baby_golden_chicken) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyGolden Chicken.png" ;;
        animal_duck) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Duck.png" ;;
        animal_rabbit) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Rabbit.png" ;;
        animal_baby_rabbit) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyRabbit.png" ;;
        animal_dinosaur) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Dinosaur.png" ;;
        animal_white_cow) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/White Cow.png" ;;
        animal_baby_white_cow) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyWhite Cow.png" ;;
        animal_brown_cow) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Brown Cow.png" ;;
        animal_baby_brown_cow) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyBrown Cow.png" ;;
        animal_goat) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Goat.png" ;;
        animal_baby_goat) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyGoat.png" ;;
        animal_sheep) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Sheep.png" ;;
        animal_baby_sheep) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabySheep.png" ;;
        animal_pig) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Pig.png" ;;
        animal_baby_pig) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyPig.png" ;;
        animal_ostrich) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/Ostrich.png" ;;
        animal_baby_ostrich) printf '%s\n' "$ROOT_DIR/源文件/Content/Animals/BabyOstrich.png" ;;
        *)
            printf 'Unknown atlas alias: %s\n' "$1" >&2
            return 1
            ;;
    esac
}

crop_atlas_slice() {
    local source_png="$1"
    local dest_png="$2"
    local u="$3"
    local v="$4"
    local w="$5"
    local h="$6"

    local pixel_width pixel_height padded_png
    pixel_width="$(sips -g pixelWidth "$source_png" | awk '/pixelWidth:/ { print $2; exit }')"
    pixel_height="$(sips -g pixelHeight "$source_png" | awk '/pixelHeight:/ { print $2; exit }')"
    padded_png="$(mktemp -t stardewcraft-ui-atlas-padded).png"
    cp "$source_png" "$padded_png"

    # sips treats --cropOffset 0 0 as a centered crop. Padding the atlas by one
    # pixel lets all manifest coordinates be used as true top-left offsets.
    sips --padToHeightWidth "$((pixel_height + 2))" "$((pixel_width + 2))" "$padded_png" --out "$padded_png" >/dev/null
    sips --cropOffset "$((v + 1))" "$((u + 1))" --cropToHeightWidth "$h" "$w" "$padded_png" --out "$dest_png" >/dev/null
    rm -f "$padded_png"
}

while IFS=, read -r output atlas u v w h || [[ -n "${output:-}" ]]; do
    case "${output:-}" in
        ''|'#'*) continue ;;
    esac

    source_png="$(atlas_path "$atlas")"
    dest_png="$GUI_DIR/$output.png"
    mkdir -p "$(dirname "$dest_png")"

    crop_atlas_slice "$source_png" "$dest_png" "$u" "$v" "$w" "$h"

    printf 'wrote %s (%sx%s from %s:%s,%s)\n' "$dest_png" "$w" "$h" "$atlas" "$u" "$v"
done < "$MANIFEST"
