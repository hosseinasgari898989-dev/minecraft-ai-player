# AI Player Architecture Specification

## Layered Module Design

1. **Perception Layer (`WorldSnapshot` / `worldSensor`)**
   - Reads bounded local world slices (18-block radius on Java, 16-block radius on Bedrock) on throttled ticks (`10` ticks = `0.5s` brain cycle, `5` ticks = `0.25s` melee/pickup cycle).
   - Never mutates world state and never caches stale entity references across ticks.

2. **Natural-Language Command Layer (`TextNormalizer` + `RuleBasedCommandInterpreter`)**
   - Performs NFKC Unicode normalization, Persian/Arabic character folding (`ي -> ی`, `ك -> ک`, ZWNJ half-spaces), Persian/Arabic numeral conversion (`۰-۹`, `٠-٩` -> `0-9`), English/Persian number word parsing, player-name slot extraction, resource/style classification, ambiguity detection, and explicit PvP consent verification.

3. **Decision & Dependency Planner (`GoalManager` + `ActiveTask` + `SkillRegistry`)
   - Priority tiers: `EMERGENCY_SURVIVAL (100)` > `OWNER_PROTECTION (85)` > `RETURN_OWNER (75)` > `ASSIGNED_TASK (60)` > `AUTONOMOUS_SURVIVAL (40)` > `IDLE (15)`.
   - Suspends active construction/gathering into `suspendedTask` during combat/healing emergencies and resumes automatically once threats clear (`20`-tick switch cooldown prevents oscillation).
   - Computes real inventory deficits (`max(0, target - currentHave)`) and expands tool prerequisites (`wood -> oak_planks -> stick -> wooden_pickaxe -> stone -> stone_pickaxe`).

4. **Navigation & Recovery Layer (`LocalAStarPathfinder` + `StuckDetector`)
   - Bounded 512-node 3D A* grid search evaluating solid ground, 2-block headroom, 1-block jump steps, safe drops (`<= 3` blocks), water cost penalties, and impassable lava/fire/cactus/magma hazards.
   - `StuckDetector` triggers A* local detours up to `maxRecoveryAttempts` before failing cleanly with `FailureReason.ROUTE_BLOCKED` (no silent teleportation).

5. **Execution & Persistence Layer (`AiPlayerEntity` / `scripts/main.js` + `CompanionMemory`)
   - Breaks real blocks, sweeps genuine dropped `ItemEntity` stacks into container slots, consumes real items for recipes and block placement, validates placed blueprints, and persists state via NBT (Java) / Dynamic Properties + `minecraft:inventory` (Bedrock).
