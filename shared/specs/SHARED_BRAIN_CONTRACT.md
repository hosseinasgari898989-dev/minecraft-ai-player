# AI Player Shared Brain Contract (v2.0 — 100% Local Deterministic Architecture)

## 1. Zero-LLM Guarantee

Both implementations (`Java/Fabric 1.20.6` and `Bedrock Add-On 1.21+`) run completely offline inside the game process with **zero**:
- Hosted LLM APIs (OpenAI, Gemini, Claude, Kimi, etc.)
- API keys, paid subscriptions, or cloud reasoning services
- Local HTTP model servers (Ollama, llama.cpp, vLLM) or Python sidecars
- Neural weights or external runtime downloads

All perception, natural-language command parsing, task decomposition, dependency resolution, pathfinding, survival management, construction planning, combat threat evaluation, and failure recovery execute locally through deterministic data structures.

## 2. Canonical Priority & Preemption Hierarchy

| Tier | Priority Weight | Category | Preempts Lower Tiers? | Preserves Interrupted Task? |
|------|-----------------|----------|-----------------------|-----------------------------|
| P0 | `100` | `EMERGENCY_SURVIVAL` (critical health <= 6 HP, lava/fire contact, eating food) | Yes | Yes (`suspendedTask` slot) |
| P1 | `85` | `OWNER_PROTECTION` (hostile attacking owner or entering guard perimeter) | Yes | Yes (`suspendedTask` slot) |
| P2 | `75` | `RETURN_TO_OWNER` (explicit `COME_HERE` / `RETURN_OWNER`) | Yes | Cancels navigation-conflicting task |
| P3 | `60` | `ASSIGNED_TASK` (explicit player command: gather, build, farm, mine, escort) | Yes | Replaces prior player assignment |
| P4 | `55` | `SUBTASK_DEPENDENCY` (gather missing logs -> craft planks -> craft pickaxe) | Parent-bound | Feeds parent `ASSIGNED_TASK` |
| P5 | `40` | `AUTONOMOUS_SURVIVAL` (proactive tool equip, inventory sort, night shelter check) | Only when idle | N/A |
| P6 | `15` | `IDLE_EXPLORATION` (follow owner at distance, patrol, idle look-around) | Lowest | N/A |

Goal-switching hysteresis: non-emergency preemptions enforce a minimum `20`-tick cooldown to prevent oscillation.

## 3. Canonical Operating Modes

- `COMPANION`: Balanced default — stays near owner, assists threats, accepts tasks, runs self-preservation.
- `FOLLOW`: Tracks owner position maintaining configurable distance (`default 4.0m`, stop buffer `2.5m`), never colliding with owner.
- `STAY`: Holds exact block position until explicitly reassigned or threatened in melee range.
- `GUARD`: Establishes a bounded protection zone (`default 16.0m` radius) around owner or home anchor; engages nearby hostiles and returns to post rather than chasing across the world.
- `RESOURCE_GATHERING`: Executes bounded world search, pathfinding, block harvesting, drop collection, and inventory verification.
- `CONSTRUCTION`: Site selection, terrain leveling check, material dependency planning, layer-by-layer real inventory placement, and final block audit.
- `EXPLORATION`: Waypoint scouting within bounded radius from home anchor while avoiding lava, deep water, and cliffs.
- `RETURN_TO_OWNER`: Navigates back to owner using pathfinding (never silent teleportation).
- `IDLE`: Halts movement and active plans while remaining responsive.
- `AUTONOMOUS_SURVIVAL`: Proactively gathers basic food/wood, upgrades wooden/stone tools, eats when injured, and guards through nightfall.

## 4. Truthfulness & Anti-Cheat Invariants

1. **No Fabricated Inventory**: Items enter the companion inventory exclusively via collecting real dropped world items or executing verified crafting recipes that consume exact input items.
2. **No Fabricated Construction**: Every block placed in a blueprint consumes a matching item from the companion inventory. Missing materials trigger a real gathering/crafting dependency plan or truthful failure report.
3. **No Silent Teleportation**: Navigation uses platform pathfinding + local 3D A* waypoint recovery. Emergency teleportation is disabled by default (`allowEmergencyTeleport = false`).
4. **PvP Safety Lock**: Player-vs-player targeting is strictly disabled by default unless BOTH server PvP rules permit it AND the command carries explicit authorization (`if pvp is enabled` / `در صورت مجاز بودن PvP` + owner authorization).
