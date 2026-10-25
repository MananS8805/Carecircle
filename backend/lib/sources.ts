import type { LabelSections, Source } from "./types.js";

type F = typeof fetch;
const UA = { "User-Agent": "CareCircle/0.1 (informational medicine cards)" };

async function getJson(f: F, url: string, timeoutMs = 8000): Promise<any | null> {
  try {
    const r = await f(url, { headers: UA, signal: AbortSignal.timeout(timeoutMs) });
    if (!r.ok) return null;
    return await r.json();
  } catch { return null; }
}

/** RxNorm: resolve a generic name to an RxCUI (or undefined). */
export async function resolveRxcui(name: string, f: F = fetch): Promise<string | undefined> {
  const j = await getJson(f, `https://rxnav.nlm.nih.gov/REST/rxcui.json?name=${encodeURIComponent(name)}&search=2`);
  const id = j?.idGroup?.rxnormId?.[0];
  if (id) return String(id);
  const a = await getJson(f, `https://rxnav.nlm.nih.gov/REST/approximateTerm.json?term=${encodeURIComponent(name)}&maxEntries=1`);
  const c = a?.approximateGroup?.candidate?.[0]?.rxcui;
  return c ? String(c) : undefined;
}

const first = (v: unknown): string | undefined => (Array.isArray(v) && typeof v[0] === "string" ? v[0] : undefined);
const clip = (s: string | undefined, n = 1800) => (s ? s.replace(/\s+/g, " ").trim().slice(0, n) : undefined);

/** openFDA drug label (DailyMed-derived). US labels use US names (e.g. "acetaminophen" for paracetamol). */
export async function fetchLabel(generic: string, f: F = fetch): Promise<LabelSections | null> {
  const name = US_NAMES[generic] ?? generic;
  const q = encodeURIComponent(`openfda.generic_name:"${name}"`);
  const j = await getJson(f, `https://api.fda.gov/drug/label.json?search=${q}&limit=1`);
  const r = j?.results?.[0];
  if (!r) return null;
  return {
    indications: clip(first(r.indications_and_usage)),
    dosage: clip(first(r.dosage_and_administration)),
    adverse: clip(first(r.adverse_reactions)),
    warnings: clip(first(r.warnings) ?? first(r.warnings_and_cautions)),
    interactions: clip(first(r.drug_interactions)),
  };
}

/** MedlinePlus Connect: plain-language drug summary by RxCUI. */
export async function fetchMedlinePlus(rxcui: string, f: F = fetch): Promise<{ text: string; url?: string } | null> {
  const u = `https://connect.medlineplus.gov/service?mainSearchCriteria.v.cs=2.16.840.1.113883.6.88&mainSearchCriteria.v.c=${encodeURIComponent(rxcui)}&knowledgeResponseType=application/json`;
  const j = await getJson(f, u);
  const e = j?.feed?.entry?.[0];
  if (!e) return null;
  const html: string = e.summary?._value ?? "";
  const text = html.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim().slice(0, 1800);
  return text ? { text, url: e.link?.[0]?.href } : null;
}

// India uses INN names; US labels use USAN. Extend as needed.
export const US_NAMES: Record<string, string> = {
  paracetamol: "acetaminophen",
  salbutamol: "albuterol",
  adrenaline: "epinephrine",
  noradrenaline: "norepinephrine",
  frusemide: "furosemide",
  "glyceryl trinitrate": "nitroglycerin",
  rifampicin: "rifampin",
  lignocaine: "lidocaine",
  pethidine: "meperidine",
};

export function buildSources(rxcui: string | undefined, medline?: { url?: string } | null, hasLabel = false): Source[] {
  const s: Source[] = [];
  if (hasLabel) s.push({ name: "openFDA / DailyMed drug label", url: "https://open.fda.gov/apis/drug/label/" });
  if (rxcui) s.push({ name: "RxNorm (NLM)", url: `https://mor.nlm.nih.gov/RxNav/search?searchBy=RXCUI&searchTerm=${rxcui}` });
  if (medline) s.push({ name: "MedlinePlus", url: medline.url ?? "https://medlineplus.gov/druginformation.html" });
  return s;
}
