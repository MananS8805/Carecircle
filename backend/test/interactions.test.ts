import { beforeEach, describe, expect, it, vi } from "vitest";
import { aliases, clearInteractionCache, findMention, handleInteractions, NO_INTERACTION_MESSAGE } from "../lib/interactions.js";
import { resetRateLimit } from "../lib/ratelimit.js";

const labels: Record<string, string[] | undefined> = {
  warfarin: ["Aspirin may increase bleeding risk with warfarin. Acetaminophen can raise INR when used regularly."],
  aspirin: ["Concomitant use with other NSAIDs increases bleeding."],
  acetaminophen: ["No specific interactions listed."],
  "ascorbic acid": ["Large doses may affect some laboratory tests."],
};

function mockFetch() {
  return vi.fn(async (url: any) => {
    const u = decodeURIComponent(String(url));
    if (u.includes("api.fda.gov")) {
      const name = /generic_name:"([^"]+)"/.exec(u)?.[1] ?? "";
      const di = labels[name];
      if (!di) return new Response("{}", { status: 404 });
      return new Response(JSON.stringify({ results: [{ drug_interactions: di }] }));
    }
    return new Response("{}", { status: 404 });
  }) as unknown as typeof fetch;
}
const req = (q: string, headers: Record<string, string> = {}) => new Request(`https://x.test/interactions?${q}`, { headers });

beforeEach(() => { clearInteractionCache(); resetRateLimit(); });

describe("interactions", () => {
  it("finds a cited label sentence for warfarin + aspirin", async () => {
    const res = await handleInteractions(req("salts=warfarin,aspirin"), {}, mockFetch());
    expect(res.status).toBe(200);
    const body: any = await res.json();
    expect(body.pairs).toHaveLength(1);
    expect(body.pairs[0].snippet).toMatch(/aspirin may increase bleeding/i);
    expect(body.pairs[0].source).toMatch(/warfarin/);
    expect(body.pairs[0].sourceUrl).toMatch(/^https:\/\/dailymed/);
    expect(body.message).toBe("");
  });

  it("matches synonyms: paracetamol is found as acetaminophen in the warfarin label", async () => {
    const body: any = await (await handleInteractions(req("salts=warfarin,paracetamol"), {}, mockFetch())).json();
    expect(body.pairs).toHaveLength(1);
    expect(body.pairs[0].snippet).toMatch(/acetaminophen/i);
    expect(aliases("paracetamol")).toContain("acetaminophen");
    expect(aliases("acetaminophen")).toContain("paracetamol");
  });

  it("returns the honest empty state when nothing is mentioned", async () => {
    const body: any = await (await handleInteractions(req("salts=paracetamol,vitamin%20c"), {}, mockFetch())).json();
    expect(body.pairs).toEqual([]);
    expect(body.message).toBe(NO_INTERACTION_MESSAGE);
  });

  it("reports names with no label as unresolved and invents nothing", async () => {
    const body: any = await (await handleInteractions(req("salts=warfarin,zzzzqq"), {}, mockFetch())).json();
    expect(body.pairs).toEqual([]);
    expect(body.unresolved).toEqual(["zzzzqq"]);
  });

  it("does not pair ingredients of the same combination", async () => {
    const q = "salts=" + encodeURIComponent("warfarin + aspirin") + ",paracetamol";
    const body: any = await (await handleInteractions(req(q), {}, mockFetch())).json();
    const titles = body.pairs.map((p: any) => [p.a, p.b].sort().join("+"));
    expect(titles).not.toContain("aspirin+warfarin");
  });

  it("limits to 8 names and needs at least 2", async () => {
    const nine = "alpha,bravo,charlie,delta,echo,foxtrot,golf,hotel,india";
    expect((await handleInteractions(req(`salts=${nine}`), {}, mockFetch())).status).toBe(400);
    const eight = "alpha,bravo,charlie,delta,echo,foxtrot,golf,hotel";
    expect((await handleInteractions(req(`salts=${eight}`), {}, mockFetch())).status).toBe(200);
    expect((await handleInteractions(req("salts=warfarin"), {}, mockFetch())).status).toBe(400);
    expect((await handleInteractions(req(""), {}, mockFetch())).status).toBe(400);
  });

  it("enforces APP_KEY", async () => {
    expect((await handleInteractions(req("salts=warfarin,aspirin"), { APP_KEY: "k" }, mockFetch())).status).toBe(401);
    expect((await handleInteractions(req("salts=warfarin,aspirin", { "x-app-key": "k" }), { APP_KEY: "k" }, mockFetch())).status).toBe(200);
  });

  it("rate limits bursts", async () => {
    const f = mockFetch();
    let last = 0;
    for (let i = 0; i < 25; i++) last = (await handleInteractions(req("salts=warfarin,aspirin", { "x-forwarded-for": "9.9.9.9" }), {}, f)).status;
    expect(last).toBe(429);
  });

  it("findMention returns plain text of at most 300 characters", () => {
    const long = "Warfarin " + "x".repeat(400) + " with aspirin <b>bold</b>.";
    const s = findMention(long, "aspirin")!;
    expect(s.length).toBeLessThanOrEqual(300);
    expect(s).not.toContain("<b>");
    expect(findMention("Nothing relevant here.", "aspirin")).toBeNull();
  });
});
