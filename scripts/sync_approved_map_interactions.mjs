import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const project = process.cwd();
const manifestPaths = [
  "scripts/data/map_interaction_approvals_4_2_to_4_7.json",
  "scripts/data/map_interaction_approvals_phase3.json",
];
const manifests = manifestPaths.map((relativePath) => ({
  relativePath,
  value: JSON.parse(
    fs.readFileSync(path.join(project, relativePath), "utf8"),
  ),
}));
const localeSources = {
  en_us: "",
  zh_cn: ".zh-CN",
  de_de: ".de-DE",
  es_es: ".es-ES",
  fr_fr: ".fr-FR",
  hu_hu: ".hu-HU",
  it_it: ".it-IT",
  ja_jp: ".ja-JP",
  ko_kr: ".ko-KR",
  pt_br: ".pt-BR",
  ru_ru: ".ru-RU",
  tr_tr: ".tr-TR",
};

for (const { relativePath, value: manifest } of manifests) {
  if (
    manifest.format !== 2 ||
    manifest.geometry?.unpaired_points !== "single_block" ||
    manifest.geometry?.paired_points !== "inclusive_cuboid" ||
    JSON.stringify(manifest.geometry?.paired_suffixes) !==
      JSON.stringify(["_1", "_2"]) ||
    !Array.isArray(manifest.batches)
  ) {
    throw new Error(`Unsupported author-approval manifest: ${relativePath}`);
  }
}

const interactions = manifests.flatMap(({ relativePath, value: manifest }) =>
  manifest.batches.flatMap((batch) =>
    batch.interactions.map((interaction) => ({
      manifestPath: relativePath,
      batch,
      interaction,
    })),
  ),
);
const ids = new Set();
const pointNames = new Set();

function compileTriggerGeometry(interaction) {
  const positions = [];
  const pairedEndpoints = new Map();

  for (const point of interaction.points) {
    const match = /^(.*)_([12])$/.exec(point.name);
    if (match == null) {
      positions.push([point.x, point.y, point.z]);
      continue;
    }
    const [, baseName, endpoint] = match;
    if (baseName.length === 0) {
      throw new Error(
        `Interaction ${interaction.id} has an empty paired-point name`,
      );
    }
    const pair = pairedEndpoints.get(baseName) ?? {};
    if (pair[endpoint] != null) {
      throw new Error(
        `Interaction ${interaction.id} repeats endpoint ${point.name}`,
      );
    }
    pair[endpoint] = point;
    pairedEndpoints.set(baseName, pair);
  }

  const boxes = [];
  for (const [baseName, pair] of pairedEndpoints) {
    if (pair["1"] == null || pair["2"] == null) {
      throw new Error(
        `Interaction ${interaction.id} needs both ${baseName}_1 and ${baseName}_2`,
      );
    }
    boxes.push({
      min: [
        Math.min(pair["1"].x, pair["2"].x),
        Math.min(pair["1"].y, pair["2"].y),
        Math.min(pair["1"].z, pair["2"].z),
      ],
      max: [
        Math.max(pair["1"].x, pair["2"].x),
        Math.max(pair["1"].y, pair["2"].y),
        Math.max(pair["1"].z, pair["2"].z),
      ],
    });
  }

  return { positions, boxes };
}

for (const { batch, interaction } of interactions) {
  if (!ids.add(interaction.id)) {
    throw new Error(`Duplicate interaction id: ${interaction.id}`);
  }
  if (!["message", "letter", "npc_message"].includes(interaction.type)) {
    throw new Error(`Unsupported interaction type: ${interaction.type}`);
  }
  if (interaction.type === "npc_message") {
    for (const field of ["npc", "original_action"]) {
      if (typeof interaction[field] !== "string"
          || interaction[field].length === 0) {
        throw new Error(
          `Interaction ${interaction.id} needs ${field}`,
        );
      }
    }
    for (const field of ["nearby", "fallback"]) {
      const text = interaction[field];
      if (
        text == null ||
        typeof text.source !== "string" ||
        typeof text.original_key !== "string" ||
        typeof text.text_key !== "string"
      ) {
        throw new Error(
          `Interaction ${interaction.id} needs a complete ${field} source`,
        );
      }
    }
  } else if (
    typeof interaction.original_key !== "string"
    || typeof interaction.text_key !== "string"
  ) {
    throw new Error(
      `Interaction ${interaction.id} needs original_key and text_key`,
    );
  }
  if (
    interaction.equivalent_original_keys != null
    && !Array.isArray(interaction.equivalent_original_keys)
  ) {
    throw new Error(
      `Interaction ${interaction.id} equivalent_original_keys must be an array`,
    );
  }
  if (!Array.isArray(interaction.points) || interaction.points.length === 0) {
    throw new Error(`Interaction ${interaction.id} has no approved points`);
  }
  for (const point of interaction.points) {
    if (typeof point.name !== "string" || point.name.length === 0) {
      throw new Error(
        `Interaction ${interaction.id} has an invalid point name`,
      );
    }
    const identity = `${batch.section}:${point.name}`;
    if (!pointNames.add(identity)) {
      throw new Error(`Duplicate author point name: ${identity}`);
    }
    for (const axis of ["x", "y", "z"]) {
      if (!Number.isInteger(point[axis])) {
        throw new Error(`${identity}.${axis} must be an integer`);
      }
    }
  }
  compileTriggerGeometry(interaction);
}

const sourceByLocale = Object.fromEntries(
  Object.entries(localeSources).map(([locale, suffix]) => [
    locale,
    JSON.parse(
      fs.readFileSync(
        path.join(
          project,
          `源文件/Content/Strings/StringsFromMaps${suffix}.json`,
        ),
        "utf8",
      ),
    ),
  ]),
);
const contentSourceCache = new Map();

function contentSource(locale, contentPath) {
  const cacheKey = `${locale}:${contentPath}`;
  if (contentSourceCache.has(cacheKey)) {
    return contentSourceCache.get(cacheKey);
  }
  const source = JSON.parse(
    fs.readFileSync(
      path.join(
        project,
        `源文件/Content/${contentPath}${localeSources[locale]}.json`,
      ),
      "utf8",
    ),
  );
  contentSourceCache.set(cacheKey, source);
  return source;
}

function localizedSourceText(locale, source) {
  const catalog = contentSource(locale, source.source);
  const value = catalog[source.original_key];
  if (typeof value !== "string") {
    throw new Error(
      `Missing ${source.source}:${source.original_key}`
        + ` in source locale ${locale}`,
    );
  }
  return value;
}
const uiSourceByLocale = Object.fromEntries(
  Object.entries(localeSources).map(([locale, suffix]) => [
    locale,
    JSON.parse(
      fs.readFileSync(
        path.join(
          project,
          `源文件/Content/Strings/UI${suffix}.json`,
        ),
        "utf8",
      ),
    ),
  ]),
);

function minecraftFormat(value) {
  return value.replaceAll("{0}", "%1$s").replaceAll("{1}", "%2$s");
}

function upsertFlatJsonStrings(file, values) {
  let source = fs.readFileSync(file, "utf8");
  const parsed = JSON.parse(source);
  const missing = [];

  for (const [key, value] of Object.entries(values)) {
    if (typeof value !== "string") {
      throw new Error(`Expected a string for ${key}`);
    }
    const escapedKey = JSON.stringify(key);
    const lines = source.split("\n");
    const lineIndex = lines.findIndex((line) =>
      line.trimStart().startsWith(`${escapedKey}:`),
    );
    if (lineIndex >= 0) {
      const comma = lines[lineIndex].trimEnd().endsWith(",") ? "," : "";
      lines[lineIndex] = `  ${escapedKey}: ${JSON.stringify(value)}${comma}`;
      source = lines.join("\n");
    } else {
      missing.push([key, value]);
    }
  }

  if (missing.length > 0) {
    const closing = source.lastIndexOf("\n}");
    if (closing < 0) {
      throw new Error(`Cannot find closing object in ${file}`);
    }
    let prefix = source.slice(0, closing).trimEnd();
    if (!prefix.endsWith("{") && !prefix.endsWith(",")) {
      prefix += ",";
    }
    const additions = missing
      .map(
        ([key, value], index) =>
          `  ${JSON.stringify(key)}: ${JSON.stringify(value)}${
            index + 1 < missing.length ? "," : ""
          }`,
      )
      .join("\n");
    source = `${prefix}\n${additions}\n}${source.slice(closing + 2)}`;
  }

  const updated = JSON.parse(source);
  for (const [key, value] of Object.entries(values)) {
    if (updated[key] !== value) {
      throw new Error(`Failed to synchronize ${key} in ${file}`);
    }
  }
  if (JSON.stringify(parsed) === JSON.stringify(updated)) {
    return false;
  }
  fs.writeFileSync(file, source);
  return true;
}

let changedLanguages = 0;
for (const locale of Object.keys(localeSources)) {
  const copied = {};
  for (const { interaction } of interactions) {
    if (interaction.type === "npc_message") {
      copied[interaction.nearby.text_key] =
        localizedSourceText(locale, interaction.nearby);
      copied[interaction.fallback.text_key] =
        localizedSourceText(locale, interaction.fallback);
      continue;
    }
    const value = sourceByLocale[locale][interaction.original_key];
    if (typeof value !== "string") {
      throw new Error(
        `Missing ${interaction.original_key} in source locale ${locale}`,
      );
    }
    for (const equivalent of interaction.equivalent_original_keys ?? []) {
      if (sourceByLocale[locale][equivalent] !== value) {
        throw new Error(
          `${interaction.original_key} and ${equivalent}`
            + ` differ in source locale ${locale}`,
        );
      }
    }
    copied[interaction.text_key] = value;
  }
  copied["stardewcraft.strings_ui.chat_caught_snooping"] =
    minecraftFormat(uiSourceByLocale[locale].Chat_Caught_Snooping);
  const languageFile = path.join(
    project,
    `src/main/resources/assets/stardewcraft/lang/${locale}.json`,
  );
  if (upsertFlatJsonStrings(languageFile, copied)) {
    changedLanguages++;
  }
}

const outputDirectory = path.join(
  project,
  "src/main/resources/data/stardewcraft/map_interactions",
);
fs.mkdirSync(outputDirectory, { recursive: true });
let changedDefinitions = 0;

for (const { batch, interaction } of interactions) {
  const english = interaction.type === "npc_message"
    ? null
    : sourceByLocale.en_us[interaction.original_key];
  const location = Object.hasOwn(interaction, "location")
    ? interaction.location
    : batch.location;
  let branch;
  if (interaction.type === "letter") {
    branch = {
      id: "default",
      action: {
        type: "stardewcraft:open_letter",
        data: { text: interaction.text_key },
      },
    };
  } else if (interaction.type === "npc_message") {
    branch = {
      id: "default",
      action: {
        type: "stardewcraft:npc_message",
        data: {
          npc: interaction.npc,
          nearby: {
            translate: interaction.nearby.text_key,
            fallback: localizedSourceText("en_us", interaction.nearby),
          },
          fallback: {
            translate: interaction.fallback.text_key,
            fallback: localizedSourceText("en_us", interaction.fallback),
          },
          radius: 14.0,
          vertical_radius: 4,
          announce_snooping: interaction.announce_snooping === true,
        },
      },
    };
  } else {
    branch = {
      id: "default",
      messages: [
        {
          translate: interaction.text_key,
          fallback: english,
        },
      ],
    };
  }
  const geometry = compileTriggerGeometry(interaction);
  const actionName = interaction.type === "npc_message"
    ? "NPCMessage"
    : interaction.type === "letter"
      ? "Letter"
      : "Message";
  const definition = {
    format: 1,
    priority: 100,
    ...(["letter", "npc_message"].includes(interaction.type)
      ? { hint: "read" }
      : {}),
    source: {
      vanilla_version: "1.6",
      map: batch.map,
      tile_action: interaction.type === "npc_message"
        ? interaction.original_action
        : `${actionName} "${interaction.original_key}"`,
      code: `StardewValley.GameLocation.performAction:${actionName}`,
    },
    trigger: {
      dimension: "stardewcraft:stardew_valley",
      ...(location == null ? {} : { location }),
      ...(geometry.positions.length > 0
        ? { positions: geometry.positions }
        : {}),
      ...(geometry.boxes.length > 0 ? { boxes: geometry.boxes } : {}),
      hand: "main_hand",
    },
    branches: [branch],
  };
  const output = `${JSON.stringify(definition, null, 2)}\n`;
  const outputFile = path.join(outputDirectory, `${interaction.id}.json`);
  const previous = fs.existsSync(outputFile)
    ? fs.readFileSync(outputFile, "utf8")
    : null;
  if (previous !== output) {
    fs.writeFileSync(outputFile, output);
    changedDefinitions++;
  }
}

console.log(
  `Synchronized ${interactions.length} definitions (${changedDefinitions} changed), ` +
    `${Object.keys(localeSources).length} locales (${changedLanguages} changed).`,
);
