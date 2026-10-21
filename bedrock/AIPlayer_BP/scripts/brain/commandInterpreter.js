import { detectLanguage, extractQuantity, normalizePreservingCase, stripWakePrefix } from "./textNormalizer.js";

const EN_PLAYER_PATTERN = /(?:player\s+)([A-Za-z0-9_]{2,16})/i;
const FA_PLAYER_PATTERN = /(?:بازیکن\s+)([A-Za-z0-9_\u0600-\u06FF]{2,16})/;
const hasAny = (text, ...needles) => needles.some((n) => text.includes(n));
const hasExactOrPhrase = (text, ...phrases) => phrases.some((p) => text === p || text.startsWith(p + " ") || text.endsWith(" " + p) || text.includes(" " + p + " "));

function extractPlayerName(preserved) {
  const en = EN_PLAYER_PATTERN.exec(preserved);
  if (en && !["me", "here", "and", "or", "is"].includes(en[1].toLowerCase())) return en[1];
  const fa = FA_PLAYER_PATTERN.exec(preserved);
  if (fa && !["رو", "را", "به", "از"].includes(fa[1])) return fa[1];
  return null;
}
function hasPvpAuthorizationPhrase(norm) { return hasAny(norm, "pvp is enabled", "authorized", "if pvp", "مجاز بودن pvp", "در صورت مجاز", "مجاز است"); }
function detectStructureStyle(norm) {
  if (hasAny(norm, "farm", "مزرعه", "کشتزار")) return "small_farm";
  if (hasAny(norm, "tower", "outpost", "برج", "دیده بانی")) return "watchtower";
  if (hasAny(norm, "shelter", "hut", "cabin", "پناهگاه", "کلبه")) return "shelter";
  if (hasAny(norm, "nice house", "modern", "stylish", "pretty house", "شیک", "مدرن", "قشنگ", "زیبا")) return "modern_house";
  if (hasAny(norm, "large house", "big house", "decorative", "villa", "بزرگ", "تزئینی", "مجلل", "ویلا")) return "decorative_house";
  if (hasAny(norm, "house", "home", "خونه", "خانه", "ساختمان")) return "compact_wood";
  return null;
}
function detectResource(norm) {
  if (hasAny(norm, "wood", "log", "tree", "timber", "oak", "birch", "spruce", "چوب", "هیزم", "درخت")) return "wood";
  if (hasAny(norm, "stone", "cobble", "cobblestone", "rock", "سنگ", "کابل استون")) return "stone";
  if (hasAny(norm, "coal", "charcoal", "زغال")) return "coal";
  if (hasAny(norm, "iron", "ore", "آهن", "اهن")) return "iron";
  if (hasAny(norm, "food", "meat", "apple", "bread", "غذا", "نان", "سیب")) return "food";
  if (hasAny(norm, "wheat", "seed", "گندم", "بذر")) return "wheat";
  return null;
}
function extractDistanceOrNull(norm) {
  if (hasAny(norm, "block", "meter", "بلاک", "متر", "فاصله")) { const q = extractQuantity(norm); if (q !== null && q >= 1 && q <= 64) return q; }
  return null;
}
function makeIntent(intent, language, normalizedInput, extras = {}) {
  return { intent, language, normalizedInput, resource: extras.resource ?? null, quantity: extras.quantity ?? null, structureStyle: extras.structureStyle ?? null, targetPlayer: extras.targetPlayer ?? null, distance: extras.distance ?? null, pvpExplicitlyAuthorized: extras.pvpExplicitlyAuthorized ?? false, ambiguous: extras.ambiguous ?? false, clarificationPrompt: extras.clarificationPrompt ?? null, unsupportedReason: extras.unsupportedReason ?? null };
}

export function interpretCommand(rawInput) {
  const lang = detectLanguage(rawInput), preserved = stripWakePrefix(normalizePreservingCase(rawInput)), norm = preserved.toLowerCase();
  if (!norm) return makeIntent("CLARIFY", lang, norm, { ambiguous: true, clarificationPrompt: lang === "fa" ? "چه کاری انجام دهم؟ مثلاً: دنبالم بیا، ۱۶ تا چوب جمع کن، خونه بساز." : "What task would you like me to perform? (e.g., follow me, collect 16 wood, build a house)" });

  const targetPlayer = extractPlayerName(preserved);
  if (targetPlayer) {
    if (hasAny(norm, "attack", "fight", "hit", "kill", "حمله")) return makeIntent("ATTACK_PLAYER", lang, norm, { targetPlayer, pvpExplicitlyAuthorized: hasPvpAuthorizationPhrase(norm) });
    if (hasAny(norm, "follow", "track", "shadow", "دنبال")) return makeIntent("FOLLOW_PLAYER", lang, norm, { targetPlayer });
    if (hasAny(norm, "find", "locate", "search", "where is", "پیدا", "بگرد")) return makeIntent("FIND_PLAYER", lang, norm, { targetPlayer });
  }
  if (hasAny(norm, "cancel", "abort", "clear task", "forget previous", "لغو", "کنسل", "بیخیال دستور")) return makeIntent("CANCEL_TASK", lang, norm);
  if (hasAny(norm, "resume", "continue task", "ادامه بده", "کار قبلی")) return makeIntent("RESUME_TASK", lang, norm);
  if (hasAny(norm, "status", "report", "inventory status", "وضعیت", "گزارش")) return makeIntent("STATUS_REPORT", lang, norm);
  if (hasExactOrPhrase(norm, "stop", "wait", "halt", "stand still", "freeze", "صبر کن", "وایسا", "توقف", "بس کن", "ایست")) return makeIntent("STOP", lang, norm);
  if (hasAny(norm, "nearby threat", "check for threat", "dangerous mob", "scan threat", "hostile", "موجودات خطرناک", "خطر", "بررسی کن", "دشمن") && hasAny(norm, "check", "scan", "inspect", "look for", "بررسی", "ببین", "چک")) return makeIntent("SCAN_THREATS", lang, norm);
  if (hasAny(norm, "night", "شب") && hasAny(norm, "protect", "guard", "defend", "محافظت", "مراقبت", "نگهبانی")) return makeIntent("PROTECT_NIGHT", lang, norm);
  if (hasAny(norm, "guard", "protect this area", "defend area", "گارد بده", "گارد", "نگهبانی بده", "از این منطقه")) return makeIntent("GUARD_AREA", lang, norm, { distance: extractDistanceOrNull(norm) });
  if (hasAny(norm, "patrol", "گشت بزن", "گشت بده", "دور بزن")) return makeIntent("PATROL_AREA", lang, norm);
  if (hasAny(norm, "protect me", "defend me", "keep me safe", "care for me", "bodyguard", "مراقبت کن", "محافظت کن", "محافظم باش", "هوامو داشته باش")) return makeIntent("PROTECT_OWNER", lang, norm);
  if (hasAny(norm, "stay beside", "stay with me", "by my side", "stay close", "کنارم بمون", "کنارم بمان", "پیشم بمون", "کنار من بمون")) return makeIntent("STAY_BESIDE", lang, norm);
  if (hasAny(norm, "stay here", "hold position", "اینجا بمون", "اینجا بمان", "سر جات بمون")) return makeIntent("HOLD_POSITION", lang, norm);
  if (hasAny(norm, "follow me", "follow", "come with me", "move behind me", "walk behind me", "دنبالم بیا", "دنبال من بیا", "دنبالم حرکت کن", "پشت سرم حرکت کن", "همراهم بیا")) return makeIntent("FOLLOW_OWNER", lang, norm, { distance: extractDistanceOrNull(norm) });
  if (hasAny(norm, "return home", "go home", "برگرد خونه", "برگرد خانه", "برو خونه", "برو خانه")) return makeIntent("RETURN_HOME", lang, norm);
  if (hasAny(norm, "set home", "خانه اینجاست", "خونه اینجاست")) return makeIntent("SET_HOME", lang, norm);
  if (hasAny(norm, "return to me", "get back to me", "come back", "برگرد پیشم", "برگرد اینجا", "برگرد نزد من")) return makeIntent("RETURN_OWNER", lang, norm);
  if (hasAny(norm, "come here", "come to me", "come closer", "بیا پیشم", "بیا اینجا", "نزدیک بیا")) return makeIntent("COME_HERE", lang, norm);
  if (hasAny(norm, "upgrade your tool", "upgrade tool", "better tool", "craft pickaxe", "craft axe", "craft sword", "ابزار بهتر", "ابزار بساز", "کلنگ بساز", "تبر بساز", "شمشیر بساز", "ارتقا ابزار")) return makeIntent("UPGRADE_TOOLS", lang, norm);
  if (hasAny(norm, "organize", "sort inventory", "tidy inventory", "وسایلت رو مرتب", "مرتب کن", "کیفت رو مرتب")) return makeIntent("ORGANIZE_INVENTORY", lang, norm);
  if (hasAny(norm, "store item", "put in chest", "deposit", "بذار تو صندوق", "داخل صندوق")) return makeIntent("STORE_ITEMS", lang, norm);
  if (hasAny(norm, "plant", "sow", "بکار", "کشت کن") && hasAny(norm, "wheat", "crop", "seed", "گندم", "بذر", "محصول")) return makeIntent("PLANT_CROPS", lang, norm, { resource: "wheat", quantity: extractQuantity(norm) ?? 9 });
  if (hasAny(norm, "harvest crop", "harvest wheat", "برداشت کن", "گندم جمع کن", "محصول جمع کن")) return makeIntent("HARVEST_CROPS", lang, norm);
  if (hasAny(norm, "find food", "get food", "gather food", "hungry", "غذا پیدا کن", "غذا بیار", "غذا جمع کن", "خوراکی")) return makeIntent("FIND_FOOD", lang, norm, { resource: "food", quantity: extractQuantity(norm) ?? 6 });
  if (hasAny(norm, "go mining", "mine ore", "dig mine", "برو معدن", "معدن کاوی", "استخراج کن")) return makeIntent("GO_MINING", lang, norm, { resource: "iron", quantity: extractQuantity(norm) ?? 12 });
  if (hasAny(norm, "build", "construct", "make a", "بساز", "درست کن", "بنا کن")) {
    const style = detectStructureStyle(norm);
    if (style) return makeIntent("BUILD_STRUCTURE", lang, norm, { structureStyle: style });
    return makeIntent("CLARIFY", lang, norm, { ambiguous: true, clarificationPrompt: lang === "fa" ? "چه سازه‌ای بسازم؟ (پناهگاه، خونه چوبی، خونه شیک، مزرعه، یا برج)" : "Which structure should I build? (shelter, house, nice house, farm, or tower)" });
  }
  const resource = detectResource(norm);
  if (resource && hasAny(norm, "gather", "collect", "chop", "mine", "get", "find", "bring", "harvest", "جمع کن", "جمع اوری", "جمع آوری", "پیدا کن", "بیار", "بکن", "قطع کن", "تهیه کن")) {
    const qty = extractQuantity(norm), defaults = { wood: 8, stone: 16, coal: 12, iron: 8 };
    return makeIntent("GATHER_RESOURCE", lang, norm, { resource, quantity: qty ?? (defaults[resource] || 8) });
  }
  if (hasAny(norm, "explore", "scout", "wander", "کاوش کن", "اطراف رو بگرد", "منطقه رو بگرد", "بگرد")) return makeIntent("EXPLORE", lang, norm);
  if (hasAny(norm, "collect", "gather", "mine", "find", "جمع کن", "پیدا کن", "بیار")) return makeIntent("CLARIFY", lang, norm, { ambiguous: true, clarificationPrompt: lang === "fa" ? "چه منبعی جمع کنم؟ (چوب، سنگ، زغال، آهن، یا غذا)" : "Which resource should I gather? (wood, stone, coal, iron, or food)" });
  return makeIntent("UNSUPPORTED", lang, norm, { unsupportedReason: lang === "fa" ? "این درخواست خارج از مهارت‌های محلی فعلی من است. مهارت‌های فعال: دنبال کردن، محافظت، جمع‌آوری چوب/سنگ/زغال/آهن/غذا، ساخت ابزار، مرتب‌سازی وسایل، ساخت خانه/مزرعه/برج و کاشت گندم." : "That request is outside my local deterministic skills. Supported skills: follow, protect, guard, gather wood/stone/coal/iron/food, tool upgrades, inventory sorting, building houses/farms/towers, and wheat farming." });
}
