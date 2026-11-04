import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryBudgetStore, busyResponse, estimateCostUsd, getBudgetStore, overBudget, recordSpend, budgetUsedPct, secondsToReset, utcDay } from "../lib/budget.js";
import { LOG_KEYS, formatLog, logRequest, setLogSink, withLog } from "../lib/log.js";
import { handleHealth } from "../lib/health.js";
import { handlePolicySummary } from "../lib/policySummary.js";
import { handleMedicineInfo, clearCache } from "../lib/medicineInfo.js";
import { resetRateLimit } from "../lib/ratelimit.js";

beforeEach(() => { (getBudgetStore() as MemoryBudgetStore).reset(); resetRateLimit(); clearCache(); });

describe("budget", () => {
  it("estimates cost from token prices in env", () => {
    expect(estimateCostUsd(1_000_000, 1_000_000, {})).toBeCloseTo(6);
    expect(estimateCostUsd(1000, 100, { LLM_PRICE_IN_PER_MTOK_USD: "10", LLM_PRICE_OUT_PER_MTOK_USD: "20" })).toBeCloseTo(0.012);
  });

  it("blocks once the cap is reached and resets on the next UTC day", async () => {
    const env = { DAILY_LLM_BUDGET_USD: "1" };
    const t0 = Date.UTC(2026, 10, 4, 10);
    expect(await overBudget(env, t0)).toBe(false);
    await recordSpend(env, 200_000, 160_000, t0); // 0.2 + 0.8 = 1.0
    expect(await overBudget(env, t0)).toBe(true);
    expect(await budgetUsedPct(env, t0)).toBe(100);
    expect(await overBudget(env, t0 + 24 * 3600_000)).toBe(false);
    expect(utcDay(t0)).toBe("2026-11-04");
  });

  it("fails open when the store is down", async () => {
    const broken = { get: async () => { throw new Error("down"); }, add: async () => { throw new Error("down"); } };
    expect(await overBudget({}, Date.now(), broken)).toBe(false);
    await expect(recordSpend({}, 10, 10, Date.now(), broken)).resolves.toBeUndefined();
    expect(await budgetUsedPct({}, Date.now(), broken)).toBe(0);
  });

  it("busy response carries a retry hint of at least a minute", async () => {
    const r = busyResponse(Date.UTC(2026, 10, 4, 23, 59, 59));
    expect(r.status).toBe(503);
    expect(((await r.json()) as any).retryAfterSeconds).toBeGreaterThanOrEqual(60);
    expect(secondsToReset(Date.UTC(2026, 10, 4, 12))).toBe(12 * 3600);
  });

  it("at the cap the LLM policy endpoint is busy but the extractive path and cached cards keep working", async () => {
    const env = { DAILY_LLM_BUDGET_USD: "0" };
    const pages = [{ page: 1, text: "The Sum Insured under this policy is Rs. 5,00,000 per policy year." }];
    const req = () => new Request("https://x.test/p", { method: "POST", body: JSON.stringify({ pages }) });
    expect((await handlePolicySummary(req(), { ...env, ANTHROPIC_API_KEY: "k" }, vi.fn() as unknown as typeof fetch)).status).toBe(503);
    expect((await handlePolicySummary(req(), env, vi.fn() as unknown as typeof fetch)).status).toBe(200); // no key: free path

    const f = vi.fn(async (url: any) => {
      const u = String(url);
      if (u.includes("rxcui.json")) return new Response(JSON.stringify({ idGroup: { rxnormId: ["1"] } }));
      if (u.includes("api.fda.gov")) return new Response(JSON.stringify({ results: [{ indications_and_usage: ["Relieves pain."] }] }));
      return new Response("{}", { status: 404 });
    }) as unknown as typeof fetch;
    const med = () => new Request("https://x.test/medicine-info?salt=paracetamol");
    const first = await handleMedicineInfo(med(), { ...env, GEMINI_API_KEY: "g" }, f);
    expect(first.status).toBe(200);
    expect(((await first.json()) as any).cards[0].summarizedBy).toBe("extract"); // LLM skipped at the cap
    const second = await handleMedicineInfo(med(), { ...env, GEMINI_API_KEY: "g" }, f);
    expect(second.headers.get("x-cache")).toBe("HIT");
  });
});

describe("log", () => {
  it("only ever writes the allowed fields, whatever is passed in", () => {
    const line = formatLog({ endpoint: "x", status: 200, latencyMs: 5, cacheHit: false, inTokens: 1, outTokens: 2, question: "is my aspirin covered", body: "SECRET", ip: "1.2.3.4" } as any);
    const parsed = JSON.parse(line);
    expect(Object.keys(parsed).sort()).toEqual(["t", ...LOG_KEYS].sort());
    expect(line).not.toMatch(/SECRET|aspirin|1\.2\.3\.4/);
  });

  it("withLog records status, latency and cache hits through the sink", async () => {
    const lines: string[] = [];
    setLogSink((l) => lines.push(l));
    await withLog("demo", async (u) => { u.cacheHit = true; u.inTokens = 7; return new Response("{}", { status: 201 }); });
    logRequest({ endpoint: "direct", status: 200, latencyMs: 1, cacheHit: false, inTokens: 0, outTokens: 0 });
    setLogSink(() => {});
    expect(JSON.parse(lines[0]!)).toMatchObject({ endpoint: "demo", status: 201, cacheHit: true, inTokens: 7 });
    expect(lines).toHaveLength(2);
  });
});

describe("health", () => {
  it("is open, has no secrets and reports budget use", async () => {
    await recordSpend({ DAILY_LLM_BUDGET_USD: "2" }, 200_000, 160_000); // 1.0 of 2
    const res = await handleHealth({ DAILY_LLM_BUDGET_USD: "2", ANTHROPIC_API_KEY: "never-shown" } as any);
    const text = await res.text();
    expect(JSON.parse(text)).toMatchObject({ ok: true, budgetUsedPct: 50 });
    expect(text).not.toContain("never-shown");
  });
});
