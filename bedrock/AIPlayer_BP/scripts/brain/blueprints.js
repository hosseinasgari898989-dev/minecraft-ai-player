function computeMaterials(blocks) {
  const counts = {};
  for (const b of blocks) {
    if (!b.optional && b.blockId !== "air") counts[b.blockId] = (counts[b.blockId] || 0) + 1;
  }
  return counts;
}

function buildHouseVariant(id, displayName, width, height, depth, primaryWall, frameBlock, includeWindows) {
  const blocks = [];
  const halfW = Math.floor(width / 2), halfD = Math.floor(depth / 2), roofY = height - 1;
  for (let dz = -halfD; dz <= halfD; dz++) {
    for (let dx = -halfW; dx <= halfW; dx++) {
      const border = Math.abs(dx) === halfW || Math.abs(dz) === halfD;
      blocks.push({ dx, dy: 0, dz, blockId: border ? frameBlock : primaryWall, optional: false });
    }
  }
  for (let dy = 1; dy < roofY; dy++) {
    for (let dz = -halfD; dz <= halfD; dz++) {
      for (let dx = -halfW; dx <= halfW; dx++) {
        const border = Math.abs(dx) === halfW || Math.abs(dz) === halfD;
        if (!border) continue;
        if (dz === -halfD && dx === 0 && (dy === 1 || dy === 2)) { blocks.push({ dx, dy, dz, blockId: "air", optional: false }); continue; }
        if (Math.abs(dx) === halfW && Math.abs(dz) === halfD) { blocks.push({ dx, dy, dz, blockId: frameBlock, optional: false }); continue; }
        if (includeWindows && dy === 2 && (dx === 0 || dz === 0)) { blocks.push({ dx, dy, dz, blockId: "glass_pane", optional: true }); continue; }
        blocks.push({ dx, dy, dz, blockId: primaryWall, optional: false });
      }
    }
  }
  blocks.push({ dx: 0, dy: 1, dz: halfD - 1, blockId: "torch", optional: true });
  for (let dz = -halfD; dz <= halfD; dz++) for (let dx = -halfW; dx <= halfW; dx++) {
    const border = Math.abs(dx) === halfW || Math.abs(dz) === halfD;
    blocks.push({ dx, dy: roofY, dz, blockId: border ? frameBlock : primaryWall, optional: false });
  }
  blocks.sort((a, b) => a.dy - b.dy);
  return { id, displayName, width, height, depth, blocks, requiredMaterials: computeMaterials(blocks) };
}

function buildSmallFarm() {
  const blocks = [];
  for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) {
    const border = Math.abs(dx) === 2 || Math.abs(dz) === 2;
    if (border) blocks.push({ dx, dy: 0, dz, blockId: "oak_planks", optional: false });
    else if (dx === 0 && dz === 0) blocks.push({ dx: 0, dy: 0, dz: 0, blockId: "water", optional: true });
    else { blocks.push({ dx, dy: 0, dz, blockId: "farmland", optional: false }); blocks.push({ dx, dy: 1, dz, blockId: "wheat_crop", optional: true }); }
  }
  blocks.push({ dx: 2, dy: 1, dz: 2, blockId: "torch", optional: true });
  blocks.sort((a, b) => a.dy - b.dy);
  return { id: "small_farm", displayName: "Irrigated Wheat Farm", width: 5, height: 2, depth: 5, blocks, requiredMaterials: computeMaterials(blocks) };
}

function buildWatchtower() {
  const blocks = [];
  for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) blocks.push({ dx, dy: 0, dz, blockId: "cobblestone", optional: false });
  for (let dy = 1; dy <= 5; dy++) for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
    if (Math.abs(dx) !== 1 && Math.abs(dz) !== 1) continue;
    if (dz === -1 && dx === 0 && (dy === 1 || dy === 2)) blocks.push({ dx, dy, dz, blockId: "air", optional: false });
    else blocks.push({ dx, dy, dz, blockId: "cobblestone", optional: false });
  }
  for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) blocks.push({ dx, dy: 6, dz, blockId: "oak_planks", optional: false });
  blocks.push({ dx: 0, dy: 7, dz: 0, blockId: "torch", optional: true });
  blocks.sort((a, b) => a.dy - b.dy);
  return { id: "watchtower", displayName: "Guard Watchtower", width: 5, height: 8, depth: 5, blocks, requiredMaterials: computeMaterials(blocks) };
}

export const BLUEPRINTS = {
  shelter: buildHouseVariant("shelter", "Simple Survival Shelter", 5, 4, 5, "oak_planks", "oak_log", false),
  compact_wood: buildHouseVariant("compact_wood", "Compact Wooden House", 7, 5, 7, "oak_planks", "oak_log", true),
  modern_house: buildHouseVariant("modern_house", "Modern Stylish House", 7, 5, 7, "cobblestone", "oak_planks", true),
  decorative_house: buildHouseVariant("decorative_house", "Decorative Timber House", 9, 6, 9, "oak_planks", "cobblestone", true),
  small_farm: buildSmallFarm(),
  watchtower: buildWatchtower()
};

export function getBlueprint(styleId) { return BLUEPRINTS[styleId] || BLUEPRINTS.compact_wood; }
