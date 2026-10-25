import { parseIngredients } from "./normalize.js";
import { buildSources, fetchLabel, fetchMedlinePlus, resolveRxcui } from "./sources.js";
import { assembleCard, buildPrompt, extractiveCard, summarizeWithLlm, type LlmEnv } from "./summarize.js";
import { TtlCache } from "./cache.js";
import { allow } from "./ratelimit.js";
import type { MedicineCard } from "./types.js";

type Env = LlmEnv & { APP_KEY?: string };
const cache = new TtlCache<{ cards: MedicineCard[]; unresolved: string[] }>(6 * 60 * 60 * 1000);
export function clearCache() { (cache as any).m.clear(); }

const json = (status: number, body: unknown, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...extra } });

export async function handleMedicineInfo(req: Request, env: Env = process.env as Env, f: typeof fetch = fetch): Promise<Response> {
  if (env.APP_KEY && req.headers.get("x-app-key") !== env.APP_KEY) return json(401, { error: "unauthorized" });

  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "anon";
  if (!allow(ip)) return json(429, { error: "rate_limited" }, { "retry-after": "30" });

  const salt = new URL(req.url).searchParams.get("salt")?.trim();
  if (!salt || salt.length > 200) return json(400, { error: "salt query parameter required (max 200 chars)" });

  const ingredients = parseIngredients(salt).slice(0, 4);
  if (ingredients.length === 0) return json(422, { error: "could_not_parse_salt" });

  const key = ingredients.join("|");
  const hit = cache.get(key);
  if (hit) return json(200, hit, { "x-cache": "HIT", "cache-control": "public, s-maxage=86400" });

  const cards: MedicineCard[] = [];
  const unresolved: string[] = [];
  for (const generic of ingredients) {
    const [rxcui, label] = await Promise.all([resolveRxcui(generic, f), fetchLabel(generic, f)]);
    const medline = rxcui ? await fetchMedlinePlus(rxcui, f) : null;
    if (!label && !medline) { unresolved.push(generic); continue; }
    const sections = label ?? {};
    const sources = buildSources(rxcui, medline, !!label);
    const llm = await summarizeWithLlm(buildPrompt(generic, sections, medline?.text), env, f);
    const body = llm ?? extractiveCard(generic, sections, medline?.text);
    cards.push(assembleCard(generic, rxcui, body, sources, llm ? "llm" : "extract"));
  }
  const result = { cards, unresolved };
  if (cards.length > 0) cache.set(key, result);
  return json(cards.length ? 200 : 404, result, { "x-cache": "MISS", "cache-control": cards.length ? "public, s-maxage=86400" : "no-store" });
}
