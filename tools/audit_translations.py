#!/usr/bin/env python3
import argparse
import json
import re
from collections import OrderedDict, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = ROOT / "src/main/resources/assets/stardewcraft/lang"
DATA_DIR = ROOT / "src/main/resources/data/stardewcraft"
JAVA_DIR = ROOT / "src/main/java"
CONTENT_DIR = ROOT / "源文件/Content"
LOCALIZED_GUI_DIR = ROOT / "src/main/resources/assets/stardewcraft/textures/gui/localized"
SUPPORTED_LOCALES = (
    "en_us",
    "zh_cn",
    "ru_ru",
    "fr_fr",
    "de_de",
    "es_es",
    "pt_br",
    "ja_jp",
    "ko_kr",
    "it_it",
    "tr_tr",
    "hu_hu",
)
OFFICIAL_LOCALE_SUFFIXES = {
    "zh_cn": "zh-CN",
    "ru_ru": "ru-RU",
    "fr_fr": "fr-FR",
    "de_de": "de-DE",
    "es_es": "es-ES",
    "pt_br": "pt-BR",
    "ja_jp": "ja-JP",
    "ko_kr": "ko-KR",
    "it_it": "it-IT",
    "tr_tr": "tr-TR",
    "hu_hu": "hu-HU",
}
REQUIRED_LOCALIZED_GUI_ASSETS = (
    "billboard/calendar_background.png",
    "billboard/daily_quest_background.png",
    "bundle/bundle_complete.png",
    "bundle/purchase.png",
    "fishing/caught_popup.png",
    "fishing/max.png",
    "joja/joja_cd_form.png",
)
LOCALIZED_GUI_ASSET_SOURCES = {
    "billboard/calendar_background.png": ("Billboard", 0, 198, 301, 198),
    "billboard/daily_quest_background.png": ("Billboard", 0, 0, 338, 198),
    "bundle/bundle_complete.png": ("Cursors", 128, 1367, 150, 14),
    "bundle/purchase.png": ("JunimoNote", 517, 286, 65, 20),
    "fishing/caught_popup.png": ("Cursors", 612, 1913, 74, 30),
    "fishing/max.png": ("Cursors", 545, 1921, 53, 19),
    "joja/joja_cd_form.png": ("JojaCDForm", 0, 0, 320, 160),
}
KNOWN_TRANSLATION_POLLUTION = {
    "ja_jp": ("製品情報", "サイトマップ", "よくある質問", "お問い合わせ"),
    "ko_kr": ("이름 *", "제품정보", "팟캐스트", "사이트맵", "뚝 베어", "회사 소개"),
}
HAN_ALLOWED_LOCALES = {"zh_cn", "ja_jp"}
RUNTIME_ASSET_JSON = (
    ROOT / "src/main/resources/assets/stardewcraft/stardew_hat_index.json",
)

KEY_PREFIXES = (
    "advancements.stardewcraft.",
    "block.stardewcraft.",
    "container.stardewcraft.",
    "entity.stardewcraft.",
    "event.stardewcraft.",
    "gui.stardewcraft.",
    "item.stardewcraft.",
    "message.stardewcraft.",
    "recipe.stardewcraft.",
    "stardewcraft.",
)

SKIP_PREFIXES = (
    # These are dynamic item names. SmokedFishItem builds the displayed name from
    # stardewcraft.preserve.smoked_fish.flavored_name + the source fish name.
    "item.stardewcraft.smoked_",
    "stardewcraft.type.",
)

SKIP_KEYS = {
    # Examples/debug/system-property keys, not user-facing translations.
    "block.stardewcraft.xxx",
    "item.stardewcraft.xxx.desc",
    "stardewcraft.eagerPregenBiomeMigration",
    "stardewcraft.event.some_message",
    "stardewcraft.event.test.line1",
    "stardewcraft.key",
    "stardewcraft.npcMovementDebug",
    "stardewcraft.iridium_milk_consumed",
    "stardewcraft.secret_note_10_scene_pending",
    "stardewcraft.secret_woods_open",
}

SKIP_BLOCK_SUFFIXES = (
    # Internal helper blocks rendered by block entities.
    "_top_render",
)

# These values are intentionally proper names. Matching an English name like
# "Ginger" or "Poppy" to an unrelated vanilla common-noun string must not make
# the audit replace the name with the translated noun.
OFFICIAL_VALUE_REUSE_SKIP_PREFIXES = (
    "gui.stardewcraft.farm_selection.random_name.",
    # One-letter JEI units (d/h/m) collide with unrelated one-letter values in
    # the official content tables, so value-only source matching is invalid.
    "stardewcraft.jei.time.",
    "stardewcraft.animal.random_name.",
    "stardewcraft.farmer_title.",
    "stardewcraft.totem.random_name.",
)

STATIC_STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
JAVA_TRANSLATABLE_RE = re.compile(r'(?:Component|TextComponent)?\.?translatable\(\s*"((?:[^"\\]|\\.)*)"')
TEXT_JSON_TRANSLATE_RE = re.compile(r'\\"translate\\":\\"([^"\\]+)\\"')
HAN_RE = re.compile(r"[\u3400-\u9fff]")
PRIVATE_USE_RE = re.compile(r"[\ue000-\uf8ff]|ZXQSEG")
PATHOLOGICAL_REPEAT_RE = re.compile(
    r"(?i)\b([A-Za-zÀ-ÖØ-öø-ÿА-Яа-яЁёĞğİıŞşÇçÖöÜüŐőŰű]{3,})\b"
    r"(?:[\s,.;:!?()\-]+\1\b){3,}"
)
PLACEHOLDER_RE = re.compile(r"%(?:(\d+)\$)?([sdf])(?![A-Za-z]{2})")
STARDEW_CONTROL_RE = re.compile(
    r"%(?:noturn|farm|fork|pet|revealtaste|spouse|time|secretsanta|noun|season|firstnameletter|name)"
    r"(?![A-Za-z])"
)
JAVA_FORMAT_RE = re.compile(r"%(?:\d+\$)?[sdf]")
DIALOGUE_CONTROL_RE = re.compile(r"\$query|\$(?:\d+|[A-Za-z])")
DIALOGUE_BRANCH_RE = re.compile(r"(?:^|#)\$[qr]\s+-?\d+\s+-?\d+\s+[A-Za-z0-9_]+")
QUOTED_TEXT_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
ENGLISH_PROSE_WORD_RE = re.compile(r"[A-Za-z]{2,}")
NON_RUNTIME_JSON_FIELDS = {"note", "comment", "comments"}


STRING_KEY_MAP = {
    "block.stardewcraft.beach_artifact_spot": ("Strings/Objects", "ArtifactSpot_Name"),
    "block.stardewcraft.large_boulder": ("Strings/BigCraftables", "Boulder_Name"),
    "stardewcraft.mail.pamNewChannel": ("Data/mail", "pamNewChannel"),
    "stardewcraft.menu.community_center": ("Strings/UI", "GameMenu_JunimoNote_Hover"),
    "stardewcraft.trout_derby.booth.intro": ("Strings/1_6_Strings", "FishingDerbyBooth_Intro"),
    "stardewcraft.trout_derby.booth.explanation": ("Strings/1_6_Strings", "FishingDerbyBooth_Explanation"),
    "stardewcraft.trout_derby.booth.explanation.choice": ("Strings/1_6_Strings", "FishingDerbyBooth_Explanation"),
    "stardewcraft.trout_derby.booth.no_tags": ("Strings/1_6_Strings", "FishingDerbyBooth_NoTags"),
    "stardewcraft.trout_derby.booth.get_rewards": ("Strings/1_6_Strings", "GetRewards"),
    "stardewcraft.trout_derby.booth.leave": ("Strings/1_6_Strings", "Leave"),
    "stardewcraft.trout_derby.booth.bag_full": ("Strings/UI", "Forge_noroom"),
}

OBJECT_NAME_KEYS_BY_BLOCK = {
    "fall_wild_seed_crop": "FallSeeds_Name",
    "rice_crop": "Rice_Name",
    "spring_wild_seed_crop": "SpringSeeds_Name",
    "summer_wild_seed_crop": "SummerSeeds_Name",
    "winter_wild_seed_crop": "WinterSeeds_Name",
}

GIANT_CROP_OBJECT_KEYS = {
    "giant_cauliflower": "Cauliflower_Name",
    "giant_melon": "Melon_Name",
    "giant_powdermelon": "Powdermelon_Name",
    "giant_pumpkin": "Pumpkin_Name",
}

ITEM_DESCRIPTION_KEYS = {
    "artichoke": "Artichoke_Description",
    "beet": "Beet_Description",
    "blueberry": "Blueberry_Description",
    "cauliflower": "Cauliflower_Description",
    "coffee_bean": "CoffeeBean_Description",
    "copper_ore": "CopperOre_Description",
    "corn": "Corn_Description",
    "eggplant": "Eggplant_Description",
    "garlic": "Garlic_Description",
    "gold_ore": "GoldOre_Description",
    "iridium_ore": "IridiumOre_Description",
    "iron_ore": "IronOre_Description",
    "parsnip": "Parsnip_Description",
    "pumpkin": "Pumpkin_Description",
    "red_cabbage": "RedCabbage_Description",
    "starfruit": "Starfruit_Description",
    "summer_spangle": "SummerSpangle_Description",
    "tomato": "Tomato_Description",
    "tulip": "Tulip_Description",
    "wheat": "Wheat_Description",
}


def load_json(path):
    with path.open(encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=OrderedDict)


def load_json_with_duplicates(path):
    duplicates = []

    def hook(pairs):
        result = OrderedDict()
        for key, value in pairs:
            if key in result:
                duplicates.append(key)
            result[key] = value
        return result

    with path.open(encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=hook), sorted(set(duplicates))


def write_json(path, data):
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def is_translation_key(value):
    if not isinstance(value, str):
        return False
    if value in SKIP_KEYS:
        return False
    if value.endswith(".") or value.endswith("_"):
        return False
    if ":" in value or " " in value or "\n" in value:
        return False
    if value.startswith(SKIP_PREFIXES):
        return False
    if value.startswith("block.stardewcraft.") and value.endswith(SKIP_BLOCK_SUFFIXES):
        return False
    return value.startswith(KEY_PREFIXES)


def unescape_java_string(value):
    try:
        return bytes(value, "utf-8").decode("unicode_escape")
    except UnicodeDecodeError:
        return value


def collect_hardcoded_java_han():
    findings = []
    for path in JAVA_DIR.rglob("*.java"):
        rel = path.relative_to(ROOT)
        text = path.read_text(encoding="utf-8", errors="ignore")
        index = 0
        line = 1
        state = "code"
        string_line = 1
        value = []
        while index < len(text):
            char = text[index]
            following = text[index + 1] if index + 1 < len(text) else ""
            if state == "code":
                if char == "/" and following == "/":
                    state = "line_comment"
                    index += 2
                    continue
                if char == "/" and following == "*":
                    state = "block_comment"
                    index += 2
                    continue
                if char == '"':
                    state = "string"
                    string_line = line
                    value = []
                    index += 1
                    continue
                if char == "'":
                    state = "char"
            elif state == "line_comment":
                if char == "\n":
                    state = "code"
            elif state == "block_comment":
                if char == "*" and following == "/":
                    state = "code"
                    index += 2
                    continue
            elif state == "char":
                if char == "\\":
                    index += 2
                    continue
                if char == "'":
                    state = "code"
            elif state == "string":
                if char == "\\":
                    if following == "u" and index + 5 < len(text):
                        try:
                            value.append(chr(int(text[index + 2:index + 6], 16)))
                            index += 6
                            continue
                        except ValueError:
                            pass
                    index += 2
                    continue
                if char == '"':
                    decoded = "".join(value)
                    if HAN_RE.search(decoded):
                        findings.append({"path": str(rel), "line": string_line, "value": decoded})
                    state = "code"
                else:
                    value.append(char)
            if char == "\n":
                line += 1
            index += 1
    return findings


def collect_hardcoded_data_han():
    findings = []
    for path in runtime_json_paths():
        rel = path.relative_to(ROOT)
        try:
            root = load_json(path)
        except json.JSONDecodeError:
            continue

        def visit(value, json_path):
            if any(any(part.lower().lstrip("_").startswith(field) for field in NON_RUNTIME_JSON_FIELDS)
                   for part in json_path):
                return
            if HAN_RE.search(value):
                findings.append({
                    "path": str(rel),
                    "json_path": ".".join(json_path),
                    "value": value,
                })

        walk_json_strings(root, visit)
    return findings


def placeholder_signature(value):
    sequential_index = 0
    signature = []
    for explicit_index, kind in PLACEHOLDER_RE.findall(value):
        if explicit_index:
            index = int(explicit_index)
        else:
            sequential_index += 1
            index = sequential_index
        signature.append(f"{index}:{kind}")
    return sorted(signature)


def stardew_control_signature(value):
    percent_controls = STARDEW_CONTROL_RE.findall(value)
    without_java_formats = JAVA_FORMAT_RE.sub("", value)
    dialogue_controls = DIALOGUE_CONTROL_RE.findall(without_java_formats)
    branch_controls = DIALOGUE_BRANCH_RE.findall(value)
    return sorted(percent_controls + dialogue_controls + branch_controls)


def png_dimensions(path):
    """Read PNG dimensions without adding an image-library dependency."""
    data = path.read_bytes()[:24]
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def collect_localized_gui_asset_issues():
    issues = []
    try:
        from PIL import Image
    except ImportError:
        return [{
            "problem": "validator_unavailable",
            "detail": "Install Pillow to verify localized GUI pixels against official atlases.",
        }]
    baseline_dimensions = {}
    for relative_path in REQUIRED_LOCALIZED_GUI_ASSETS:
        path = LOCALIZED_GUI_DIR / "en_us" / relative_path
        baseline_dimensions[relative_path] = png_dimensions(path) if path.exists() else None
    for locale in SUPPORTED_LOCALES:
        for relative_path in REQUIRED_LOCALIZED_GUI_ASSETS:
            path = LOCALIZED_GUI_DIR / locale / relative_path
            if not path.exists():
                issues.append({"locale": locale, "asset": relative_path, "problem": "missing"})
                continue
            dimensions = png_dimensions(path)
            if dimensions is None:
                issues.append({"locale": locale, "asset": relative_path, "problem": "invalid_png"})
            elif dimensions != baseline_dimensions[relative_path]:
                issues.append({
                    "locale": locale,
                    "asset": relative_path,
                    "problem": "dimension_mismatch",
                    "current": dimensions,
                    "en_us": baseline_dimensions[relative_path],
                })
                continue

            atlas, u, v, width, height = LOCALIZED_GUI_ASSET_SOURCES[relative_path]
            suffix = "" if locale == "en_us" else f".{OFFICIAL_LOCALE_SUFFIXES[locale]}"
            source_path = CONTENT_DIR / "LooseSprites" / f"{atlas}{suffix}.png"
            if not source_path.exists():
                issues.append({
                    "locale": locale,
                    "asset": relative_path,
                    "problem": "official_source_missing",
                    "source": str(source_path.relative_to(ROOT)),
                })
                continue
            with Image.open(source_path) as source_image, Image.open(path) as localized_image:
                official_crop = source_image.convert("RGBA").crop((u, v, u + width, v + height))
                localized_pixels = localized_image.convert("RGBA")
                if official_crop.tobytes() != localized_pixels.tobytes():
                    issues.append({
                        "locale": locale,
                        "asset": relative_path,
                        "problem": "official_pixel_mismatch",
                        "source": str(source_path.relative_to(ROOT)),
                        "rect": [u, v, width, height],
                    })
    return issues


def add_ref(refs, key, path, detail):
    if is_translation_key(key):
        refs[key].add(f"{path}:{detail}")


def collect_java_refs(refs):
    for path in JAVA_DIR.rglob("*.java"):
        rel = path.relative_to(ROOT)
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in JAVA_TRANSLATABLE_RE.finditer(text):
            add_ref(refs, unescape_java_string(match.group(1)), rel, "translatable")
        for match in TEXT_JSON_TRANSLATE_RE.finditer(text):
            add_ref(refs, match.group(1), rel, "text-json")
        for match in STATIC_STRING_RE.finditer(text):
            value = unescape_java_string(match.group(1))
            if value.startswith(KEY_PREFIXES):
                add_ref(refs, value, rel, "string")


def walk_json_strings(node, callback, path=()):
    if isinstance(node, dict):
        for key, value in node.items():
            walk_json_strings(value, callback, path + (str(key),))
    elif isinstance(node, list):
        for index, value in enumerate(node):
            walk_json_strings(value, callback, path + (str(index),))
    elif isinstance(node, str):
        callback(node, path)


def flatten_json_strings(node, path=(), result=None):
    if result is None:
        result = {}
    if isinstance(node, dict):
        for key, value in node.items():
            flatten_json_strings(value, path + (str(key),), result)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            flatten_json_strings(value, path + (str(index),), result)
    elif isinstance(node, str):
        result[path] = node
    return result


def build_official_value_map(locale_suffix, include_unchanged=False):
    candidates = defaultdict(set)
    for localized_path in CONTENT_DIR.rglob(f"*.{locale_suffix}.json"):
        english_name = localized_path.name[:-len(f".{locale_suffix}.json")] + ".json"
        english_path = localized_path.with_name(english_name)
        if not english_path.exists():
            continue
        try:
            english_values = flatten_json_strings(load_json(english_path))
            localized_values = flatten_json_strings(load_json(localized_path))
        except json.JSONDecodeError:
            continue
        for json_path, english_value in english_values.items():
            localized_value = localized_values.get(json_path)
            if (isinstance(localized_value, str)
                    and (include_unchanged or localized_value != english_value)):
                candidates[english_value].add(localized_value)
    return {
        english_value: next(iter(localized_values))
        for english_value, localized_values in candidates.items()
        if len(localized_values) == 1
    }


def collect_data_refs(refs):
    for path in runtime_json_paths():
        rel = path.relative_to(ROOT)
        try:
            root = load_json(path)
        except json.JSONDecodeError:
            continue

        def visit(value, json_path):
            key_name = json_path[-1] if json_path else ""
            if key_name == "translate":
                add_ref(refs, value, rel, ".".join(json_path))
            elif is_translation_key(value):
                add_ref(refs, value, rel, ".".join(json_path))

        walk_json_strings(root, visit)


def runtime_json_paths():
    yield from DATA_DIR.rglob("*.json")
    yield from (path for path in RUNTIME_ASSET_JSON if path.exists())


def collect_registry_refs(refs):
    blocks = ROOT / "src/main/java/com/stardew/craft/block/ModBlocks.java"
    items = ROOT / "src/main/java/com/stardew/craft/item/ModItems.java"
    register_re = re.compile(r'\bregister\(\s*"([a-z0-9_./-]+)"\s*,')
    if blocks.exists():
        for name in register_re.findall(blocks.read_text(encoding="utf-8", errors="ignore")):
            add_ref(refs, f"block.stardewcraft.{name}", blocks.relative_to(ROOT), "registry")
    if items.exists():
        for name in register_re.findall(items.read_text(encoding="utf-8", errors="ignore")):
            add_ref(refs, f"item.stardewcraft.{name}", items.relative_to(ROOT), "registry")


def source_json_pair(base):
    en = CONTENT_DIR / f"{base}.json"
    zh = CONTENT_DIR / f"{base}.zh-CN.json"
    if en.exists() and zh.exists():
        return load_json(en), load_json(zh)
    return None, None


def build_string_sources():
    cache = {}
    for base, _key in set(STRING_KEY_MAP.values()):
        cache[base] = source_json_pair(base)
    cache["Strings/Objects"] = source_json_pair("Strings/Objects")
    return cache


def vanilla_dialogue_file(npc_id):
    dialogue_dir = CONTENT_DIR / "Characters/Dialogue"
    if not dialogue_dir.exists():
        return None
    wanted = npc_id.lower()
    for path in dialogue_dir.glob("*.json"):
        stem = path.stem
        if stem.endswith(".zh-CN"):
            continue
        if stem.lower() == wanted:
            return stem
    return None


def localized_source_path(base, locale):
    if locale == "en_us":
        return CONTENT_DIR / f"{base}.json"
    suffix = OFFICIAL_LOCALE_SUFFIXES[locale]
    return CONTENT_DIR / f"{base}.{suffix}.json"


def last_sentence(value, locale):
    value = value.strip()
    if locale in {"zh_cn", "ja_jp"}:
        parts = [part for part in re.findall(r"[^。！？]+[。！？]?", value) if part]
    else:
        parts = [part for part in re.split(r"(?<=[.!?])\s+", value) if part]
    return parts[-1] if parts else value


def build_keyed_official_values(locale):
    """Build values whose exact Stardew source key is known for a mod lang key."""
    values = {}
    cache = {}

    def source(base):
        if base not in cache:
            path = localized_source_path(base, locale)
            cache[base] = load_json(path) if path.exists() else {}
        return cache[base]

    def add(lang_key, base, source_key, transform=None):
        data = source(base)
        if source_key in data and isinstance(data[source_key], str):
            value = data[source_key]
            # The official French Abigail asset leaves Sun_old in English.
            # Keep the key source-of-truth binding while supplying the missing
            # localized line instead of forcing the mod back to English.
            if (locale, base, source_key) == (
                    "fr_fr", "Characters/Dialogue/Abigail", "Sun_old"):
                value = ("$p 17#Tu penses qu’il ne se passerait rien, c’est ça ?$u|"
                         "Peut-être qu’un esprit maléfique apparaîtrait !")
            values[lang_key] = transform(value) if transform else value

    for lang_key, (base, source_key) in STRING_KEY_MAP.items():
        add(lang_key, base, source_key)

    for item_id, source_key in ITEM_DESCRIPTION_KEYS.items():
        add(
            f"item.stardewcraft.{item_id}.flavor",
            "Strings/Objects",
            source_key,
            lambda value: last_sentence(value, locale),
        )

    dialogue_dir = DATA_DIR / "npc/dialogue"
    if dialogue_dir.exists():
        for data_path in dialogue_dir.glob("*.json"):
            data = load_json(data_path)
            npc_id = data.get("npc_id", data_path.stem)
            source_stem = vanilla_dialogue_file(npc_id)
            if not source_stem:
                continue
            base = f"Characters/Dialogue/{source_stem}"
            for source_key, lang_key in data.get("entries", {}).items():
                if isinstance(lang_key, str):
                    add(lang_key, base, source_key)

    mail = source("Data/mail")
    for source_key, official_value in mail.items():
        if isinstance(official_value, str):
            values[f"stardewcraft.mail.{source_key}"] = official_value

    # The mod renders the vanilla secret-note text itself.  The vanilla values
    # append %revealtaste directives which StardewCraft handles separately, so
    # keep the official prose while removing only those source directives.
    secret_notes = source("Data/SecretNotes")
    for source_key, official_value in secret_notes.items():
        lang_key = f"stardewcraft.secret_note.{source_key}"
        if isinstance(official_value, str) and not official_value.startswith("!image"):
            values[lang_key] = re.sub(r"%revealtaste:[^%]+", "", official_value)

    # Data/Quests stores slash-delimited fields.  StardewCraft exposes the
    # vanilla title, description, and objective as independent translation
    # keys, so bind those fields by quest id rather than by English text.
    quests = source("Data/Quests")
    quest_fields = {"title": 1, "description": 2, "objective": 3}
    for quest_id, encoded in quests.items():
        if not isinstance(encoded, str):
            continue
        fields = encoded.split("/")
        for field_name, index in quest_fields.items():
            if index < len(fields):
                values[f"stardewcraft.quest.{quest_id}.{field_name}"] = fields[index]

    # Festival scripts contain user-visible dialogue embedded in commands.  The
    # mod key suffix is the lowercase vanilla source key, so bind every exposed
    # dialogue entry by key instead of hoping the whole script matches by value.
    festival_ids = ("spring13", "spring24", "summer11", "summer28", "fall16", "fall27", "winter8", "winter25")
    festival_metadata = {"name", "conditions", "set-up", "mainEvent", "set-up_y2", "mainEvent_y2"}
    korean_winter8_y2 = {
        "Impressive, that's a lot of caught fish!$h": "대단하군요, 정말 많은 물고기를 잡았어요!$h",
        "*gag*... I will never get used to that stench...$s": "*욱*... 이 비린내에는 도저히 익숙해지지 않네요...$s",
        "Now, for the winner of this year's ice fishing competition...": "그럼, 올해 얼음낚시 대회의 우승자를 발표하겠습니다...",
        "Here's your prize! Enjoy.": "여기 상품입니다! 마음껏 즐기세요.",
        "Here's your prize, Willy. Enjoy.": "여기 상품입니다, 윌리. 마음껏 즐기세요.",
        "Well, that's it for this year's Festival of Ice. Thanks for coming, everyone!#$b#Now let's release these poor fish...$s": "자, 올해 얼음 축제는 이것으로 끝입니다. 모두 와 주셔서 고맙습니다!#$b#이제 이 불쌍한 물고기들을 놓아줍시다...$s",
        "I can't believe I won! Well, time to head home.": "내가 우승했다니 믿기지 않아! 이제 집에 갈 시간이군.",
        "I didn't win the competition, but it was still fun! Time to head home.": "대회에서 이기지는 못했지만 그래도 즐거웠어! 이제 집에 갈 시간이군.",
    }
    korean_spring13_y2 = {
        "Wow, look at all these eggs!$h#$b#Now if only I could get you kids to pick up litter this efficiently, we'd have the cleanest town this side of the Gem Sea! *chuckle*$h": "와, 이 달걀들을 좀 보세요!$h#$b#여러분이 이렇게 능숙하게 쓰레기도 주워 준다면, 보석해 이쪽에서 가장 깨끗한 마을이 될 텐데요! *웃음*$h",
        "And now, the winner of this year's egg hunt...": "그럼, 올해 달걀 찾기 대회의 우승자는...",
        "Here's your prize! Enjoy.": "여기 상품입니다! 마음껏 즐기세요.",
        "Well, that's it for this year's Egg Festival. Thanks for coming, everyone!": "자, 올해 달걀 축제는 이것으로 끝입니다. 모두 와 주셔서 고맙습니다!",
    }
    untranslated_plain_fallbacks = {
        ("zh_cn", "By combining their collected eggs... Jas and Vincent!$1"): "把收集到的彩蛋合起来……贾斯和文森特！$1",
        ("zh_cn", "You say it's raining up above?#$e#Rain... It's almost mythical to us. Some of us live our entire lives without ever experiencing it."): "你说上面正在下雨？#$e#雨……对我们来说几乎只存在于神话中。我们中有些人一辈子都从未亲身经历过。",
        ("zh_cn", "I heard it's raining back home. Is that why you came here?$h#$e#I kind of miss the rain, actually...$s"): "我听说家乡正在下雨。你就是为了这个才来这里的吗？$h#$e#说实话，我还真有点想念雨……$s",
        ("es_es", "Need any last minute gifts? I've got you covered! Take a look at my wares.$0"): "¿Necesitas algún regalo de última hora? ¡Yo me encargo! Échale un vistazo a mis productos.$0",
        ("es_es", "This spot is for the overflow presents. There's just too many to count this year!$0"): "Aquí ponemos los regalos que ya no caben. ¡Este año hay tantos que es imposible contarlos!$0",
    }
    for festival_id in festival_ids:
        festival = source(f"Data/Festivals/{festival_id}")
        for source_key, official_value in festival.items():
            if source_key in festival_metadata or not isinstance(official_value, str):
                continue
            official_value = untranslated_plain_fallbacks.get((locale, official_value), official_value)
            if locale == "ko_kr" and festival_id == "winter8" and source_key in {"afterIceFishing_y2", "DickWin_y2"}:
                for english, korean in korean_winter8_y2.items():
                    official_value = official_value.replace(f'"{english}"', f'"{korean}"')
            if locale == "ko_kr" and festival_id == "spring13" and source_key == "afterEggHunt_y2":
                for english, korean in korean_spring13_y2.items():
                    official_value = official_value.replace(f'"{english}"', f'"{korean}"')
            values[f"stardewcraft.festival.{festival_id}.dialogue.{source_key.lower()}"] = official_value

    gift_tastes = source("Data/NPCGiftTastes")
    taste_indexes = {
        "loved": 0,
        "liked": 2,
        "disliked": 4,
        "hated": 6,
        "neutral": 8,
    }
    for npc_name, encoded in gift_tastes.items():
        if not isinstance(encoded, str):
            continue
        fields = encoded.split("/")
        npc_id = npc_name.lower()
        for taste, index in taste_indexes.items():
            if index < len(fields) and fields[index]:
                values[f"stardewcraft.npc.{npc_id}.gift_taste.{taste}"] = fields[index]

    return values


def collect_autofill_values():
    autofill = {}
    sources = {}

    string_sources = build_string_sources()
    for lang_key, (base, source_key) in STRING_KEY_MAP.items():
        en, zh = string_sources.get(base, (None, None))
        if en and zh and source_key in en and source_key in zh:
            autofill[lang_key] = (en[source_key], zh[source_key])
            sources[lang_key] = f"Content/{base}: {source_key}"

    objects_en, objects_zh = string_sources.get("Strings/Objects", (None, None))
    if objects_en and objects_zh:
        for block_id, source_key in OBJECT_NAME_KEYS_BY_BLOCK.items():
            if source_key in objects_en and source_key in objects_zh:
                lang_key = f"block.stardewcraft.{block_id}"
                autofill[lang_key] = (f"{objects_en[source_key]} Crop", f"{objects_zh[source_key]}作物")
                sources[lang_key] = f"Content/Strings/Objects: {source_key} + crop block suffix"

        for block_id, source_key in GIANT_CROP_OBJECT_KEYS.items():
            if source_key in objects_en and source_key in objects_zh:
                lang_key = f"block.stardewcraft.{block_id}"
                autofill[lang_key] = (f"Giant {objects_en[source_key]}", f"巨型{objects_zh[source_key]}")
                sources[lang_key] = f"Content/Strings/Objects: {source_key} + giant crop prefix"

    extra_dialogue_en, extra_dialogue_zh = source_json_pair("Data/ExtraDialogue")

    dialogue_dir = DATA_DIR / "npc/dialogue"
    if dialogue_dir.exists():
        for path in dialogue_dir.glob("*.json"):
            data = load_json(path)
            npc_id = data.get("npc_id", path.stem)
            entries = data.get("entries", {})
            source_stem = vanilla_dialogue_file(npc_id)
            if source_stem:
                en_path = CONTENT_DIR / f"Characters/Dialogue/{source_stem}.json"
                zh_path = CONTENT_DIR / f"Characters/Dialogue/{source_stem}.zh-CN.json"
                if en_path.exists() and zh_path.exists():
                    en = load_json(en_path)
                    zh = load_json(zh_path)
                    for source_key, lang_key in entries.items():
                        if not isinstance(lang_key, str):
                            continue
                        if source_key in en and source_key in zh:
                            autofill[lang_key] = (en[source_key], zh[source_key])
                            sources[lang_key] = f"Content/Characters/Dialogue/{source_stem}: {source_key}"

            if extra_dialogue_en and extra_dialogue_zh:
                for source_key, lang_key in entries.items():
                    if not isinstance(lang_key, str):
                        continue
                    if lang_key in autofill:
                        continue
                    if source_key in extra_dialogue_en and source_key in extra_dialogue_zh:
                        autofill[lang_key] = (extra_dialogue_en[source_key], extra_dialogue_zh[source_key])
                        sources[lang_key] = f"Content/Data/ExtraDialogue: {source_key}"

    return autofill, sources


def collect_misaligned_official_reuse(languages):
    findings = {locale: [] for locale in OFFICIAL_LOCALE_SUFFIXES}
    en_lang = languages["en_us"]

    dialogue_dir = DATA_DIR / "npc/dialogue"
    if dialogue_dir.exists():
        for data_path in dialogue_dir.glob("*.json"):
            data = load_json(data_path)
            npc_id = data.get("npc_id", data_path.stem)
            source_stem = vanilla_dialogue_file(npc_id)
            if not source_stem:
                continue
            english_path = CONTENT_DIR / f"Characters/Dialogue/{source_stem}.json"
            english_source = load_json(english_path)
            localized_sources = {}
            for locale, suffix in OFFICIAL_LOCALE_SUFFIXES.items():
                localized_path = CONTENT_DIR / f"Characters/Dialogue/{source_stem}.{suffix}.json"
                localized_sources[locale] = load_json(localized_path) if localized_path.exists() else {}
            for source_key, lang_key in data.get("entries", {}).items():
                if not isinstance(lang_key, str) or lang_key not in en_lang or source_key not in english_source:
                    continue
                if en_lang[lang_key] == english_source[source_key]:
                    continue
                for locale, localized_source in localized_sources.items():
                    if source_key in localized_source and languages[locale].get(lang_key) == localized_source[source_key]:
                        findings[locale].append({
                            "key": lang_key,
                            "source": f"Content/Characters/Dialogue/{source_stem}: {source_key}",
                        })

    english_mail, _ = source_json_pair("Data/mail")
    if english_mail:
        localized_mail = {}
        for locale, suffix in OFFICIAL_LOCALE_SUFFIXES.items():
            path = CONTENT_DIR / f"Data/mail.{suffix}.json"
            localized_mail[locale] = load_json(path) if path.exists() else {}
        for source_key, english_value in english_mail.items():
            lang_key = f"stardewcraft.mail.{source_key}"
            if lang_key not in en_lang or en_lang[lang_key] == english_value:
                continue
            for locale, localized_source in localized_mail.items():
                if source_key in localized_source and languages[locale].get(lang_key) == localized_source[source_key]:
                    findings[locale].append({
                        "key": lang_key,
                        "source": f"Content/Data/mail: {source_key}",
                    })

    return findings


def main():
    parser = argparse.ArgumentParser(description="Audit StardewCraft translation keys.")
    parser.add_argument("--fix", action="store_true", help="write missing auto-fillable keys to lang JSON files")
    parser.add_argument("--strict", action="store_true", help="fail on missing keys, lang drift, placeholders, duplicates, or hardcoded runtime Chinese")
    parser.add_argument("--report", default=".tmp/missing_translations.json", help="report path")
    args = parser.parse_args()

    languages = {}
    duplicates = {}
    missing_locale_files = []
    for locale in SUPPORTED_LOCALES:
        path = LANG_DIR / f"{locale}.json"
        if not path.exists():
            languages[locale] = OrderedDict()
            duplicates[locale] = []
            missing_locale_files.append(locale)
            continue
        languages[locale], duplicates[locale] = load_json_with_duplicates(path)

    en_lang = languages["en_us"]
    zh_lang = languages["zh_cn"]

    refs = defaultdict(set)
    collect_java_refs(refs)
    collect_data_refs(refs)
    collect_registry_refs(refs)

    autofill, autofill_sources = collect_autofill_values()
    all_keys = sorted(refs)
    effective_keys = {locale: set(lang) for locale, lang in languages.items()}

    # BlockItem display names resolve through the block description id.  If the
    # matching block key exists, the item.* registry id is already covered.
    for key in all_keys:
        prefix = "item.stardewcraft."
        if not key.startswith(prefix):
            continue
        block_key = "block.stardewcraft." + key[len(prefix):]
        for locale, lang in languages.items():
            if block_key in lang:
                effective_keys[locale].add(key)

    missing_by_locale = {
        locale: [key for key in all_keys if key not in effective_keys[locale]]
        for locale in SUPPORTED_LOCALES
    }
    missing_en = missing_by_locale["en_us"]
    missing_zh = missing_by_locale["zh_cn"]
    missing_any = sorted(set(missing_en) | set(missing_zh))
    auto_keys = [key for key in missing_any if key in autofill]
    unresolved = [key for key in missing_any if key not in autofill]
    baseline_keys = set(en_lang)
    official_value_maps = {
        locale: build_official_value_map(suffix, include_unchanged=True)
        for locale, suffix in OFFICIAL_LOCALE_SUFFIXES.items()
    }
    keyed_official_values = {
        locale: build_keyed_official_values(locale)
        for locale in OFFICIAL_LOCALE_SUFFIXES
    }
    keyed_official_values["en_us"] = build_keyed_official_values("en_us")
    key_drift = {
        locale: {
            "missing_from_locale": sorted(baseline_keys - set(lang)),
            "extra_in_locale": sorted(set(lang) - baseline_keys),
        }
        for locale, lang in languages.items()
        if locale != "en_us"
    }
    only_en = key_drift["zh_cn"]["missing_from_locale"]
    only_zh = key_drift["zh_cn"]["extra_in_locale"]
    placeholder_mismatches_by_locale = {}
    for locale, lang in languages.items():
        if locale == "en_us":
            continue
        placeholder_mismatches_by_locale[locale] = [
            {
                "key": key,
                "en": placeholder_signature(en_lang[key]),
                "locale": placeholder_signature(lang[key]),
            }
            for key in sorted(baseline_keys & set(lang))
            if isinstance(en_lang[key], str)
            and isinstance(lang[key], str)
            and placeholder_signature(en_lang[key]) != placeholder_signature(lang[key])
            and official_value_maps.get(locale, {}).get(en_lang[key]) != lang[key]
            and keyed_official_values.get(locale, {}).get(key) != lang[key]
        ]
    placeholder_mismatches = placeholder_mismatches_by_locale["zh_cn"]
    stardew_control_mismatches_by_locale = {}
    for locale, lang in languages.items():
        if locale == "en_us":
            continue
        stardew_control_mismatches_by_locale[locale] = [
            {
                "key": key,
                "en": stardew_control_signature(en_lang[key]),
                "locale": stardew_control_signature(lang[key]),
            }
            for key in sorted(baseline_keys & set(lang))
            if isinstance(en_lang[key], str)
            and isinstance(lang[key], str)
            and stardew_control_signature(en_lang[key]) != stardew_control_signature(lang[key])
            and official_value_maps.get(locale, {}).get(en_lang[key]) != lang[key]
            and keyed_official_values.get(locale, {}).get(key) != lang[key]
        ]
    english_han_values = [
        {"key": key, "value": value}
        for key, value in en_lang.items()
        if isinstance(value, str) and HAN_RE.search(value)
    ]
    han_values_by_locale = {
        locale: [
            {"key": key, "value": value}
            for key, value in lang.items()
            if isinstance(value, str) and HAN_RE.search(value)
        ]
        for locale, lang in languages.items()
        if locale != "en_us" and locale not in HAN_ALLOWED_LOCALES
    }
    non_string_values_by_locale = {
        locale: [key for key, value in lang.items() if not isinstance(value, str)]
        for locale, lang in languages.items()
    }
    leaked_translation_markers_by_locale = {
        locale: [
            {"key": key, "value": value}
            for key, value in lang.items()
            if isinstance(value, str) and PRIVATE_USE_RE.search(value)
        ]
        for locale, lang in languages.items()
    }
    pathological_repetitions_by_locale = {
        locale: [
            {"key": key, "value": value}
            for key, value in lang.items()
            if isinstance(value, str) and PATHOLOGICAL_REPEAT_RE.search(value)
        ]
        for locale, lang in languages.items()
    }
    unbalanced_brackets_by_locale = {
        locale: [
            {"key": key, "value": value}
            for key, value in lang.items()
            if isinstance(value, str)
            and isinstance(en_lang.get(key), str)
            and keyed_official_values.get(locale, {}).get(key) != value
            and official_value_maps.get(locale, {}).get(en_lang[key]) != value
            and en_lang[key].count("(") == en_lang[key].count(")")
            and en_lang[key].count("[") == en_lang[key].count("]")
            and (value.count("(") != value.count(")") or value.count("[") != value.count("]"))
        ]
        for locale, lang in languages.items()
        if locale != "en_us"
    }
    unchanged_from_en_by_locale = {
        locale: [
            key for key in sorted(baseline_keys & set(lang))
            if isinstance(en_lang[key], str) and lang[key] == en_lang[key]
        ]
        for locale, lang in languages.items()
        if locale != "en_us"
    }
    official_source_matches = {}
    official_source_mismatches = {}
    for locale, suffix in OFFICIAL_LOCALE_SUFFIXES.items():
        official_values = build_official_value_map(suffix)
        lang = languages[locale]
        matches = [
            key for key, english_value in en_lang.items()
            if isinstance(english_value, str) and english_value in official_values
            and not key.startswith(OFFICIAL_VALUE_REUSE_SKIP_PREFIXES)
        ]
        official_source_matches[locale] = len(matches)
        official_source_mismatches[locale] = [
            {
                "key": key,
                "current": lang.get(key),
                "official": official_values[en_lang[key]],
            }
            for key in matches
            if lang.get(key) != official_values[en_lang[key]]
            and keyed_official_values.get(locale, {}).get(key) != lang.get(key)
        ]
    misaligned_official_reuse_by_locale = collect_misaligned_official_reuse(languages)
    keyed_official_source_mismatches = {
        locale: [
            {"key": key, "current": languages[locale].get(key), "official": value}
            for key, value in official_values.items()
            if key in languages[locale] and languages[locale][key] != value
        ]
        for locale, official_values in keyed_official_values.items()
    }
    known_translation_pollution_by_locale = {
        locale: [
            {"key": key, "value": value, "pattern": pattern}
            for key, value in languages[locale].items()
            if isinstance(value, str)
            for pattern in patterns
            if pattern in value
        ]
        for locale, patterns in KNOWN_TRANSLATION_POLLUTION.items()
    }
    embedded_unchanged_english_by_locale = {}
    for locale, lang in languages.items():
        if locale == "en_us":
            continue
        findings = []
        for key in sorted(baseline_keys & set(lang)):
            english_value = en_lang[key]
            localized_value = lang[key]
            if not isinstance(english_value, str) or not isinstance(localized_value, str):
                continue
            english_quoted = set(QUOTED_TEXT_RE.findall(english_value))
            for segment in QUOTED_TEXT_RE.findall(localized_value):
                if (segment in english_quoted
                        and len(ENGLISH_PROSE_WORD_RE.findall(segment)) >= 3):
                    findings.append({"key": key, "segment": segment})
        embedded_unchanged_english_by_locale[locale] = findings
    severe_truncations_by_locale = {
        locale: [
            {
                "key": key,
                "english_length": len(en_lang[key]),
                "locale_length": len(lang[key]),
            }
            for key in sorted(baseline_keys & set(lang))
            if isinstance(en_lang[key], str)
            and isinstance(lang[key], str)
            and len(en_lang[key]) >= 80
            and len(lang[key]) < max(20, len(en_lang[key]) * 0.25)
            and keyed_official_values.get(locale, {}).get(key) != lang[key]
            and official_value_maps.get(locale, {}).get(en_lang[key]) != lang[key]
        ]
        for locale, lang in languages.items()
        if locale not in {"en_us", "zh_cn", "ja_jp", "ko_kr"}
    }
    localized_gui_asset_issues = collect_localized_gui_asset_issues()
    hardcoded_java_han = collect_hardcoded_java_han()
    hardcoded_data_han = collect_hardcoded_data_han()

    if args.fix:
        for key in auto_keys:
            en_value, zh_value = autofill[key]
            if key not in en_lang:
                en_lang[key] = en_value
            if key not in zh_lang:
                zh_lang[key] = zh_value
        write_json(LANG_DIR / "en_us.json", en_lang)
        write_json(LANG_DIR / "zh_cn.json", zh_lang)

    report = {
        "referenced_key_count": len(all_keys),
        "missing_en_count": len(missing_en),
        "missing_zh_count": len(missing_zh),
        "supported_locales": list(SUPPORTED_LOCALES),
        "missing_locale_files": missing_locale_files,
        "missing_by_locale": {
            locale: {"count": len(keys), "keys": keys}
            for locale, keys in missing_by_locale.items()
        },
        "key_drift": key_drift,
        "duplicates_by_locale": duplicates,
        "placeholder_mismatches_by_locale": placeholder_mismatches_by_locale,
        "stardew_control_mismatches_by_locale": stardew_control_mismatches_by_locale,
        "han_values_by_locale": han_values_by_locale,
        "non_string_values_by_locale": non_string_values_by_locale,
        "leaked_translation_markers_by_locale": leaked_translation_markers_by_locale,
        "pathological_repetitions_by_locale": pathological_repetitions_by_locale,
        "unbalanced_brackets_by_locale": unbalanced_brackets_by_locale,
        "unchanged_from_en_by_locale": unchanged_from_en_by_locale,
        "official_source_matches": official_source_matches,
        "official_source_mismatches": official_source_mismatches,
        "keyed_official_source_mismatches": keyed_official_source_mismatches,
        "misaligned_official_reuse_by_locale": misaligned_official_reuse_by_locale,
        "known_translation_pollution_by_locale": known_translation_pollution_by_locale,
        "embedded_unchanged_english_by_locale": embedded_unchanged_english_by_locale,
        "severe_truncations_by_locale": severe_truncations_by_locale,
        "localized_gui_asset_issues": localized_gui_asset_issues,
        "auto_fillable_count": len(auto_keys),
        "unresolved_count": len(unresolved),
        "only_en": only_en,
        "only_zh": only_zh,
        "duplicate_en": duplicates["en_us"],
        "duplicate_zh": duplicates["zh_cn"],
        "placeholder_mismatches": placeholder_mismatches,
        "english_han_values": english_han_values,
        "hardcoded_java_han": hardcoded_java_han,
        "hardcoded_data_han": hardcoded_data_han,
        "auto_fillable": [
            {
                "key": key,
                "source": autofill_sources.get(key, ""),
                "refs": sorted(refs[key]),
            }
            for key in auto_keys
        ],
        "unresolved": [
            {
                "key": key,
                "refs": sorted(refs[key]),
            }
            for key in unresolved
        ],
    }

    report_path = ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    write_json(report_path, report)

    print(f"Referenced keys: {len(all_keys)}")
    for locale in SUPPORTED_LOCALES:
        print(f"Missing {locale}: {len(missing_by_locale[locale])}")
    print(f"Auto-fillable: {len(auto_keys)}")
    print(f"Unresolved: {len(unresolved)}")
    print("Lang key drift: " + ", ".join(
        f"{locale}=missing:{len(drift['missing_from_locale'])}/extra:{len(drift['extra_in_locale'])}"
        for locale, drift in key_drift.items()
    ))
    print("Duplicate lang keys: " + ", ".join(
        f"{locale}={len(keys)}" for locale, keys in duplicates.items()
    ))
    print("Placeholder mismatches: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in placeholder_mismatches_by_locale.items()
    ))
    print("Stardew control mismatches: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in stardew_control_mismatches_by_locale.items()
    ))
    print(f"Chinese values in en_us: {len(english_han_values)}")
    print("Chinese values in non-Chinese locales: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in han_values_by_locale.items()
    ))
    print("Non-string lang values: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in non_string_values_by_locale.items()
    ))
    print("Leaked translation markers: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in leaked_translation_markers_by_locale.items()
    ))
    print("Pathological repetitions: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in pathological_repetitions_by_locale.items()
    ))
    print("Unbalanced brackets: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in unbalanced_brackets_by_locale.items()
    ))
    print("Values unchanged from en_us: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in unchanged_from_en_by_locale.items()
    ))
    print("Official source coverage: " + ", ".join(
        f"{locale}={count}" for locale, count in official_source_matches.items()
    ))
    print("Official source mismatches: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in official_source_mismatches.items()
    ))
    print("Keyed official source mismatches: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in keyed_official_source_mismatches.items()
    ))
    print("Misaligned official reuse: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in misaligned_official_reuse_by_locale.items()
    ))
    print("Known translation pollution: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in known_translation_pollution_by_locale.items()
    ))
    print("Embedded unchanged English prose: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in embedded_unchanged_english_by_locale.items()
    ))
    print("Severe translation truncations: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in severe_truncations_by_locale.items()
    ))
    print(f"Localized GUI asset issues: {len(localized_gui_asset_issues)}")
    print(f"Hardcoded runtime Chinese: java={len(hardcoded_java_han)}, data={len(hardcoded_data_han)}")
    print(f"Report: {report_path.relative_to(ROOT)}")
    if args.fix:
        print(f"Wrote {len(auto_keys)} auto-fillable keys to lang files.")
    strict_failures = (
        missing_locale_files
        or any(missing_by_locale.values())
        or any(drift["missing_from_locale"] or drift["extra_in_locale"] for drift in key_drift.values())
        or any(duplicates.values())
        or any(placeholder_mismatches_by_locale.values())
        or any(stardew_control_mismatches_by_locale.values())
        or english_han_values
        or any(han_values_by_locale.values())
        or any(non_string_values_by_locale.values())
        or any(leaked_translation_markers_by_locale.values())
        or any(pathological_repetitions_by_locale.values())
        or any(unbalanced_brackets_by_locale.values())
        or any(official_source_mismatches.values())
        or any(keyed_official_source_mismatches.values())
        or any(misaligned_official_reuse_by_locale.values())
        or any(known_translation_pollution_by_locale.values())
        or any(embedded_unchanged_english_by_locale.values())
        or any(severe_truncations_by_locale.values())
        or localized_gui_asset_issues
        or hardcoded_java_han
        or hardcoded_data_han
    )
    if args.strict and strict_failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
