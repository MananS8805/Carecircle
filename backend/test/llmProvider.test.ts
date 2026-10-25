import { describe, expect, it, vi } from "vitest";
import { pickProvider, summarizeWithLlm } from "../lib/summarize.js";

const card = { whyYouTakeIt: "pain relief", howToTake: "with water", commonSideEffects: ["nausea"], callDoctorIf: [], keyInteractions: [] };

describe("provider choice", () => {
  it("is none without keys", () => expect(pickProvider({})).toBeNull());
  it("prefers Gemini when its key exists, else Anthropic", () => {
    expect(pickProvider({ GEMINI_API_KEY: "g", ANTHROPIC_API_KEY: "a" })).toBe("gemini");
    expect(pickProvider({ ANTHROPIC_API_KEY: "a" })).toBe("anthropic");
  });
  it("honours LLM_PROVIDER and refuses a provider without its key", () => {
    expect(pickProvider({ LLM_PROVIDER: "anthropic", GEMINI_API_KEY: "g", ANTHROPIC_API_KEY: "a" })).toBe("anthropic");
    expect(pickProvider({ LLM_PROVIDER: "gemini", ANTHROPIC_API_KEY: "a" })).toBeNull();
  });
});

describe("Gemini call", () => {
  it("sends the key in a header (not the URL), asks for JSON and parses the reply", async () => {
    const f = vi.fn(async () => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(card) }] } }] }))) as unknown as typeof fetch;
    const out = await summarizeWithLlm("PROMPT", { GEMINI_API_KEY: "secret-key" }, f);
    expect(out?.whyYouTakeIt).toBe("pain relief");
    const [url, init] = (f as any).mock.calls[0];
    expect(String(url)).toContain("gemini-2.5-flash:generateContent");
    expect(String(url)).not.toContain("secret-key");
    expect(init.headers["x-goog-api-key"]).toBe("secret-key");
    expect(JSON.parse(init.body).generationConfig.responseMimeType).toBe("application/json");
  });

  it("returns null (so the plain label extract is used) on errors or bad JSON", async () => {
    const bad = vi.fn(async () => new Response("no", { status: 429 })) as unknown as typeof fetch;
    expect(await summarizeWithLlm("p", { GEMINI_API_KEY: "k" }, bad)).toBeNull();
    const junk = vi.fn(async () => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "not json" }] } }] }))) as unknown as typeof fetch;
    expect(await summarizeWithLlm("p", { GEMINI_API_KEY: "k" }, junk)).toBeNull();
    const boom = vi.fn(async () => { throw new Error("network"); }) as unknown as typeof fetch;
    expect(await summarizeWithLlm("p", { GEMINI_API_KEY: "k" }, boom)).toBeNull();
  });

  it("makes no call when no key is configured", async () => {
    const f = vi.fn() as unknown as typeof fetch;
    expect(await summarizeWithLlm("p", {}, f)).toBeNull();
    expect((f as any).mock.calls.length).toBe(0);
  });
});
