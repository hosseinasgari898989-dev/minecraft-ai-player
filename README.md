# AI Player — Autonomous Offline Companion for Minecraft Java (Fabric 1.20.6) & Bedrock (Add-On 1.21+)

> **100% Local, Deterministic Game AI — Zero Hosted LLMs, Zero API Keys, Zero Cloud Servers, Zero Python Runtimes.**

`AI Player` is a dual-edition autonomous survival companion for **Minecraft Java Edition 1.20.6 (Fabric)** and **Minecraft Bedrock Edition 1.21+ (Behavior Pack + Resource Pack + Stable `@minecraft/server` API)**. Both editions implement the same **Shared Brain Contract** (`shared/specs/SHARED_BRAIN_CONTRACT.md`) using local perception, Unicode-normalized bilingual (English + Persian/فارسی) command parsing, hierarchical priority/dependency planning, bounded 3D A* pathfinding with stuck recovery, real inventory & tool progression, blueprint construction, and persistent world memory.

---

## Repository Layout

```text
minecraft-ai-player/
├── README.md                         # English engineering & installation guide
├── README.fa.md                      # راهنمای جامع فارسی نصب و معماری
├── LICENSE                           # MIT License (preserved from upstream main)
├── build.gradle                      # Fabric Loom 1.6.12 + Minecraft 1.20.6 + JUnit 5
├── gradle.properties                 # Java 21 / Yarn 1.20.6+build.3 / Fabric API 0.100.8
├── settings.gradle
├── src/
│   ├── main/java/com/hosseinasgari/aiplayer/
│   │   ├── AiPlayerMod.java          # Fabric entrypoint, lifecycle deduplication, /aiplayer commands & chat
│   │   ├── AiConfig.java             # Offline local brain configuration
│   │   ├── AiIntentClient.java       # Offline deterministic command-decision bridge
│   │   ├── client/                   # PlayerEntityModel 64x64 biped renderer with held tool display
│   │   ├── entity/                   # AiPlayerEntity (36-slot inventory, NBT persistence, world skills)
│   │   └── brain/
│   │       ├── model/                # IntentType, OperatingMode, FailureReason, CommandIntent
│   │       ├── nlp/                  # TextNormalizer (EN/FA/Arabic/ZWNJ) & RuleBasedCommandInterpreter
│   │       ├── perception/           # Bounded WorldSnapshot state representation
│   │       ├── planner/              # SkillRegistry, ActiveTask, GoalManager (priority + suspension)
│   │       ├── navigation/           # LocalAStarPathfinder (3D walkability + hazard avoidance) & StuckDetector
│   │       ├── construction/         # StructureBlueprint & BlueprintLibrary (6 survival structures)
│   │       └── memory/               # CompanionMemory with reload validation
│   ├── main/resources/               # fabric.mod.json + authentic 64x64 companion skin
│   └── test/java/                    # JUnit 5 test suite (Scenarios B, C, D, E, F, G, H, I, J)
├── bedrock/
│   ├── AIPlayer_BP/                  # Bedrock Behavior Pack + @minecraft/server 1.11.0 brain scripts
│   ├── AIPlayer_RP/                  # Bedrock Resource Pack (64x64 player geometry, skin, animations)
│   └── README.md                     # Bedrock installation & /scriptevent reference
├── shared/
│   ├── schemas/                      # JSON Schemas (intent, task, blueprint, memory, status)
│   ├── specs/                        # SHARED_BRAIN_CONTRACT.md
│   └── test-scenarios/               # canonical-command-suite.json (56 EN/FA test cases)
├── docs/
│   ├── ARCHITECTURE.md
│   ├── CAPABILITY_MATRIX.md
│   ├── BEHAVIOR_DESIGN.md
│   ├── COMMAND_REFERENCE.md
│   └── PLATFORM_DIFFERENCES.md
├── tests/
│   └── bedrock-and-parity.test.mjs   # Node.js automated test suite for Bedrock brain & manifest parity
├── tools/
│   └── package-all.mjs               # Validates manifests & packages .mcpack, .mcaddon, and source .zip
└── .github/workflows/                # Reproducible Java + Bedrock build & test CI workflows
```

---

## Quick Installation

### 1. Minecraft Java Edition 1.20.6 (Fabric)
1. Install **Minecraft Java Edition 1.20.6**, **Fabric Loader >= 0.19.5**, and **Fabric API 0.100.8+1.20.6**.
2. Copy `ai-player-0.2.0.jar` into your `.minecraft/mods/` folder (ensure only one version of `ai-player` is in `mods/`).
3. Enter a Singleplayer or Multiplayer world — your companion activates automatically (or run `/aiplayer spawn`).
4. Control naturally in chat (`Collect 16 pieces of wood.`, `Build me a nice house.`, `۱۶ تا چوب جمع کن.`) or via `/aiplayer ask <command>`, `/aiplayer status`, `/aiplayer mode <MODE>`.

### 2. Minecraft Bedrock Edition 1.21+
1. Double-click **`AIPlayer-Bedrock-0.2.0.mcaddon`** on Windows, Android, or iOS to import both `AIPlayer Companion BP` and `AIPlayer Companion RP` automatically.
2. Activate both packs on a world (**no experimental/Beta world toggles required** — targets stable `@minecraft/server` `1.11.0`).
3. Spawn your companion with `/function aiplayer/spawn` (or the **Spawn AI Player** egg) and issue commands via `/scriptevent aiplayer:cmd Collect 16 pieces of wood.` or `/scriptevent aiplayer:cmd ۱۶ تا چوب جمع کن.` (or direct chat where supported).

---

## Reproducible Local Build & Test Commands

```bash
# 1. Compile Java 21 / Fabric 1.20.6 JAR and run all JUnit 5 scenario suites:
gradle clean test build --no-daemon

# 2. Run Bedrock brain, manifest, blueprint, A*, and 56-case bilingual parity tests:
node --test tests/bedrock-and-parity.test.mjs

# 3. Validate manifests and generate .mcpack, .mcaddon, and clean source .zip:
node tools/package-all.mjs
```
