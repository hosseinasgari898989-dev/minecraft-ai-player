/**
 * Hierarchical priority goal manager & dependency planner for Bedrock Edition.
 * Mirrors Java GoalManager.java semantics identically.
 */
import { getBlueprint } from "./blueprints.js";

export const PRIORITY = { EMERGENCY_SURVIVAL: 100, OWNER_PROTECTION: 85, RETURN_OWNER: 75, ASSIGNED_TASK: 60, AUTONOMOUS_SURVIVAL: 40, IDLE: 15 };
let taskCounter = 1;

export class GoalManager {
  constructor() { this.activeTask = null; this.suspendedTask = null; this.lastFailure = "NONE"; this.lastOutcomeMessage = "Idle and ready."; this.lastSwitchTick = -100; }

  assignIntent(intent, worldSnapshot, pvpAllowed = false, targetApproved = false) {
    const lang = intent.language || "en";
    if (intent.intent === "CANCEL_TASK" || intent.intent === "STOP") {
      this.activeTask = null;
      if (intent.intent === "CANCEL_TASK") this.suspendedTask = null;
      this.lastFailure = "NONE";
      this.lastOutcomeMessage = lang === "fa" ? "تسک فعلی متوقف و لغو شد." : "Stopped and cleared the current task.";
      return null;
    }
    if (intent.intent === "RESUME_TASK") {
      if (this.suspendedTask) { this.activeTask = this.suspendedTask; this.suspendedTask = null; this.activeTask.status = "ACTIVE"; this.lastOutcomeMessage = "Resumed task: " + this.activeTask.summary; return this.activeTask; }
      this.lastOutcomeMessage = "No suspended task to resume."; return null;
    }
    if (intent.intent === "ATTACK_PLAYER" && (!pvpAllowed || (!intent.pvpExplicitlyAuthorized && !targetApproved))) {
      this.lastFailure = "PVP_NOT_AUTHORIZED"; this.lastOutcomeMessage = "Player combat is disabled or explicit PvP authorization was not granted.";
      return { id: `task-${taskCounter++}`, intent: intent.intent, priority: PRIORITY.ASSIGNED_TASK, status: "FAILED", failureReason: "PVP_NOT_AUTHORIZED", summary: `Engage player ${intent.targetPlayer}`, subtasks: [] };
    }

    const steps = [], requiredResources = {};
    let summary = intent.intent, priority = PRIORITY.ASSIGNED_TASK;
    const countItem = (id) => (worldSnapshot.itemCounts && worldSnapshot.itemCounts[id]) || 0;
    const addToolDeps = (res) => {
      if ((res === "stone" || res === "coal" || res === "iron") && !worldSnapshot.hasWoodenPickaxeOrBetter) {
        const haveWoodEquiv = countItem("wood") + Math.floor(countItem("oak_planks") / 4);
        if (haveWoodEquiv < 3) steps.push({ skillId: "HARVEST_WOOD", parameter: "wood", targetCount: 3 - haveWoodEquiv, completedCount: 0, status: "PENDING" });
        steps.push({ skillId: "CRAFT_PLANKS", parameter: "oak_planks", targetCount: 8, completedCount: 0, status: "PENDING" });
        steps.push({ skillId: "CRAFT_STICKS", parameter: "stick", targetCount: 4, completedCount: 0, status: "PENDING" });
        steps.push({ skillId: "CRAFT_PICKAXE", parameter: "wooden_pickaxe", targetCount: 1, completedCount: 0, status: "PENDING" });
      }
      if (res === "iron" && !worldSnapshot.hasStonePickaxeOrBetter) {
        const missingStone = Math.max(0, 3 - countItem("stone"));
        if (missingStone > 0) steps.push({ skillId: "MINE_STONE", parameter: "stone", targetCount: missingStone, completedCount: 0, status: "PENDING" });
        steps.push({ skillId: "CRAFT_STICKS", parameter: "stick", targetCount: 4, completedCount: 0, status: "PENDING" });
        steps.push({ skillId: "CRAFT_PICKAXE", parameter: "stone_pickaxe", targetCount: 1, completedCount: 0, status: "PENDING" });
      }
    };

    switch (intent.intent) {
      case "COME_HERE": case "RETURN_OWNER":
        priority = PRIORITY.RETURN_OWNER; summary = "Return to owner"; steps.push({ skillId: "COME_HERE", parameter: "owner", targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "FOLLOW_OWNER": case "STAY_BESIDE":
        summary = "Follow owner"; steps.push({ skillId: "FOLLOW_OWNER", parameter: "owner", targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "HOLD_POSITION":
        summary = "Hold current position"; steps.push({ skillId: "STAY_POSITION", parameter: "current", targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "PROTECT_OWNER": case "PROTECT_NIGHT":
        priority = PRIORITY.OWNER_PROTECTION; summary = "Protect owner"; steps.push({ skillId: "PROTECT_OWNER", parameter: intent.intent, targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "GUARD_AREA": case "PATROL_AREA":
        priority = PRIORITY.OWNER_PROTECTION; summary = "Guard area"; steps.push({ skillId: "GUARD_AREA", parameter: intent.intent, targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "GATHER_RESOURCE": {
        const res = intent.resource || "wood", requested = intent.quantity || 8, have = countItem(res), missing = Math.max(0, requested - have);
        requiredResources[res] = requested; summary = `Collect ${requested} ${res} (currently have ${have})`;
        if (missing === 0) { const done = { id: `task-${taskCounter++}`, intent: intent.intent, priority, status: "COMPLETED", failureReason: "NONE", summary, requiredResources, subtasks: [] }; this.activeTask = done; this.lastOutcomeMessage = `Already have ${have} ${res} in inventory.`; return done; }
        if (worldSnapshot.inventoryUsedSlots >= worldSnapshot.inventoryTotalSlots) { this.lastFailure = "INVENTORY_FULL"; return { id: `task-${taskCounter++}`, intent: intent.intent, priority, status: "FAILED", failureReason: "INVENTORY_FULL", summary, requiredResources, subtasks: [] }; }
        addToolDeps(res);
        const skillMap = { wood: "HARVEST_WOOD", stone: "MINE_STONE", coal: "MINE_COAL", iron: "MINE_IRON" };
        steps.push({ skillId: skillMap[res] || "HARVEST_WOOD", parameter: res, targetCount: missing, completedCount: 0, status: "PENDING" }); break;
      }
      case "BUILD_STRUCTURE": {
        const bp = getBlueprint(intent.structureStyle); summary = `Construct ${bp.displayName}`; Object.assign(requiredResources, bp.requiredMaterials);
        const planksNeeded = bp.requiredMaterials.oak_planks || 0, logsDirect = bp.requiredMaterials.oak_log || 0, cobbleNeeded = bp.requiredMaterials.cobblestone || 0;
        const missingPlanks = Math.max(0, planksNeeded - countItem("oak_planks")), logsForPlanks = Math.ceil(missingPlanks / 4), missingLogs = Math.max(0, logsDirect + logsForPlanks - countItem("wood"));
        if (missingLogs > 0) steps.push({ skillId: "HARVEST_WOOD", parameter: "wood", targetCount: missingLogs, completedCount: 0, status: "PENDING" });
        if (missingPlanks > 0) steps.push({ skillId: "CRAFT_PLANKS", parameter: "oak_planks", targetCount: missingPlanks, completedCount: 0, status: "PENDING" });
        const missingCobble = Math.max(0, cobbleNeeded - countItem("stone"));
        if (missingCobble > 0) { addToolDeps("stone"); steps.push({ skillId: "MINE_STONE", parameter: "stone", targetCount: missingCobble, completedCount: 0, status: "PENDING" }); }
        steps.push({ skillId: "SELECT_BUILD_SITE", parameter: bp.id, targetCount: 1, completedCount: 0, status: "PENDING" });
        steps.push({ skillId: "BUILD_BLUEPRINT", parameter: bp.id, targetCount: bp.blocks.length, completedCount: 0, status: "PENDING" }); break;
      }
      case "UPGRADE_TOOLS": summary = "Upgrade survival toolset"; addToolDeps("iron"); steps.push({ skillId: "CRAFT_SWORD_AXE", parameter: "stone_gear", targetCount: 2, completedCount: 0, status: "PENDING" }); break;
      case "ORGANIZE_INVENTORY": summary = "Sort inventory stacks"; steps.push({ skillId: "ORGANIZE_INVENTORY", parameter: "all", targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "FIND_FOOD": summary = "Gather food"; steps.push({ skillId: "FIND_FOOD", parameter: "food", targetCount: intent.quantity || 6, completedCount: 0, status: "PENDING" }); break;
      case "PLANT_CROPS": summary = "Plant wheat crops"; steps.push({ skillId: "PLANT_WHEAT", parameter: "wheat", targetCount: intent.quantity || 9, completedCount: 0, status: "PENDING" }); break;
      case "SCAN_THREATS": summary = "Scan nearby threats"; steps.push({ skillId: "SCAN_THREATS", parameter: "area", targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "FIND_PLAYER": case "FOLLOW_PLAYER": summary = `${intent.intent === "FIND_PLAYER" ? "Locate" : "Follow"} player ${intent.targetPlayer}`; steps.push({ skillId: "LOCATE_PLAYER", parameter: intent.targetPlayer, targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      case "ATTACK_PLAYER": summary = `Engage authorized PvP target ${intent.targetPlayer}`; steps.push({ skillId: "ENGAGE_PVP", parameter: intent.targetPlayer, targetCount: 1, completedCount: 0, status: "PENDING" }); break;
      default: summary = "Explore nearby area"; steps.push({ skillId: "FOLLOW_OWNER", parameter: "explore", targetCount: 1, completedCount: 0, status: "PENDING" });
    }
    const created = { id: `task-${taskCounter++}`, intent: intent.intent, priority, language: lang, status: "ACTIVE", failureReason: "NONE", summary, requiredResources, subtasks: steps, retryCount: 0, maxRetries: 4 };
    this.activeTask = created; this.lastFailure = "NONE"; this.lastOutcomeMessage = `Accepted task: ${summary}`; return created;
  }

  evaluatePriorities(worldSnapshot) {
    const foodCount = (worldSnapshot.itemCounts && worldSnapshot.itemCounts.food) || 0;
    const emergency = worldSnapshot.inLavaOrFire || (worldSnapshot.health <= 8 && foodCount > 0);
    const urgentThreat = worldSnapshot.nearbyHostileCount > 0 && worldSnapshot.nearestHostileDistance <= 10.0;
    if (emergency) {
      if (this.activeTask && this.activeTask.priority < PRIORITY.EMERGENCY_SURVIVAL) {
        this.suspendedTask = this.activeTask; this.suspendedTask.status = "SUSPENDED";
        this.activeTask = { id: `task-${taskCounter++}`, intent: "FIND_FOOD", priority: PRIORITY.EMERGENCY_SURVIVAL, status: "ACTIVE", summary: "Emergency survival recovery", subtasks: [{ skillId: foodCount > 0 ? "EAT_FOOD" : "COME_HERE", parameter: "emergency", targetCount: 1, completedCount: 0, status: "ACTIVE" }] };
        this.lastSwitchTick = worldSnapshot.worldTick;
      }
      return;
    }
    if (urgentThreat && worldSnapshot.worldTick - this.lastSwitchTick >= 20) {
      if (this.activeTask && this.activeTask.priority < PRIORITY.OWNER_PROTECTION) {
        this.suspendedTask = this.activeTask; this.suspendedTask.status = "SUSPENDED";
        this.activeTask = { id: `task-${taskCounter++}`, intent: "PROTECT_OWNER", priority: PRIORITY.OWNER_PROTECTION, status: "ACTIVE", summary: `Defend against ${worldSnapshot.nearestHostileName}`, subtasks: [{ skillId: "PROTECT_OWNER", parameter: "emergency_defense", targetCount: 1, completedCount: 0, status: "ACTIVE" }] };
        this.lastSwitchTick = worldSnapshot.worldTick;
      }
      return;
    }
    if (this.activeTask && (this.activeTask.priority === PRIORITY.EMERGENCY_SURVIVAL || (this.activeTask.priority === PRIORITY.OWNER_PROTECTION && this.activeTask.subtasks[0]?.parameter === "emergency_defense")) && !emergency && !urgentThreat) this.activeTask.status = "COMPLETED";
    if (this.activeTask && this.activeTask.status === "COMPLETED" && this.suspendedTask) { this.activeTask = this.suspendedTask; this.suspendedTask = null; this.activeTask.status = "ACTIVE"; this.lastSwitchTick = worldSnapshot.worldTick; }
  }
}
