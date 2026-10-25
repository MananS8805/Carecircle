import { beforeEach, describe, expect, it, vi } from "vitest";
import { handleMedicineInfo, clearCache } from "../lib/medicineInfo.js";
import { resetRateLimit, allow } from "../lib/ratelimit.js";
import { parseLlmJson } from "../lib/summarize.js";

const labelBody = {
  results: [{
    indications_and_usage: ["Temporarily relieves minor aches and pains. Reduces fever."],
    dosage_and_administration: ["Adults: 1 tablet every 6 hours with water."],
    adverse_reactions: ["Nausea. Rash."],
    warnings: ["Liver damage warning. Do not exceed 4 doses a day."],
    drug_interactions: ["Warfarin may interact."],
  }],
};

function mockFetch(opts: { label?: boolean } = { label: true }) {
  return vi.fn(async (url: any) => {
    const u = String(url);
    if (u.includes("rxcui.json")) return new Response(JSON.stringify({ idGroup: { rxnormId: ["161"] } }));
    if (u.includes("api.fda.gov")) return opts.label === false ? new Response("{}", { status: 404 }) : new Response(JSON.stringify(labelBody));
    if (u.includes("medlineplus")) return new Response("{}", { status: 404 });
    return new Response("{}", { status: 404 });
  }) as unknown as typeof fetch;
}
const req = (q: string, headers: Record<string, string> = {}) => new Request(`https://x.test/medicine-info?${q}`, { headers });

beforeEach(() => { clearCache(); resetRateLimit(); });

describe("medicine-info", () => {
  it("400 without salt", async () => {
    expect((await handleMedicineInfo(req(""), {}, mockFetch())).status).toBe(400);
  });
  it("returns extractive card when no LLM key, and maps paracetamol to US name", async () => {
    const f = mockFetch();
    const res = await handleMedicineInfo(req("salt=Paracetamol%20IP%20500%20mg"), {}, f);
    expect(res.status).toBe(200);
    const body: any = await res.json();
    expect(body.cards[0].generic).toBe("paracetamol");
    expect(body.cards[0].summarizedBy).toBe("extract");
    expect(body.cards[0].whyYouTakeIt).toContain("aches");
    expect(body.cards[0].disclaimer).toMatch(/not medical advice/i);
    expect(body.cards[0].sources.length).toBeGreaterThan(0);
    const fdaCall = (f as any).mock.calls.map((c: any[]) => String(c[0])).find((u: string) => u.includes("api.fda.gov"));
    expect(decodeURIComponent(fdaCall)).toContain("acetaminophen");
  });
  it("caches second call", async () => {
    const f = mockFetch();
    await handleMedicineInfo(req("salt=paracetamol"), {}, f);
    const n = (f as any).mock.calls.length;
    const res = await handleMedicineInfo(req("salt=paracetamol"), {}, f);
    expect(res.headers.get("x-cache")).toBe("HIT");
    expect((f as any).mock.calls.length).toBe(n);
  });
  it("404 when nothing resolves", async () => {
    const f = vi.fn(async () => new Response("{}", { status: 404 })) as unknown as typeof fetch;
    expect((await handleMedicineInfo(req("salt=zzzzqq"), {}, f)).status).toBe(404);
  });
  it("enforces APP_KEY", async () => {
    expect((await handleMedicineInfo(req("salt=paracetamol"), { APP_KEY: "k" }, mockFetch())).status).toBe(401);
    expect((await handleMedicineInfo(req("salt=paracetamol", { "x-app-key": "k" }), { APP_KEY: "k" }, mockFetch())).status).toBe(200);
  });
});

describe("helpers", () => {
  it("rate limiter blocks bursts then refills", () => {
    const o = { capacity: 2, refillPerSec: 1 };
    expect(allow("a", o, 0)).toBe(true);
    expect(allow("a", o, 0)).toBe(true);
    expect(allow("a", o, 0)).toBe(false);
    expect(allow("a", o, 2000)).toBe(true);
  });
  it("parseLlmJson tolerates prose around JSON and bad shapes", () => {
    const r = parseLlmJson('Sure! {"whyYouTakeIt":"pain","howToTake":"x","commonSideEffects":["a",1],"callDoctorIf":[],"keyInteractions":"no"}');
    expect(r?.commonSideEffects).toEqual(["a"]);
    expect(r?.keyInteractions).toEqual([]);
    expect(parseLlmJson("nope")).toBeNull();
  });
});
