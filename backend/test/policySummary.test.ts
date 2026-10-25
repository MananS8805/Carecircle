import { beforeEach, describe, expect, it, vi } from "vitest";
import { MAX_CHARS, MAX_PAGES, handlePolicySummary, quoteVerified, verifySummary, type Page } from "../lib/policySummary.js";
import { resetRateLimit } from "../lib/ratelimit.js";

// Invented sample text (not a real policy).
const pages: Page[] = [
  {
    page: 1,
    text: [
      "Sample Health Shield - Policy Wording",
      "2.1 The Sum Insured under this policy is Rs. 5,00,000 per policy year.",
      "2.4 Co-payment of 10% applies to every claim for insured persons aged above 60 years.",
      "Claim helpline: toll free 1800 123 4567 (24x7).",
    ].join("\n"),
  },
  {
    page: 2,
    text: [
      "4.1 Initial waiting period: 30 days from the policy start date.",
      "4.2 Pre-existing diseases are covered after a waiting period of 36 months of continuous coverage.",
      "4.3 Cataract surgery has a waiting period of 24 months.",
      "5.1 Room rent is limited to 1% of the Sum Insured per day.",
      "6.1 Pre-hospitalisation expenses are covered for 60 days before admission.",
    ].join("\n"),
  },
];

const env = (extra: Record<string, string> = {}) => extra;
const post = (body: unknown, headers: Record<string, string> = {}) =>
  new Request("https://x.test/policy-summary", { method: "POST", body: typeof body === "string" ? body : JSON.stringify(body), headers });

const llmReply = (text: string) =>
  vi.fn(async () => new Response(JSON.stringify({ content: [{ type: "text", text }] }))) as unknown as typeof fetch;

beforeEach(() => resetRateLimit());

describe("quote verification", () => {
  it("accepts a quote that appears on the cited page (case and whitespace insensitive)", () => {
    expect(quoteVerified("the SUM insured under this   policy is rs. 5,00,000", 1, pages)).toBe(true);
  });
  it("rejects a quote that is not on the cited page, or too short, or on another page", () => {
    expect(quoteVerified("Sum Insured is Rs. 50,00,000", 1, pages)).toBe(false);
    expect(quoteVerified("Initial waiting period: 30 days", 1, pages)).toBe(false);
    expect(quoteVerified("short", 1, pages)).toBe(false);
    expect(quoteVerified("Initial waiting period: 30 days", 9, pages)).toBe(false);
  });
  it("drops a fabricated item and reports its field in unverified", () => {
    const raw = {
      sumInsured: { value: "Rs. 5,00,000", page: 1, clause: "2.1", quote: "The Sum Insured under this policy is Rs. 5,00,000" },
      coPay: { value: "20%", page: 1, clause: "2.4", quote: "Co-payment of 20% applies to every claim" },
      waitingSpecific: [{ illness: "Cataract", period: "24 months", page: 2, clause: "4.3", quote: "Cataract surgery has a waiting period of 24 months" },
        { illness: "Hernia", period: "12 months", page: 2, clause: "4.9", quote: "Hernia surgery has a waiting period of 12 months" }],
      exclusions: [{ value: "Cosmetic surgery", page: 2, clause: null, quote: "Cosmetic surgery is never covered" }],
    };
    const { summary, unverified } = verifySummary(raw, pages);
    expect(summary.sumInsured?.value).toBe("Rs. 5,00,000");
    expect(summary.coPay).toBeNull();
    expect(summary.waitingSpecific).toHaveLength(1);
    expect(summary.exclusions).toEqual([]);
    expect(unverified.sort()).toEqual(["coPay", "exclusions", "waitingSpecific"]);
  });
});

const listPages: Page[] = [
  {
    page: 1,
    text: [
      "5. WHAT IS COVERED",
      "Inclusions",
      "5.1 In-patient hospitalisation for at least 24 hours",
      "5.2 Day care procedures such as cataract surgery",
      "- Ambulance charges up to Rs. 2,000 per claim",
    ].join("\n"),
  },
  {
    page: 2,
    text: [
      "Exclusions",
      "7.1 Cosmetic or plastic surgery unless needed after an accident",
      "7.2 Dental treatment, unless caused by an accident",
      "(a) Infertility and assisted conception treatment",
      "Claim Procedure",
      "8.1 Intimate the insurer within 24 hours of hospitalisation",
    ].join("\n"),
  },
];

describe("covered and not covered lists (extractive)", () => {
  it("reads inclusion and exclusion bullets with page, clause and an exact quote", async () => {
    const b: any = await (await handlePolicySummary(post({ pages: listPages }), env(), vi.fn() as unknown as typeof fetch)).json();
    expect(b.mode).toBe("extractive");
    expect(b.summary.inclusions.map((i: any) => i.page)).toEqual([1, 1, 1]);
    expect(b.summary.inclusions[0].clause).toBe("5.1");
    expect(b.summary.inclusions[0].value).toMatch(/In-patient hospitalisation/);
    expect(b.summary.exclusions).toHaveLength(3);
    expect(b.summary.exclusions[0]).toMatchObject({ page: 2, clause: "7.1" });
    expect(b.summary.exclusions[2].clause).toBe("(a)");
    expect(b.unverified).toEqual([]);
  });

  it("stops at the next heading, so claim-procedure lines are not exclusions", async () => {
    const b: any = await (await handlePolicySummary(post({ pages: listPages }), env(), vi.fn() as unknown as typeof fetch)).json();
    expect(b.summary.exclusions.some((e: any) => /Intimate the insurer/.test(e.value))).toBe(false);
  });

  it("returns empty lists when the text has no such sections", async () => {
    const b: any = await (await handlePolicySummary(post({ pages }), env(), vi.fn() as unknown as typeof fetch)).json();
    expect(b.summary.exclusions).toEqual([]);
    expect(b.summary.inclusions).toEqual([]);
  });
});

describe("handler", () => {
  it("extractive fallback (no LLM key) finds the headline items with pages", async () => {
    const res = await handlePolicySummary(post({ pages }), env(), vi.fn() as unknown as typeof fetch);
    expect(res.status).toBe(200);
    const b: any = await res.json();
    expect(b.mode).toBe("extractive");
    expect(b.disclaimer).toBe("Informational only. Verify with your insurer.");
    expect(b.summary.sumInsured.value).toMatch(/5,00,000/);
    expect(b.summary.sumInsured.page).toBe(1);
    expect(b.summary.coPay.value).toBe("10%");
    expect(b.summary.claimHelpline.value).toMatch(/1800/);
    expect(b.summary.waitingInitial.page).toBe(2);
    expect(b.summary.waitingPreExisting.page).toBe(2);
    expect(b.summary.waitingSpecific[0].illness).toBe("cataract");
    expect(b.unverified).toEqual([]);
  });

  it("uses the LLM answer but still verifies quotes", async () => {
    const f = llmReply(JSON.stringify({
      sumInsured: { value: "Rs. 5,00,000", page: 1, clause: "2.1", quote: "The Sum Insured under this policy is Rs. 5,00,000 per policy year" },
      tpa: { value: "Made Up TPA", page: 1, clause: null, quote: "Claims are handled by Made Up TPA Services" },
    }));
    const b: any = await (await handlePolicySummary(post({ pages }), env({ ANTHROPIC_API_KEY: "k" }), f)).json();
    expect(b.mode).toBe("llm");
    expect(b.summary.sumInsured.page).toBe(1);
    expect(b.summary.tpa).toBeNull();
    expect(b.unverified).toEqual(["tpa"]);
  });

  it("retries malformed LLM JSON once, then falls back to extractive", async () => {
    const f = llmReply("sorry, here you go: not json");
    const b: any = await (await handlePolicySummary(post({ pages }), env({ ANTHROPIC_API_KEY: "k" }), f)).json();
    expect((f as any).mock.calls.length).toBe(2);
    expect(b.mode).toBe("extractive");
    expect(b.summary.sumInsured.value).toMatch(/5,00,000/);
  });

  it("rejects too many pages and too many characters with 413", async () => {
    const many = Array.from({ length: MAX_PAGES + 1 }, (_, i) => ({ page: i + 1, text: "x" }));
    expect((await handlePolicySummary(post({ pages: many }), env(), fetch)).status).toBe(413);
    const big = [{ page: 1, text: "a".repeat(MAX_CHARS + 1) }];
    expect((await handlePolicySummary(post({ pages: big }), env(), fetch)).status).toBe(413);
  });

  it("400 for bad bodies, and errors never echo the policy text", async () => {
    expect((await handlePolicySummary(post("not json"), env(), fetch)).status).toBe(400);
    const res = await handlePolicySummary(post({ pages: [{ page: "one", text: "SECRET POLICY TEXT" }] }), env(), fetch);
    expect(res.status).toBe(400);
    expect(await res.text()).not.toContain("SECRET");
  });

  it("enforces APP_KEY", async () => {
    expect((await handlePolicySummary(post({ pages }), { APP_KEY: "k" }, fetch)).status).toBe(401);
    expect((await handlePolicySummary(post({ pages }, { "x-app-key": "k" }), { APP_KEY: "k" }, fetch)).status).toBe(200);
  });

  it("allows 5 requests per minute per IP", async () => {
    const h = { "x-forwarded-for": "7.7.7.7" };
    const codes: number[] = [];
    for (let i = 0; i < 6; i++) codes.push((await handlePolicySummary(post({ pages }, h), env(), fetch)).status);
    expect(codes).toEqual([200, 200, 200, 200, 200, 429]);
  });
});
