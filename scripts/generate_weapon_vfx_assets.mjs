import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

const outputDir = path.resolve(
  "src/main/resources/assets/stardewcraft/textures/particle",
);
fs.mkdirSync(outputDir, { recursive: true });
for (let frame = 0; frame < 4; frame += 1) {
  fs.rmSync(path.join(outputDir, `forest_pulse_${frame}.png`), { force: true });
}

const crcTable = new Uint32Array(256);
for (let n = 0; n < 256; n += 1) {
  let value = n;
  for (let bit = 0; bit < 8; bit += 1) {
    value = (value & 1) !== 0 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  }
  crcTable[n] = value >>> 0;
}

function crc32(buffer) {
  let value = 0xffffffff;
  for (const byte of buffer) {
    value = crcTable[(value ^ byte) & 0xff] ^ (value >>> 8);
  }
  return (value ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type, "ascii");
  const payload = Buffer.concat([typeBuffer, data]);
  const output = Buffer.alloc(12 + data.length);
  output.writeUInt32BE(data.length, 0);
  typeBuffer.copy(output, 4);
  data.copy(output, 8);
  output.writeUInt32BE(crc32(payload), 8 + data.length);
  return output;
}

function createImage(width, height) {
  return {
    width,
    height,
    pixels: new Uint8Array(width * height * 4),
  };
}

function setPixel(image, x, y, red, green, blue, alpha) {
  if (x < 0 || y < 0 || x >= image.width || y >= image.height) {
    return;
  }
  const offset = (Math.floor(y) * image.width + Math.floor(x)) * 4;
  image.pixels[offset] = Math.max(0, Math.min(255, Math.round(red)));
  image.pixels[offset + 1] = Math.max(0, Math.min(255, Math.round(green)));
  image.pixels[offset + 2] = Math.max(0, Math.min(255, Math.round(blue)));
  image.pixels[offset + 3] = Math.max(0, Math.min(255, Math.round(alpha)));
}

function writePng(fileName, image) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(image.width, 0);
  header.writeUInt32BE(image.height, 4);
  header[8] = 8;
  header[9] = 6;

  const rows = Buffer.alloc((image.width * 4 + 1) * image.height);
  for (let y = 0; y < image.height; y += 1) {
    const rowOffset = y * (image.width * 4 + 1);
    rows[rowOffset] = 0;
    Buffer.from(
      image.pixels.buffer,
      image.pixels.byteOffset + y * image.width * 4,
      image.width * 4,
    ).copy(rows, rowOffset + 1);
  }

  const png = Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", header),
    chunk("IDAT", zlib.deflateSync(rows, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
  fs.writeFileSync(path.join(outputDir, fileName), png);
}

function mix(from, to, amount) {
  return from + (to - from) * amount;
}

function makeTrail(fileName, darkColor, edgeColor, segmented) {
  const image = createImage(64, 16);
  for (let y = 0; y < image.height; y += 1) {
    for (let x = 0; x < image.width; x += 1) {
      const along = x / (image.width - 1);
      const fromTip = y / (image.height - 1);
      const tailFade = Math.max(0, Math.min(1, (along - 0.03) / 0.42));
      const bladeFalloff = Math.pow(1 - fromTip, 1.65);
      const edgeBand = y <= 2 ? 1 : y <= 4 ? 0.52 : 0;
      const dashIndex = Math.floor(x / 7);
      const dashFactor = segmented && dashIndex % 2 === 0 ? 0.42 : 1;
      const breakup = ((x * 5 + y * 3) % 17) === 0 ? 0.58 : 1;
      const alpha = (
        34 * bladeFalloff
        + 170 * edgeBand
      ) * tailFade * dashFactor * breakup;
      const edgeMix = Math.max(edgeBand, bladeFalloff * 0.32);
      setPixel(
        image,
        x,
        y,
        mix(darkColor[0], edgeColor[0], edgeMix),
        mix(darkColor[1], edgeColor[1], edgeMix),
        mix(darkColor[2], edgeColor[2], edgeMix),
        alpha,
      );
    }
  }
  writePng(fileName, image);
}

function drawLine(image, x0, y0, x1, y1, width, color) {
  const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
  for (let step = 0; step <= steps; step += 1) {
    const amount = step / Math.max(1, steps);
    const x = Math.round(mix(x0, x1, amount));
    const y = Math.round(mix(y0, y1, amount));
    for (let oy = -width; oy <= width; oy += 1) {
      for (let ox = -width; ox <= width; ox += 1) {
        if (Math.abs(ox) + Math.abs(oy) <= width) {
          setPixel(image, x + ox, y + oy, ...color);
        }
      }
    }
  }
}

function makeImpactFrame(frame) {
  const image = createImage(32, 32);
  const retract = frame * 2;
  const alpha = 230 - frame * 42;
  drawLine(
    image,
    4 + retract,
    18 - Math.floor(retract / 3),
    27 - retract,
    14 + Math.floor(retract / 3),
    1,
    [190, 130, 54, Math.round(alpha * 0.58)],
  );
  drawLine(
    image,
    6 + retract,
    18 - Math.floor(retract / 3),
    26 - retract,
    14 + Math.floor(retract / 3),
    0,
    [255, 241, 194, alpha],
  );
  const sparkAlpha = Math.max(0, 190 - frame * 52);
  setPixel(image, 25 - frame, 11 + frame, 232, 178, 76, sparkAlpha);
  setPixel(image, 28 - frame, 16 + frame, 255, 225, 143, sparkAlpha);
  setPixel(image, 8 + frame, 21 - frame, 214, 155, 63, sparkAlpha);
  return image;
}

function makeLeafFrame(frame) {
  const image = createImage(16, 16);
  const centerX = 7.5;
  const centerY = 7.5;
  const angle = -0.65 + frame * 0.48;
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  for (let y = 1; y < 15; y += 1) {
    for (let x = 1; x < 15; x += 1) {
      const dx = x - centerX;
      const dy = y - centerY;
      const localX = dx * cos + dy * sin;
      const localY = -dx * sin + dy * cos;
      const leaf = (localX * localX) / 26 + (localY * localY) / 4.2;
      if (leaf <= 1 && Math.abs(localY) <= 2.25 - Math.abs(localX) * 0.16) {
        const vein = Math.abs(localY) < 0.45;
        const tipShade = localX > 1.5 ? 1 : 0;
        setPixel(
          image,
          x,
          y,
          vein ? 205 : 84 + tipShade * 18,
          vein ? 190 : 156 + tipShade * 20,
          vein ? 80 : 76,
          vein ? 235 : 220,
        );
      }
    }
  }
  return image;
}

function makeWispFrame(frame) {
  const image = createImage(16, 16);
  const centerX = 7 + (frame % 2);
  const centerY = 9 - Math.floor(frame / 2);
  const fade = 235 - frame * 28;
  setPixel(image, centerX, centerY, 231, 214, 116, fade);
  setPixel(image, centerX, centerY - 1, 190, 205, 104, fade);
  setPixel(image, centerX - 1, centerY, 98, 155, 77, fade * 0.82);
  setPixel(image, centerX + 1, centerY, 98, 155, 77, fade * 0.82);
  setPixel(image, centerX, centerY + 1, 63, 112, 62, fade * 0.62);
  setPixel(image, centerX - 2, centerY - 1, 128, 174, 84, fade * 0.28);
  setPixel(image, centerX + 2, centerY + 1, 128, 174, 84, fade * 0.24);
  setPixel(image, centerX + (frame % 3) - 1, centerY - 3, 184, 201, 101, fade * 0.42);
  return image;
}

makeTrail(
  "crescent_blade_trail.png",
  [118, 76, 38],
  [255, 241, 194],
  false,
);
makeTrail(
  "forest_blade_trail.png",
  [36, 77, 50],
  [190, 207, 109],
  true,
);

for (let frame = 0; frame < 4; frame += 1) {
  writePng(`crescent_impact_${frame}.png`, makeImpactFrame(frame));
  writePng(`forest_leaf_${frame}.png`, makeLeafFrame(frame));
  writePng(`forest_wisp_${frame}.png`, makeWispFrame(frame));
}
