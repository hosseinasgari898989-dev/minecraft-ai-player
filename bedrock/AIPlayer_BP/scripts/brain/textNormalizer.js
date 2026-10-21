/**
 * Offline Persian/Arabic/English Unicode & numeral normalizer for AI Player (Bedrock Edition).
 * Shares identical normalization rules with Java TextNormalizer.java.
 */

const PERSIAN_SCRIPT = /[\u0600-\u06FF]/;
const DIACRITICS = /[\u064B-\u065F\u0670\u0640]/g;
const PUNCTUATION = /[.,!?;:،؛؟"'«»()[\]{}]+/g;
const MULTI_SPACE = /\s+/g;
const FIRST_INTEGER = /\b(\d{1,4})\b/;

const NUMBER_WORDS = new Map([
  ["one", 1], ["two", 2], ["three", 3], ["four", 4], ["five", 5],
  ["six", 6], ["eight", 8], ["ten", 10], ["twelve", 12], ["dozen", 12],
  ["sixteen", 16], ["twenty", 20], ["thirtytwo", 32], ["stack", 64],
  ["یک", 1], ["یه", 1], ["دو", 2], ["سه", 3], ["چهار", 4], ["پنج", 5],
  ["شش", 6], ["هشت", 8], ["ده", 10], ["دوازده", 12], ["شانزده", 16],
  ["شونزده", 16], ["بیست", 20], ["سی", 30], ["شصت", 64], ["استک", 64]
]);

export function detectLanguage(raw) {
  if (!raw) return "en";
  return PERSIAN_SCRIPT.test(raw) ? "fa" : "en";
}

export function normalizePreservingCase(raw) {
  if (!raw || !raw.trim()) return "";
  const text = raw.normalize("NFKC");
  let out = "";
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i);
    const ch = text[i];
    if (code >= 0x06f0 && code <= 0x06f9) {
      out += String.fromCharCode(48 + (code - 0x06f0));
    } else if (code >= 0x0660 && code <= 0x0669) {
      out += String.fromCharCode(48 + (code - 0x0660));
    } else if (ch === "\u064A" || ch === "\u0649" || ch === "\u06CC") {
      out += "\u06CC";
    } else if (ch === "\u0643" || ch === "\u06A9") {
      out += "\u06A9";
    } else if (ch === "\u0629") {
      out += "\u0647";
    } else if (ch === "\u0624") {
      out += "\u0648";
    } else if (ch === "\u0623" || ch === "\u0625") {
      out += "\u0627";
    } else if (ch === "\u200C" || ch === "\u200D" || ch === "\uFEFF") {
      out += " ";
    } else {
      out += ch;
    }
  }
  return out
    .replace(DIACRITICS, "")
    .replace(PUNCTUATION, " ")
    .replace(MULTI_SPACE, " ")
    .trim();
}

export function stripWakePrefix(preservedCaseText) {
  const trimmed = preservedCaseText.trim();
  const lower = trimmed.toLowerCase();
  const prefixes = [
    "@aiplayer ", "aiplayer ", "ai player ", "@bot ", "bot ",
    "@ربات ", "ربات ", "ای آی پلیر ", "همراه "
  ];
  for (const prefix of prefixes) {
    if (lower.startsWith(prefix)) {
      return trimmed.slice(prefix.length).trim();
    }
  }
  return trimmed;
}

export function extractQuantity(normalizedLower) {
  const match = FIRST_INTEGER.exec(normalizedLower);
  if (match) {
    const parsed = parseInt(match[1], 10);
    if (parsed > 0) return Math.min(parsed, 2304);
  }
  const tokens = normalizedLower.split(/\s+/);
  for (let i = 0; i < tokens.length; i++) {
    const tok = tokens[i];
    if (tok === "half" && tokens[i + 1] === "stack") return 32;
    if (tok === "نیم" && tokens[i + 1] === "استک") return 32;
    if (NUMBER_WORDS.has(tok)) return NUMBER_WORDS.get(tok);
  }
  return null;
}
