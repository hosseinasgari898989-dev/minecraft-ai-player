# Supported Bilingual Command Reference (English & فارسی)

## 1. Companion Movement & Modes
| English Command | Persian Command (فرمان فارسی) | Canonical Intent | Extracted Parameters |
|-----------------|-------------------------------|------------------|----------------------|
| `Come here.` | `بیا پیشم.` | `COME_HERE` | — |
| `Follow me.` / `AI Player, come here and follow me.` | `دنبالم بیا.` / `پشت سرم حرکت کن.` | `FOLLOW_OWNER` | optional `distance` |
| `Stay beside me.` | `کنارم بمون.` | `STAY_BESIDE` | — |
| `Stay here.` | `اینجا بمون.` | `HOLD_POSITION` | — |
| `Stop.` / `Wait.` | `صبر کن.` / `وایسا.` | `STOP` | — |
| `Cancel your current task.` | `دستورات قبلی رو لغو کن.` | `CANCEL_TASK` | — |
| `Resume task.` | `ادامه بده.` | `RESUME_TASK` | — |
| `Return to me.` | `برگرد پیشم.` | `RETURN_OWNER` | — |
| `Return home.` / `Set home.` | `برگرد خونه.` / `خونه اینجاست.` | `RETURN_HOME` / `SET_HOME` | — |

## 2. Gathering, Mining, Crafting, Farming & Inventory
| English Command | Persian Command (فرمان فارسی) | Canonical Intent | Extracted Parameters |
|-----------------|-------------------------------|------------------|----------------------|
| `Gather wood.` | `برو چوب جمع کن.` | `GATHER_RESOURCE` | `resource=wood, qty=8` |
| `Collect 16 pieces of wood.` | `۱۶ تا چوب جمع کن.` | `GATHER_RESOURCE` | `resource=wood, qty=16` |
| `Find stone.` | `برو سنگ پیدا کن.` | `GATHER_RESOURCE` | `resource=stone, qty=16` |
| `Go mining.` | `برو معدن.` | `GO_MINING` | `resource=iron, qty=12` |
| `Find food.` | `برام غذا پیدا کن.` | `FIND_FOOD` | `resource=food, qty=6` |
| `Upgrade your tools.` | `ابزار بهتر بساز.` | `UPGRADE_TOOLS` | — |
| `Organize your inventory.` | `وسایلت رو مرتب کن.` | `ORGANIZE_INVENTORY` | — |
| `Store items in chest.` | `وسایل رو بذار تو صندوق.` | `STORE_ITEMS` | — |
| `Plant wheat.` / `Harvest wheat.` | `گندم بکار.` / `محصول جمع کن.` | `PLANT_CROPS` / `HARVEST_CROPS` | `resource=wheat` |

## 3. Construction Blueprints
| English Command | Persian Command (فرمان فارسی) | Blueprint ID |
|-----------------|-------------------------------|--------------|
| `Build a shelter.` | `یک پناهگاه بساز.` | `shelter` (5x4x5) |
| `Build me a house.` | `برام یک خونه بساز.` | `compact_wood` (7x5x7) |
| `Build me a nice house.` | `برام یک خونه شیک بساز.` | `modern_house` (7x5x7) |
| `Build a large decorative house.` | `یک خونه بزرگ بساز.` | `decorative_house` (9x6x9) |
| `Build a farm.` | `یک مزرعه درست کن.` | `small_farm` (5x2x5) |
| `Build a tower.` | `یک برج بساز.` | `watchtower` (5x8x5) |

## 4. Protection, Scanning & Player Interaction
| English Command | Persian Command (فرمان فارسی) | Canonical Intent |
|-----------------|-------------------------------|------------------|
| `Protect me.` | `از من محافظت کن.` / `ازم مراقبت کن.` | `PROTECT_OWNER` |
| `Protect me during the night.` | `شب از من محافظت کن.` | `PROTECT_NIGHT` |
| `Guard this area.` / `Patrol.` | `اطراف من گارد بده.` / `گشت بزن.` | `GUARD_AREA` / `PATROL_AREA` |
| `Check for nearby threats.` | `موجودات خطرناک اطرافم رو بررسی کن.` | `SCAN_THREATS` |
| `Find player [Name].` | `برو بازیکن [Name] رو پیدا کن.` | `FIND_PLAYER` |
| `Follow player [Name].` | `بازیکن [Name] رو دنبال کن.` | `FOLLOW_PLAYER` |
| `Attack player [Name], if authorized and PvP is enabled.` | `در صورت مجاز بودن PvP، به بازیکن [Name] حمله کن.` | `ATTACK_PLAYER` |
