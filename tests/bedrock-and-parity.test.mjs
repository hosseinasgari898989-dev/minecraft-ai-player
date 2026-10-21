import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { interpretCommand } from "../bedrock/AIPlayer_BP/scripts/brain/commandInterpreter.js";
import { GoalManager, PRIORITY } from "../bedrock/AIPlayer_BP/scripts/brain/goalPlanner.js";
import { BLUEPRINTS } from "../bedrock/AIPlayer_BP/scripts/brain/blueprints.js";
import { findLocalPath } from "../bedrock/AIPlayer_BP/scripts/brain/pathfinder.js";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

test("Bedrock manifests, dependency UUIDs, entity IDs, model and texture are valid", () => {
  const bp = JSON.parse(fs.readFileSync(path.join(ROOT, "bedrock/AIPlayer_BP/manifest.json"), "utf8"));
  const rp = JSON.parse(fs.readFileSync(path.join(ROOT, "bedrock/AIPlayer_RP/manifest.json"), "utf8"));
  assert.equal(bp.format_version, 2);
  assert.equal(rp.format_version, 2);
  assert.ok(bp.dependencies.some((d) => d.uuid === rp.header.uuid));
  assert.ok(rp.dependencies.some((d) => d.uuid === bp.header.uuid));
  assert.ok(bp.dependencies.some((d) => d.module_name === "@minecraft/server" && !d.version.includes("beta")));
  const serverEntity = JSON.parse(fs.readFileSync(path.join(ROOT, "bedrock/AIPlayer_BP/entities/ai_player.json"), "utf8"));
  const clientEntity = JSON.parse(fs.readFileSync(path.join(ROOT, "bedrock/AIPlayer_RP/entity/ai_player.entity.json"), "utf8"));
  assert.equal(serverEntity["minecraft:entity"].description.identifier, "ai_player:companion");
  assert.equal(clientEntity["minecraft:client_entity"].description.identifier, "ai_player:companion");
  assert.ok(fs.existsSync(path.join(ROOT, "bedrock/AIPlayer_BP/scripts/main.js")));
  assert.ok(fs.existsSync(path.join(ROOT, "bedrock/AIPlayer_RP/textures/entity/ai_player.png")));
});

test("Persian and English natural-language commands produce the same resource goals", () => {
  for (const input of ["Collect 16 pieces of wood.", "۱۶ تا چوب جمع کن."]) {
    const parsed = interpretCommand(input);
    assert.equal(parsed.intent, "GATHER_RESOURCE");
    assert.equal(parsed.resource, "wood");
    assert.equal(parsed.quantity, 16);
  }
  assert.equal(interpretCommand("دنبالم بیا").intent, "FOLLOW_OWNER");
  assert.equal(interpretCommand("برام یک خونه شیک بساز").structureStyle, "modern_house");
  assert.equal(interpretCommand("وایسا").intent, "STOP");
});

test("Goal planner verifies deficits, prerequisites, interruptions and PvP lock", () => {
  const gm = new GoalManager();
  const worldState = { itemCounts: { wood: 5 }, inventoryUsedSlots: 2, inventoryTotalSlots: 27, hasWoodenPickaxeOrBetter: false, hasStonePickaxeOrBetter: false };
  const task = gm.assignIntent(interpretCommand("۱۶ تا چوب جمع کن"), worldState);
  assert.equal(task.subtasks[0].targetCount, 11);
  const house = gm.assignIntent(interpretCommand("Build me a nice house"), { ...worldState, itemCounts: {} });
  assert.ok(house.subtasks.some((s) => s.skillId === "BUILD_BLUEPRINT"));
  gm.evaluatePriorities({ health: 20, itemCounts: {}, nearbyHostileCount: 1, nearestHostileDistance: 3.5, worldTick: 120 });
  assert.equal(gm.activeTask.priority, PRIORITY.OWNER_PROTECTION);
  assert.equal(gm.suspendedTask.summary, "Construct Modern Stylish House");
  const denied = gm.assignIntent(interpretCommand("Attack player Rival42"), worldState, true, false);
  assert.equal(denied.failureReason, "PVP_NOT_AUTHORIZED");
  assert.equal(Object.keys(BLUEPRINTS).length, 6);
});

test("Bounded 3D A* routes around walls and the executor has no hosted-AI calls", () => {
  const wall = new Set();
  for (let z = -3; z <= 3; z++) { wall.add(`3,64,${z}`); wall.add(`3,65,${z}`); }
  const grid = {
    isPassableBody: (x, y, z) => y >= 64 && !wall.has(`${x},${y},${z}`),
    isSolidGround: (_x, y, _z) => y === 63,
    isHazard: (x, y, z) => x === 1 && y === 63 && z === 0,
    isWater: () => false
  };
  const route = findLocalPath(grid, { x: 0, y: 64, z: 0 }, { x: 6, y: 64, z: 0 }, 1.5, 512);
  assert.equal(route.reachable, true);
  assert.equal(route.waypoints.some((p) => p.x === 1 && p.z === 0), false);
  const code = fs.readFileSync(path.join(ROOT, "bedrock/AIPlayer_BP/scripts/main.js"), "utf8").toLowerCase();
  assert.equal(code.includes("fetch("), false);
  assert.equal(code.includes("openai"), false);
});
