/**
 * AI Player — Bedrock runtime.
 * The local planner is deterministic and never calls a hosted AI API.
 */
import { world, system, ItemStack } from "@minecraft/server";
import { interpretCommand } from "./brain/commandInterpreter.js";
import { GoalManager } from "./brain/goalPlanner.js";
import { getBlueprint } from "./brain/blueprints.js";
import { findLocalPath } from "./brain/pathfinder.js";

const ENTITY_ID = "ai_player:companion";
const brains = new Map();
const routes = new Map();
const resourceTargetCache = new Map();
const HOSTILES = ["monster", "undead", "arthropod", "illager"];

function brainFor(entity) {
  if (!brains.has(entity.id)) brains.set(entity.id, new GoalManager());
  return brains.get(entity.id);
}
function ownerFor(entity) {
  const id = entity.getDynamicProperty("aiplayer:owner_id");
  return world.getAllPlayers().find((p) => p.id === id) ?? null;
}
function findCompanion(player) {
  const matches = [];
  for (const name of ["overworld", "nether", "the_end"]) {
    try {
      for (const entity of world.getDimension(name).getEntities({ type: ENTITY_ID })) {
        if (entity.getDynamicProperty("aiplayer:owner_id") === player.id) matches.push(entity);
      }
    } catch { /* dimension may have no loaded chunks */ }
  }
  for (let i = 1; i < matches.length; i++) matches[i].remove();
  return matches[0] ?? null;
}
function spawnCompanion(player) {
  const current = findCompanion(player);
  if (current) return current;
  const entity = player.dimension.spawnEntity(ENTITY_ID, {
    x: player.location.x + 2, y: player.location.y, z: player.location.z + 2
  });
  entity.nameTag = "AI Player";
  entity.setDynamicProperty("aiplayer:owner_id", player.id);
  entity.setDynamicProperty("aiplayer:owner_name", player.name);
  entity.setDynamicProperty("aiplayer:mode", "COMPANION");
  entity.setDynamicProperty("aiplayer:home_x", Math.floor(entity.location.x));
  entity.setDynamicProperty("aiplayer:home_y", Math.floor(entity.location.y));
  entity.setDynamicProperty("aiplayer:home_z", Math.floor(entity.location.z));
  player.sendMessage("§a[AI Player]§f همراه هوشمند آماده است؛ می‌توانی فارسی یا انگلیسی فرمان بدهی.");
  return entity;
}
function inventory(entity) {
  return entity.getComponent("minecraft:inventory")?.container ?? null;
}
function itemCounts(entity) {
  const container = inventory(entity);
  const counts = { wood: 0, oak_planks: 0, stone: 0, coal: 0, iron: 0, food: 0, wheat: 0 };
  let used = 0;
  if (container) for (let i = 0; i < container.size; i++) {
    const stack = container.getItem(i);
    if (!stack) continue;
    used++;
    const id = stack.typeId;
    if (id.endsWith("_log")) counts.wood += stack.amount;
    if (id.endsWith("_planks")) counts.oak_planks += stack.amount;
    if (id.endsWith("_stone") || id === "minecraft:stone" || id === "minecraft:cobblestone") counts.stone += stack.amount;
    if (id === "minecraft:coal" || id === "minecraft:charcoal") counts.coal += stack.amount;
    if (id === "minecraft:raw_iron" || id === "minecraft:iron_ingot") counts.iron += stack.amount;
    if (["minecraft:bread", "minecraft:apple", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:carrot", "minecraft:baked_potato"].includes(id)) counts.food += stack.amount;
    if (id === "minecraft:wheat") counts.wheat += stack.amount;
  }
  return { container, counts, used, size: container?.size ?? 27 };
}
function takeItems(container, predicate, amount) {
  if (!container) return false;
  let remaining = amount;
  for (let i = 0; i < container.size && remaining > 0; i++) {
    const stack = container.getItem(i);
    if (!stack || !predicate(stack.typeId)) continue;
    const take = Math.min(stack.amount, remaining);
    remaining -= take;
    if (stack.amount === take) container.setItem(i, undefined);
    else { stack.amount -= take; container.setItem(i, stack); }
  }
  return remaining === 0;
}
function collectDroppedItems(entity, radius = 3.2) {
  const container = inventory(entity);
  if (!container) return;
  for (const item of entity.dimension.getEntities({ type: "minecraft:item", location: entity.location, maxDistance: radius })) {
    const stack = item.getComponent("minecraft:item")?.itemStack;
    if (!stack) continue;
    if (!container.addItem(stack)) item.remove();
  }
}
function moveToward(entity, target, speed = 0.23) {
  const dx = target.x - entity.location.x;
  const dz = target.z - entity.location.z;
  const d = Math.hypot(dx, dz);
  if (d < 0.65) return true;
  const blockAhead = entity.dimension.getBlock({
    x: Math.floor(entity.location.x + dx / d * 0.8),
    y: Math.floor(entity.location.y),
    z: Math.floor(entity.location.z + dz / d * 0.8)
  });
  const vy = blockAhead && !blockAhead.isAir && !blockAhead.isLiquid ? 0.32 : -0.05;
  entity.applyImpulse({ x: dx / d * speed, y: vy, z: dz / d * speed });
  entity.setRotation({ x: 0, y: Math.atan2(-dx, dz) * 180 / Math.PI });
  return false;
}
function nearestBlock(dimension, origin, radius, predicate) {
  let best = null, bestDistance = Infinity;
  const baseX = Math.floor(origin.x), baseY = Math.floor(origin.y), baseZ = Math.floor(origin.z);
  // Keep the scan bounded; search is cached below so it does not run every brain tick.
  for (let dx = -radius; dx <= radius; dx++) {
    for (let dy = -3; dy <= 4; dy++) {
      for (let dz = -radius; dz <= radius; dz++) {
        const pos = { x: baseX + dx, y: baseY + dy, z: baseZ + dz };
        let block;
        try { block = dimension.getBlock(pos); } catch { continue; }
        if (!block || !predicate(block.typeId)) continue;
        const distance = dx * dx + dy * dy + dz * dz;
        if (distance < bestDistance) { best = pos; bestDistance = distance; }
      }
    }
  }
  return best;
}

function cachedNearestBlock(entity, resource, predicate) {
  const cached = resourceTargetCache.get(entity.id);
  if (cached && cached.resource === resource && system.currentTick - cached.tick < 40) {
    if (!cached.pos) return null;
    try {
      const block = entity.dimension.getBlock(cached.pos);
      if (block && predicate(block.typeId)) return cached.pos;
    } catch { /* invalidated by an unloaded/changed target */ }
    resourceTargetCache.delete(entity.id);
  }
  const pos = nearestBlock(entity.dimension, entity.location, 10, predicate);
  resourceTargetCache.set(entity.id, { resource, tick: system.currentTick, pos });
  return pos;
}
function snapshot(entity, owner) {
  const state = itemCounts(entity);
  const hostiles = entity.dimension.getEntities({ location: entity.location, maxDistance: 16, families: HOSTILES });
  let closest = 999, closestName = "none";
  for (const hostile of hostiles) {
    const d = Math.hypot(hostile.location.x - entity.location.x, hostile.location.y - entity.location.y, hostile.location.z - entity.location.z);
    if (d < closest) { closest = d; closestName = hostile.typeId; }
  }
  const health = entity.getComponent("minecraft:health");
  return {
    worldTick: system.currentTick, dimensionId: entity.dimension.id,
    x: Math.floor(entity.location.x), y: Math.floor(entity.location.y), z: Math.floor(entity.location.z),
    health: health?.currentValue ?? 20, maxHealth: health?.effectiveMax ?? 20,
    inLavaOrFire: false, nightTime: world.getTimeOfDay() >= 12542 && world.getTimeOfDay() <= 23459,
    ownerPresent: Boolean(owner), ownerName: owner?.name ?? "",
    ownerDistance: owner ? Math.hypot(owner.location.x - entity.location.x, owner.location.z - entity.location.z) : -1,
    ownerHealth: 20, nearbyHostileCount: hostiles.length, nearestHostileName: closestName,
    nearestHostileDistance: closest, inventoryUsedSlots: state.used, inventoryTotalSlots: state.size,
    itemCounts: state.counts, hasWoodenPickaxeOrBetter: Boolean(state.container && Array.from({ length: state.container.size }, (_, i) => state.container.getItem(i)?.typeId ?? "").some((id) => id.endsWith("_pickaxe"))),
    hasStonePickaxeOrBetter: Boolean(state.container && Array.from({ length: state.container.size }, (_, i) => state.container.getItem(i)?.typeId ?? "").some((id) => ["minecraft:stone_pickaxe", "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe"].includes(id)))
  };
}
function say(player, message) { if (player) player.sendMessage("§b[AI Player]§f " + message); }
function command(player, raw) {
  const entity = spawnCompanion(player);
  const parsed = interpretCommand(raw);
  if (parsed.ambiguous) { say(player, parsed.clarificationPrompt ?? "Please clarify your request."); return; }
  if (parsed.unsupportedReason) { say(player, parsed.unsupportedReason); return; }
  if (parsed.intent === "SET_HOME") {
    entity.setDynamicProperty("aiplayer:home_x", Math.floor(entity.location.x));
    entity.setDynamicProperty("aiplayer:home_y", Math.floor(entity.location.y));
    entity.setDynamicProperty("aiplayer:home_z", Math.floor(entity.location.z));
    say(player, "Home position saved."); return;
  }
  const brain = brainFor(entity);
  const snap = snapshot(entity, player);
  const task = brain.assignIntent(parsed, snap, false, false);
  if (parsed.intent === "STATUS_REPORT") {
    say(player, `Mode=${entity.getDynamicProperty("aiplayer:mode") ?? "COMPANION"} | Task=${brain.activeTask?.summary ?? "None"} | HP=${snap.health}/${snap.maxHealth} | Slots=${snap.inventoryUsedSlots}/${snap.inventoryTotalSlots}`);
    return;
  }
  if (task?.status === "FAILED") { say(player, task.failureReason ?? "Task could not be started."); return; }
  entity.setDynamicProperty("aiplayer:mode", parsed.intent);
  say(player, task?.summary ? "Accepted: " + task.summary : brain.lastOutcomeMessage);
}
function runEntityTick(entity) {
  const owner = ownerFor(entity);
  const brain = brainFor(entity);
  const snap = snapshot(entity, owner);
  brain.evaluatePriorities(snap);
  collectDroppedItems(entity);
  const task = brain.activeTask;
  if (!task || task.status !== "ACTIVE") {
    if (owner && snap.ownerDistance > 4.5 && entity.getDynamicProperty("aiplayer:mode") !== "HOLD_POSITION") moveToward(entity, owner.location);
    return;
  }
  const sub = task.subtasks.find((s) => s.status !== "COMPLETED");
  if (!sub) {
    task.status = "COMPLETED";
    if (owner) say(owner, "Completed: " + task.summary);
    return;
  }
  const inv = inventory(entity);
  switch (sub.skillId) {
    case "COME_HERE":
    case "FOLLOW_OWNER":
      if (owner && snap.ownerDistance > 3.0) moveToward(entity, owner.location);
      else if (sub.skillId === "COME_HERE") sub.status = "COMPLETED";
      break;
    case "PROTECT_OWNER":
    case "GUARD_AREA": {
      const hostiles = entity.dimension.getEntities({ location: entity.location, maxDistance: 16, families: HOSTILES });
      const target = hostiles[0];
      if (target) {
        if (Math.hypot(target.location.x - entity.location.x, target.location.z - entity.location.z) <= 3.1) target.applyDamage(4);
        else moveToward(entity, target.location, 0.25);
      } else if (owner && snap.ownerDistance > 5) moveToward(entity, owner.location);
      break;
    }
    case "HARVEST_WOOD":
    case "MINE_STONE":
    case "MINE_COAL":
    case "MINE_IRON": {
      const resMap = { HARVEST_WOOD: "wood", MINE_STONE: "stone", MINE_COAL: "coal", MINE_IRON: "iron" };
      const resource = resMap[sub.skillId];
      const requested = task.requiredResources?.[resource] ?? sub.targetCount;
      if ((snap.itemCounts[resource] ?? 0) >= requested) { sub.status = "COMPLETED"; break; }
      const matches = (id) => resource === "wood" ? id.endsWith("_log") : resource === "stone" ? ["minecraft:stone", "minecraft:cobblestone"].includes(id) : resource === "coal" ? id.includes("coal_ore") : id.includes("iron_ore");
      const blockPos = cachedNearestBlock(entity, resource, matches);
      if (!blockPos) { task.status = "FAILED"; task.failureReason = "RESOURCE_UNAVAILABLE"; break; }
      if (Math.hypot(blockPos.x + 0.5 - entity.location.x, blockPos.z + 0.5 - entity.location.z) > 3.2) {
        const grid = {
          isPassableBody: (x, y, z) => Boolean(entity.dimension.getBlock({ x, y, z })?.isAir),
          isSolidGround: (x, y, z) => { const b = entity.dimension.getBlock({ x, y, z }); return Boolean(b && !b.isAir && !b.isLiquid); },
          isHazard: (x, y, z) => { const id = entity.dimension.getBlock({ x, y, z })?.typeId ?? ""; return id.includes("lava") || id.includes("fire") || id.includes("cactus"); },
          isWater: (x, y, z) => (entity.dimension.getBlock({ x, y, z })?.typeId ?? "").includes("water")
        };
        const nav = routes.get(entity.id) ?? { waypoints: [], cursor: 0, stuckCount: 0, lastPos: { ...entity.location } };
        routes.set(entity.id, nav);
        if (!nav.waypoints.length) {
          nav.waypoints = findLocalPath(grid, { x: snap.x, y: snap.y, z: snap.z }, blockPos, 2.5, 256).waypoints;
          nav.cursor = 0;
        }
        const wp = nav.waypoints[nav.cursor] ?? blockPos;
        if (moveToward(entity, { x: wp.x + 0.5, y: wp.y, z: wp.z + 0.5 }) && nav.cursor < nav.waypoints.length - 1) nav.cursor++;
      } else {
        try { entity.dimension.runCommand(`setblock ${blockPos.x} ${blockPos.y} ${blockPos.z} air destroy`); } catch (error) { if (owner) say(owner, `Could not mine that block: ${error?.message ?? "command failed"}`); }
        routes.delete(entity.id);
      }
      break;
    }
    case "CRAFT_PLANKS":
      while (itemCounts(entity).counts.oak_planks < sub.targetCount && takeItems(inv, (id) => id.endsWith("_log"), 1)) inv?.addItem(new ItemStack("minecraft:oak_planks", 4));
      sub.status = "COMPLETED"; break;
    case "CRAFT_STICKS":
      if (takeItems(inv, (id) => id.endsWith("_planks"), 2)) inv?.addItem(new ItemStack("minecraft:stick", 4));
      sub.status = "COMPLETED"; break;
    case "CRAFT_PICKAXE": {
      const stone = sub.parameter === "stone_pickaxe";
      const materials = stone ? (id) => id === "minecraft:cobblestone" : (id) => id.endsWith("_planks");
      if (takeItems(inv, materials, 3) && takeItems(inv, (id) => id === "minecraft:stick", 2)) inv?.addItem(new ItemStack(stone ? "minecraft:stone_pickaxe" : "minecraft:wooden_pickaxe", 1));
      sub.status = "COMPLETED"; break;
    }
    case "SELECT_BUILD_SITE":
      entity.setDynamicProperty("aiplayer:build_bp", sub.parameter);
      entity.setDynamicProperty("aiplayer:build_x", Math.floor(entity.location.x) + 5);
      entity.setDynamicProperty("aiplayer:build_y", Math.floor(entity.location.y) - 1);
      entity.setDynamicProperty("aiplayer:build_z", Math.floor(entity.location.z) + 5);
      entity.setDynamicProperty("aiplayer:build_idx", 0);
      sub.status = "COMPLETED"; break;
    case "BUILD_BLUEPRINT": {
      const bp = getBlueprint(sub.parameter);
      const bx = Number(entity.getDynamicProperty("aiplayer:build_x"));
      const by = Number(entity.getDynamicProperty("aiplayer:build_y"));
      const bz = Number(entity.getDynamicProperty("aiplayer:build_z"));
      let index = Number(entity.getDynamicProperty("aiplayer:build_idx") ?? 0);
      for (let n = 0; n < 3 && index < bp.blocks.length; n++) {
        const entry = bp.blocks[index];
        const target = { x: bx + entry.dx, y: by + entry.dy, z: bz + entry.dz };
        if (entry.blockId === "air") { entity.dimension.getBlock(target)?.setType("minecraft:air"); index++; continue; }
        const itemId = entry.blockId === "wheat_crop" ? "minecraft:wheat_seeds" : entry.blockId === "farmland" ? "minecraft:dirt" : "minecraft:" + entry.blockId;
        const paid = entry.optional || takeItems(inv, (id) => {
          if (entry.blockId === "oak_planks") return id.endsWith("_planks");
          if (entry.blockId === "oak_log") return id.endsWith("_log") || id.endsWith("_planks");
          return id === itemId;
        }, 1);
        if (!paid) {
          task.status = "FAILED"; task.failureReason = "RESOURCE_UNAVAILABLE";
          if (owner) say(owner, `Missing ${entry.blockId}; build stopped at ${index}/${bp.blocks.length}.`);
          entity.setDynamicProperty("aiplayer:build_idx", index); return;
        }
        const block = entity.dimension.getBlock(target);
        if (block) block.setType(entry.blockId === "wheat_crop" ? "minecraft:wheat" : itemId);
        index++;
      }
      entity.setDynamicProperty("aiplayer:build_idx", index);
      if (index >= bp.blocks.length) sub.status = "COMPLETED";
      break;
    }
    case "SCAN_THREATS":
      if (owner) say(owner, snap.nearbyHostileCount === 0 ? "Area clear." : `${snap.nearbyHostileCount} hostile(s) nearby; closest ${snap.nearestHostileName}.`);
      sub.status = "COMPLETED"; break;
    default:
      // Avoid false success for unsupported skills; report and stop the task.
      task.status = "FAILED"; task.failureReason = "ACTION_RETRIES_EXCEEDED";
      if (owner) say(owner, "This skill is recognized but its Bedrock executor is not implemented yet.");
      break;
  }
}

system.afterEvents.scriptEventReceive.subscribe((event) => {
  const source = event.sourceEntity?.typeId === "minecraft:player" ? event.sourceEntity : world.getAllPlayers()[0];
  if (!source) return;
  if (event.id === "aiplayer:spawn") spawnCompanion(source);
  else if (event.id === "aiplayer:status") command(source, "status");
  else if (event.id === "aiplayer:dismiss") {
    const current = findCompanion(source);
    if (current) current.remove();
    say(source, "Companion dismissed.");
  } else if (event.id === "aiplayer:cmd") command(source, event.message);
});

if (world.beforeEvents && "chatSend" in world.beforeEvents) {
  world.beforeEvents.chatSend.subscribe((event) => {
    const message = event.message.trim();
    if (message) system.run(() => command(event.sender, message));
  });
}
world.afterEvents.playerInteractWithEntity.subscribe((event) => {
  if (event.target.typeId === ENTITY_ID) system.run(() => command(event.player, "status"));
});

system.runInterval(() => {
  for (const dimName of ["overworld", "nether", "the_end"]) {
    try { for (const entity of world.getDimension(dimName).getEntities({ type: ENTITY_ID })) runEntityTick(entity); }
    catch { /* loaded-dimension-safe */ }
  }
}, 10);
