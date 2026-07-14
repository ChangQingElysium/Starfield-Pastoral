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
SUPPORTED_LOCALES = ("en_us", "zh_cn", "ru_ru", "fr_fr")
OFFICIAL_LOCALE_SUFFIXES = {"ru_ru": "ru-RU", "fr_fr": "fr-FR"}
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
    "stardewcraft.secret_woods_open",
}

SKIP_BLOCK_SUFFIXES = (
    # Internal helper blocks rendered by block entities.
    "_top_render",
)

STATIC_STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
JAVA_TRANSLATABLE_RE = re.compile(r'(?:Component|TextComponent)?\.?translatable\(\s*"((?:[^"\\]|\\.)*)"')
TEXT_JSON_TRANSLATE_RE = re.compile(r'\\"translate\\":\\"([^"\\]+)\\"')
HAN_RE = re.compile(r"[\u3400-\u9fff]")
PLACEHOLDER_RE = re.compile(r"%(?:(\d+)\$)?([sdf])(?![A-Za-z]{2})")
STARDEW_CONTROL_RE = re.compile(
    r"%(?:noturn|farm|fork|pet|revealtaste|spouse|time|secretsanta|noun|season|firstnameletter|name)"
    r"(?![A-Za-z])"
)
JAVA_FORMAT_RE = re.compile(r"%(?:\d+\$)?[sdf]")
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
    return sorted(STARDEW_CONTROL_RE.findall(JAVA_FORMAT_RE.sub("", value)))


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


def build_official_value_map(locale_suffix):
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
            if isinstance(localized_value, str) and localized_value != english_value:
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
        if locale not in ("en_us", "zh_cn")
    }
    non_string_values_by_locale = {
        locale: [key for key, value in lang.items() if not isinstance(value, str)]
        for locale, lang in languages.items()
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
        ]
    misaligned_official_reuse_by_locale = collect_misaligned_official_reuse(languages)
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
        "unchanged_from_en_by_locale": unchanged_from_en_by_locale,
        "official_source_matches": official_source_matches,
        "official_source_mismatches": official_source_mismatches,
        "misaligned_official_reuse_by_locale": misaligned_official_reuse_by_locale,
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
    print("Values unchanged from en_us: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in unchanged_from_en_by_locale.items()
    ))
    print("Official source coverage: " + ", ".join(
        f"{locale}={count}" for locale, count in official_source_matches.items()
    ))
    print("Official source mismatches: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in official_source_mismatches.items()
    ))
    print("Misaligned official reuse: " + ", ".join(
        f"{locale}={len(items)}" for locale, items in misaligned_official_reuse_by_locale.items()
    ))
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
        or any(official_source_mismatches.values())
        or any(misaligned_official_reuse_by_locale.values())
        or hardcoded_java_han
        or hardcoded_data_han
    )
    if args.strict and strict_failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
