package com.hosseinasgari.aiplayer.brain.nlp;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextNormalizer {
    private static final Pattern PERSIAN_SCRIPT = Pattern.compile("[\\u0600-\\u06FF]");
    private static final Pattern DIACRITICS = Pattern.compile("[\\u064B-\\u065F\\u0670\\u0640]");
    private static final Pattern PUNCTUATION = Pattern.compile("[.,!?;:،؛؟«»(){}]+");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern FIRST_INTEGER = Pattern.compile("\\b(\\d{1,4})\\b");

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("eight", 8), Map.entry("ten", 10), Map.entry("twelve", 12),
            Map.entry("dozen", 12), Map.entry("sixteen", 16), Map.entry("twenty", 20),
            Map.entry("thirtytwo", 32), Map.entry("stack", 64), Map.entry("یک", 1),
            Map.entry("یه", 1), Map.entry("دو", 2), Map.entry("سه", 3), Map.entry("چهار", 4),
            Map.entry("پنج", 5), Map.entry("شش", 6), Map.entry("هشت", 8), Map.entry("ده", 10),
            Map.entry("دوازده", 12), Map.entry("شانزده", 16), Map.entry("شونزده", 16),
            Map.entry("بیست", 20), Map.entry("سی", 30), Map.entry("شصت", 60), Map.entry("استک", 64)
    );

    private TextNormalizer() {}

    public static String detectLanguage(String raw) {
        if (raw == null) return "en";
        return PERSIAN_SCRIPT.matcher(raw).find() ? "fa" : "en";
    }

    public static String normalizePreservingCase(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u06F0' && ch <= '\u06F9') {
                sb.append((char) ('0' + (ch - '\u06F0')));
            } else if (ch >= '\u0660' && ch <= '\u0669') {
                sb.append((char) ('0' + (ch - '\u0660')));
            } else {
                switch (ch) {
                    case '\u064A', '\u0649', '\u06CC' -> sb.append('\u06CC');
                    case '\u0643', '\u06A9' -> sb.append('\u06A9');
                    case '\u0629' -> sb.append('\u0647');
                    case '\u0624' -> sb.append('\u0648');
                    case '\u0623', '\u0625' -> sb.append('\u0627');
                    case '\u200C', '\u200D', '\uFEFF' -> sb.append(' ');
                    default -> sb.append(ch);
                }
            }
        }
        String cleaned = DIACRITICS.matcher(sb.toString()).replaceAll("");
        cleaned = PUNCTUATION.matcher(cleaned).replaceAll(" ");
        return MULTI_SPACE.matcher(cleaned).replaceAll(" ").trim();
    }

    public static String normalize(String raw) {
        return normalizePreservingCase(raw).toLowerCase(Locale.ROOT);
    }

    public static String stripWakePrefix(String preservedCaseText) {
        String trimmed = preservedCaseText.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        List<String> prefixes = List.of("@aiplayer ", "aiplayer ", "ai player ", "@bot ", "bot ", "@ربات ", "ربات ", "ای آی پلیر ", "همراه ");
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) return trimmed.substring(prefix.length()).trim();
        }
        return trimmed;
    }

    public static Integer extractQuantity(String normalizedLower) {
        Matcher m = FIRST_INTEGER.matcher(normalizedLower);
        if (m.find()) {
            int val = Integer.parseInt(m.group(1));
            if (val > 0) return Math.min(val, 2304);
        }
        String[] tokens = normalizedLower.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            if (tok.equals("half") && i + 1 < tokens.length && tokens[i + 1].equals("stack")) return 32;
            if (tok.equals("نیم") && i + 1 < tokens.length && tokens[i + 1].equals("استک")) return 32;
            Integer mapped = NUMBER_WORDS.get(tok);
            if (mapped != null) return mapped;
        }
        return null;
    }
}
