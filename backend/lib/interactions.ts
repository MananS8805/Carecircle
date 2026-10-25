import { parseIngredients } from "./normalize.js";
import { US_NAMES, fetchLabel } from "./sources.js";
import { TtlCache } from "./cache.js";
import { allow } from "./ratelimit.js";

type Env = { APP_KEY?: string };
type F = typeof fetch;

export const MAX_NAMES = 8;
export const NO_INTERACTION_MESSAGE = "No interaction found in the official label text. This does not mean none exists.";
export const INTERACTION_DISCLAIMER = "Informational only, not medical advice. Ask your doctor or pharmacist.";

export interface InteractionPair { a: string; b: string; snippet: string; source: string; sourceUrl: string }
type LabelText = { text: string | null };

// Label text per generic name. Only drug-interaction text is kept; nothing about the user is stored.
const labelCache = new TtlCache<LabelText>(6 * 60 * 60 * 1000);
export function clearInteractionCache() { (labelCache as any).m.clear(); }

const json = (status: number, body: unknown, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...extra } });

// Other names a label may use for the same drug (besides the INN <-> USAN map in sources.ts).
const EXTRA_SYNONYMS: Record<string, string[]> = {
  aspirin: ["acetylsalicylic acid", "salicylate"],
  "vitamin c": ["ascorbic acid"],
  "ascorbic acid": ["vitamin c"],
  "vitamin k": ["phytonadione", "phylloquinone"],
  "vitamin d": ["cholecalciferol", "ergocalciferol"],
};

/** All names a label might use for [name]: itself, the US/INN counterpart and known synonyms. */
export function aliases(name: string): string[] {
  const n = name.toLowerCase();
  const out = new Set<string>([n]);
  if (US_NAMES[n]) out.add(US_NAMES[n]);
  for (const [inn, usan] of Object.entries(US_NAMES)) if (usan === n) out.add(inn);
  for (const x of EXTRA_SYNONYMS[n] ?? []) out.add(x);
  return [...out];
}

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** First label sentence that mentions any alias of [other]; plain text, at most 300 characters. */
export function findMention(text: string, other: string): string | null {
  const res = aliases(other).map((a) => new RegExp(`(^|[^a-z])${escapeRe(a)}(s|es)?([^a-z]|$)`, "i"));
  const sentences = text.replace(/\s+/g, " ").match(/[^.!?]+[.!?]?/g) ?? [];
  for (const raw of sentences) {
    const sentence = raw.trim();
    if (sentence && res.some((r) => r.test(sentence))) {
      const plain = sentence.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
      return plain.length > 300 ? plain.slice(0, 297).trimEnd() + "..." : plain;
    }
  }
  return null;
}

async function interactionText(generic: string, f: F): Promise<string | null> {
  const hit = labelCache.get(generic);
  if (hit) return hit.text;
  const label = await fetchLabel(generic, f);
  const entry = { text: label ? (label.interactions ?? "") : null };
  if (label) labelCache.set(generic, entry); // do not cache failures: they may be transient
  return entry.text;
}

/** Ingredients from a comma-separated list; [group] says which list item each came from (combination products). */
export function parseNames(salts: string): { names: string[]; group: Map<string, number> } {
  const group = new Map<string, number>();
  const names: string[] = [];
  salts.split(",").forEach((item, i) => {
    for (const n of parseIngredients(item)) {
      if (!group.has(n)) { group.set(n, i); names.push(n); }
    }
  });
  return { names, group };
}

export async function handleInteractions(req: Request, env: Env = process.env as Env, f: F = fetch): Promise<Response> {
  if (env.APP_KEY && req.headers.get("x-app-key") !== env.APP_KEY) return json(401, { error: "unauthorized" });

  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "anon";
  if (!allow(ip)) return json(429, { error: "rate_limited" }, { "retry-after": "30" });

  const raw = new URL(req.url).searchParams.get("salts")?.trim();
  if (!raw || raw.length > 800) return json(400, { error: "salts query parameter required (comma separated generic names)" });
  const { names, group } = parseNames(raw);
  if (names.length < 2) return json(400, { error: "at least two generic names required" });
  if (names.length > MAX_NAMES) return json(400, { error: `at most ${MAX_NAMES} names` });

  const texts = new Map<string, string | null>();
  await Promise.all(names.map(async (n) => { texts.set(n, await interactionText(n, f)); }));
  const unresolved = names.filter((n) => texts.get(n) === null);

  const pairs: InteractionPair[] = [];
  for (let i = 0; i < names.length; i++) {
    for (let j = i + 1; j < names.length; j++) {
      const a = names[i]!, b = names[j]!;
      if (group.get(a) === group.get(b)) continue; // ingredients of one combination tablet
      for (const [from, other] of [[a, b], [b, a]] as const) {
        const text = texts.get(from);
        const snippet = text ? findMention(text, other) : null;
        if (snippet) {
          pairs.push({
            a, b, snippet,
            source: `openFDA / DailyMed label for ${from} (drug interactions)`,
            sourceUrl: `https://dailymed.nlm.nih.gov/dailymed/search.cfm?labeltype=all&query=${encodeURIComponent(US_NAMES[from] ?? from)}`,
          });
          break;
        }
      }
    }
  }
  return json(200, {
    pairs, unresolved,
    message: pairs.length === 0 ? NO_INTERACTION_MESSAGE : "",
    disclaimer: INTERACTION_DISCLAIMER,
  }, { "cache-control": "no-store" });
}
