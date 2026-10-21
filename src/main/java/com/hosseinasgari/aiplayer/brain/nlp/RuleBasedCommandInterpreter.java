package com.hosseinasgari.aiplayer.brain.nlp;

import com.hosseinasgari.aiplayer.brain.model.CommandIntent;
import com.hosseinasgari.aiplayer.brain.model.IntentType;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleBasedCommandInterpreter {
    private static final Pattern EN_PLAYER_PATTERN = Pattern.compile("(?:player\\s+)([A-Za-z0-9_]{2,16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern FA_PLAYER_PATTERN = Pattern.compile("(?:بازیکن\\s+)([A-Za-z0-9_\\u0600-\\u06FF]{2,16})");
    private RuleBasedCommandInterpreter() {}

    public static CommandIntent interpret(String rawInput) {
        String lang = TextNormalizer.detectLanguage(rawInput);
        String preserved = TextNormalizer.stripWakePrefix(TextNormalizer.normalizePreservingCase(rawInput));
        String norm = preserved.toLowerCase(Locale.ROOT);
        if (norm.isBlank()) return CommandIntent.clarify(lang, norm, lang.equals("fa")
                ? "چه کاری انجام دهم؟ مثلاً: دنبالم بیا، ۱۶ تا چوب جمع کن، خونه بساز."
                : "What task would you like me to perform? (e.g., follow me, collect 16 wood, build a house)");

        String targetPlayer = extractPlayerName(preserved);
        if (targetPlayer != null) {
            if (hasAny(norm, "attack", "fight", "hit", "kill", "حمله")) return new CommandIntent(IntentType.ATTACK_PLAYER, lang, norm, null, null, null, targetPlayer, null, hasPvpAuthorizationPhrase(norm), false, null, null);
            if (hasAny(norm, "follow", "track", "shadow", "دنبال")) return new CommandIntent(IntentType.FOLLOW_PLAYER, lang, norm, null, null, null, targetPlayer, null, false, false, null, null);
            if (hasAny(norm, "find", "locate", "search", "where is", "پیدا", "بگرد")) return new CommandIntent(IntentType.FIND_PLAYER, lang, norm, null, null, null, targetPlayer, null, false, false, null, null);
        }

        if (hasAny(norm, "cancel", "abort", "clear task", "forget previous", "لغو", "کنسل", "بیخیال دستور")) return CommandIntent.simple(IntentType.CANCEL_TASK, lang, norm);
        if (hasAny(norm, "resume", "continue task", "ادامه بده", "کار قبلی")) return CommandIntent.simple(IntentType.RESUME_TASK, lang, norm);
        if (hasAny(norm, "status", "report", "inventory status", "وضعیت", "گزارش")) return CommandIntent.simple(IntentType.STATUS_REPORT, lang, norm);
        if (hasExactOrPhrase(norm, "stop", "wait", "halt", "stand still", "freeze", "صبر کن", "وایسا", "توقف", "بس کن", "ایست")) return CommandIntent.simple(IntentType.STOP, lang, norm);

        if (hasAny(norm, "nearby threat", "check for threat", "dangerous mob", "scan threat", "hostile", "موجودات خطرناک", "خطر", "بررسی کن", "دشمن")
                && hasAny(norm, "check", "scan", "inspect", "look for", "بررسی", "ببین", "چک")) return CommandIntent.simple(IntentType.SCAN_THREATS, lang, norm);
        if (hasAny(norm, "night", "شب") && hasAny(norm, "protect", "guard", "defend", "محافظت", "مراقبت", "نگهبانی")) return CommandIntent.simple(IntentType.PROTECT_NIGHT, lang, norm);
        if (hasAny(norm, "guard", "protect this area", "defend area", "گارد بده", "گارد", "نگهبانی بده", "از این منطقه")) return new CommandIntent(IntentType.GUARD_AREA, lang, norm, null, null, null, null, extractDistanceOrNull(norm), false, false, null, null);
        if (hasAny(norm, "patrol", "گشت بزن", "گشت بده", "دور بزن")) return CommandIntent.simple(IntentType.PATROL_AREA, lang, norm);
        if (hasAny(norm, "protect me", "defend me", "keep me safe", "care for me", "bodyguard", "مراقبت کن", "محافظت کن", "محافظم باش", "هوامو داشته باش")) return CommandIntent.simple(IntentType.PROTECT_OWNER, lang, norm);

        if (hasAny(norm, "stay beside", "stay with me", "by my side", "stay close", "کنارم بمون", "کنارم بمان", "پیشم بمون", "کنار من بمون")) return CommandIntent.simple(IntentType.STAY_BESIDE, lang, norm);
        if (hasAny(norm, "stay here", "hold position", "اینجا بمون", "اینجا بمان", "سر جات بمون")) return CommandIntent.simple(IntentType.HOLD_POSITION, lang, norm);
        if (hasAny(norm, "follow me", "follow", "come with me", "move behind me", "walk behind me", "دنبالم بیا", "دنبال من بیا", "دنبالم حرکت کن", "پشت سرم حرکت کن", "همراهم بیا")) return new CommandIntent(IntentType.FOLLOW_OWNER, lang, norm, null, null, null, null, extractDistanceOrNull(norm), false, false, null, null);
        if (hasAny(norm, "return home", "go home", "برگرد خونه", "برگرد خانه", "برو خونه", "برو خانه")) return CommandIntent.simple(IntentType.RETURN_HOME, lang, norm);
        if (hasAny(norm, "set home", "خانه اینجاست", "خونه اینجاست")) return CommandIntent.simple(IntentType.SET_HOME, lang, norm);
        if (hasAny(norm, "return to me", "get back to me", "come back", "برگرد پیشم", "برگرد اینجا", "برگرد نزد من")) return CommandIntent.simple(IntentType.RETURN_OWNER, lang, norm);
        if (hasAny(norm, "come here", "come to me", "come closer", "بیا پیشم", "بیا اینجا", "نزدیک بیا")) return CommandIntent.simple(IntentType.COME_HERE, lang, norm);

        if (hasAny(norm, "upgrade your tool", "upgrade tool", "better tool", "craft pickaxe", "craft axe", "craft sword", "ابزار بهتر", "ابزار بساز", "کلنگ بساز", "تبر بساز", "شمشیر بساز", "ارتقا ابزار")) return CommandIntent.simple(IntentType.UPGRADE_TOOLS, lang, norm);
        if (hasAny(norm, "organize", "sort inventory", "tidy inventory", "وسایلت رو مرتب", "مرتب کن", "کیفت رو مرتب")) return CommandIntent.simple(IntentType.ORGANIZE_INVENTORY, lang, norm);
        if (hasAny(norm, "store item", "put in chest", "deposit", "بذار تو صندوق", "داخل صندوق")) return CommandIntent.simple(IntentType.STORE_ITEMS, lang, norm);
        if (hasAny(norm, "plant", "sow", "بکار", "کشت کن") && hasAny(norm, "wheat", "crop", "seed", "گندم", "بذر", "محصول")) {
            Integer qty = TextNormalizer.extractQuantity(norm);
            return new CommandIntent(IntentType.PLANT_CROPS, lang, norm, "wheat", qty != null ? qty : 9, null, null, null, false, false, null, null);
        }
        if (hasAny(norm, "harvest crop", "harvest wheat", "برداشت کن", "گندم جمع کن", "محصول جمع کن")) return CommandIntent.simple(IntentType.HARVEST_CROPS, lang, norm);
        if (hasAny(norm, "find food", "get food", "gather food", "hungry", "غذا پیدا کن", "غذا بیار", "غذا جمع کن", "خوراکی")) {
            Integer qty = TextNormalizer.extractQuantity(norm);
            return new CommandIntent(IntentType.FIND_FOOD, lang, norm, "food", qty != null ? qty : 6, null, null, null, false, false, null, null);
        }
        if (hasAny(norm, "go mining", "mine ore", "dig mine", "برو معدن", "معدن کاوی", "استخراج کن")) {
            Integer qty = TextNormalizer.extractQuantity(norm);
            return new CommandIntent(IntentType.GO_MINING, lang, norm, "iron", qty != null ? qty : 12, null, null, null, false, false, null, null);
        }
        if (hasAny(norm, "build", "construct", "make a", "بساز", "درست کن", "بنا کن")) {
            String style = detectStructureStyle(norm);
            if (style != null) return new CommandIntent(IntentType.BUILD_STRUCTURE, lang, norm, null, null, style, null, null, false, false, null, null);
            return CommandIntent.clarify(lang, norm, lang.equals("fa") ? "چه سازه‌ای بسازم؟ (پناهگاه، خونه چوبی، خونه شیک، مزرعه، یا برج)" : "Which structure should I build? (shelter, house, nice house, farm, or tower)");
        }

        String resource = detectResource(norm);
        if (resource != null && hasAny(norm, "gather", "collect", "chop", "mine", "get", "find", "bring", "harvest", "جمع کن", "جمع اوری", "جمع آوری", "پیدا کن", "بیار", "بکن", "قطع کن", "تهیه کن")) {
            Integer qty = TextNormalizer.extractQuantity(norm);
            int d = switch (resource) { case "wood" -> 8; case "stone" -> 16; case "coal" -> 12; case "iron" -> 8; default -> 8; };
            return new CommandIntent(IntentType.GATHER_RESOURCE, lang, norm, resource, qty != null ? qty : d, null, null, null, false, false, null, null);
        }
        if (hasAny(norm, "explore", "scout", "wander", "کاوش کن", "اطراف رو بگرد", "منطقه رو بگرد", "بگرد")) return CommandIntent.simple(IntentType.EXPLORE, lang, norm);
        if (hasAny(norm, "collect", "gather", "mine", "find", "جمع کن", "پیدا کن", "بیار")) return CommandIntent.clarify(lang, norm, lang.equals("fa") ? "چه منبعی جمع کنم؟ (چوب، سنگ، زغال، آهن، یا غذا)" : "Which resource should I gather? (wood, stone, coal, iron, or food)");
        return CommandIntent.unsupported(lang, norm, lang.equals("fa")
                ? "این درخواست خارج از مهارت‌های محلی فعلی من است. مهارت‌های فعال: دنبال کردن، محافظت، جمع‌آوری چوب/سنگ/زغال/آهن/غذا، ساخت ابزار، مرتب‌سازی وسایل، ساخت خانه/مزرعه/برج و کاشت گندم."
                : "That request is outside my local deterministic skills. Supported skills: follow, protect, guard, gather wood/stone/coal/iron/food, tool upgrades, inventory sorting, building houses/farms/towers, and wheat farming.");
    }

    private static String detectStructureStyle(String norm) {
        if (hasAny(norm, "farm", "مزرعه", "کشتزار")) return "small_farm";
        if (hasAny(norm, "tower", "outpost", "برج", "دیده بانی")) return "watchtower";
        if (hasAny(norm, "shelter", "hut", "cabin", "پناهگاه", "کلبه")) return "shelter";
        if (hasAny(norm, "nice house", "modern", "stylish", "pretty house", "شیک", "مدرن", "قشنگ", "زیبا")) return "modern_house";
        if (hasAny(norm, "large house", "big house", "decorative", "villa", "بزرگ", "تزئینی", "مجلل", "ویلا")) return "decorative_house";
        if (hasAny(norm, "house", "home", "خونه", "خانه", "ساختمان")) return "compact_wood";
        return null;
    }
    private static String detectResource(String norm) {
        if (hasAny(norm, "wood", "log", "tree", "timber", "oak", "birch", "spruce", "چوب", "هیزم", "درخت")) return "wood";
        if (hasAny(norm, "stone", "cobble", "cobblestone", "rock", "سنگ", "کابل استون")) return "stone";
        if (hasAny(norm, "coal", "charcoal", "زغال")) return "coal";
        if (hasAny(norm, "iron", "ore", "آهن", "اهن")) return "iron";
        if (hasAny(norm, "food", "meat", "apple", "bread", "غذا", "نان", "سیب")) return "food";
        if (hasAny(norm, "wheat", "seed", "گندم", "بذر")) return "wheat";
        return null;
    }
    private static String extractPlayerName(String preserved) {
        Matcher en = EN_PLAYER_PATTERN.matcher(preserved);
        if (en.find()) { String name = en.group(1); if (!List.of("me", "here", "and", "or", "is").contains(name.toLowerCase(Locale.ROOT))) return name; }
        Matcher fa = FA_PLAYER_PATTERN.matcher(preserved);
        if (fa.find()) { String name = fa.group(1); if (!List.of("رو", "را", "به", "از").contains(name)) return name; }
        return null;
    }
    private static boolean hasPvpAuthorizationPhrase(String norm) { return hasAny(norm, "pvp is enabled", "authorized", "if pvp", "مجاز بودن pvp", "در صورت مجاز", "مجاز است"); }
    private static Double extractDistanceOrNull(String norm) {
        if (hasAny(norm, "block", "meter", "بلاک", "متر", "فاصله")) {
            Integer q = TextNormalizer.extractQuantity(norm);
            if (q != null && q >= 1 && q <= 64) return q.doubleValue();
        }
        return null;
    }
    private static boolean hasAny(String text, String... needles) { for (String n : needles) if (text.contains(n)) return true; return false; }
    private static boolean hasExactOrPhrase(String text, String... phrases) { for (String p : phrases) if (text.equals(p) || text.startsWith(p + " ") || text.endsWith(" " + p) || text.contains(" " + p + " ")) return true; return false; }
}
