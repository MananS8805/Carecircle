import { beforeEach, describe, expect, it, vi } from "vitest";
import { authorized, byteLength, MAX_BODY_BYTES, secure } from "../lib/http.js";
import { handlePolicySummary } from "../lib/policySummary.js";
import { handlePolicyQuestion } from "../lib/policyQuestion.js";
import { handleMedicineInfo, clearCache } from "../lib/medicineInfo.js";
import { handleInteractions } from "../lib/interactions.js";
import { handleHealth } from "../lib/health.js";
import { resetRateLimit } from "../lib/ratelimit.js";

const nofetch = vi.fn() as unknown as typeof fetch;
const withKey = (key?: string, url = "https://x.test/p", body?: unknown) =>
  new Request(url, { method: body === undefined ? "GET" : "POST", body: body === undefined ? undefined : JSON.stringify(body), headers: key ? { "x-app-key": key } : {} });

beforeEach(() => { resetRateLimit(); clearCache(); });

describe("app key", () => {
  it("is open only when no key is configured", () => {
    expect(authorized(withKey(), {})).toBe(true);
    expect(authorized(withKey(), { APP_KEY: "a" })).toBe(false);
  });

  it("accepts the current key and the next key during a rotation, and nothing else", () => {
    const env = { APP_KEY: "old-key", APP_KEY_NEXT: "new-key" };
    expect(authorized(withKey("old-key"), env)).toBe(true);
    expect(authorized(withKey("new-key"), env)).toBe(true);
    expect(authorized(withKey("other"), env)).toBe(false);
    expect(authorized(withKey(), env)).toBe(false);
  });

  it("a prefix or a longer string of the key is not the key", () => {
    expect(authorized(withKey("old-ke"), { APP_KEY: "old-key" })).toBe(false);
    expect(authorized(withKey("old-keyX"), { APP_KEY: "old-key" })).toBe(false);
  });

  it("every endpoint answers 401 to a wrong key", async () => {
    const env = { APP_KEY: "k" };
    expect((await handlePolicySummary(withKey("bad", "https://x.test/", { pages: [] }), env, nofetch)).status).toBe(401);
    expect((await handlePolicyQuestion(withKey("bad", "https://x.test/", {}), env, nofetch)).status).toBe(401);
    expect((await handleMedicineInfo(withKey("bad", "https://x.test/?salt=a"), env, nofetch)).status).toBe(401);
    expect((await handleInteractions(withKey("bad", "https://x.test/?salts=a,b"), env, nofetch)).status).toBe(401);
  });
});

describe("body limit", () => {
  it("rejects bodies over 200 KB with 413 whatever the content", async () => {
    const big = "x".repeat(MAX_BODY_BYTES + 10);
    expect((await handlePolicySummary(withKey(undefined, "https://x.test/", { pages: [{ page: 1, text: big }] }), {}, nofetch)).status).toBe(413);
    expect((await handlePolicyQuestion(withKey(undefined, "https://x.test/", { question: "Is it covered?", clauses: [{ page: 1, text: big }] }), {}, nofetch)).status).toBe(413);
  });

  it("counts bytes, not characters", async () => {
    const hindi = "क".repeat(70_000); // 3 bytes each: 210,000 bytes in 70,000 characters
    expect(byteLength(hindi)).toBeGreaterThan(MAX_BODY_BYTES);
    expect((await handlePolicySummary(withKey(undefined, "https://x.test/", { pages: [{ page: 1, text: hindi }] }), {}, nofetch)).status).toBe(413);
  });
});

describe("headers", () => {
  const expectSecure = (r: Response) => {
    expect(r.headers.get("x-content-type-options")).toBe("nosniff");
    expect(r.headers.get("referrer-policy")).toBe("no-referrer");
    expect(r.headers.get("x-frame-options")).toBe("DENY");
    expect(r.headers.get("content-security-policy")).toContain("default-src 'none'");
    expect(r.headers.get("strict-transport-security")).toContain("max-age");
    expect(r.headers.get("access-control-allow-origin")).toBeNull();
  };

  it("are set on every endpoint, including errors", async () => {
    expectSecure(await handlePolicySummary(withKey("bad", "https://x.test/", { pages: [] }), { APP_KEY: "k" }, nofetch));
    expectSecure(await handlePolicyQuestion(withKey(undefined, "https://x.test/", "bad"), {}, nofetch));
    expectSecure(await handleMedicineInfo(withKey(undefined, "https://x.test/"), {}, nofetch));
    expectSecure(await handleInteractions(withKey(undefined, "https://x.test/"), {}, nofetch));
    expectSecure(await handleHealth({}));
  });

  it("add no CORS header by default, and only echo an origin that is listed", () => {
    const res = () => new Response("{}");
    const req = (origin: string) => new Request("https://x.test/", { headers: { origin } });
    expect(secure(res(), req("https://evil.test"), {}).headers.get("access-control-allow-origin")).toBeNull();
    expect(secure(res(), req("https://evil.test"), { ALLOWED_ORIGINS: "https://app.test" }).headers.get("access-control-allow-origin")).toBeNull();
    const ok = secure(res(), req("https://app.test"), { ALLOWED_ORIGINS: "https://other.test, https://app.test" });
    expect(ok.headers.get("access-control-allow-origin")).toBe("https://app.test");
    expect(ok.headers.get("vary")).toBe("Origin");
  });

  it("error bodies never echo what was sent", async () => {
    const res = await handlePolicySummary(withKey(undefined, "https://x.test/", { pages: [{ page: "x", text: "SECRET POLICY" }] }), {}, nofetch);
    expect(await res.text()).not.toContain("SECRET");
  });
});

describe("/health", () => {
  it("stays open even when keys are configured and shows no secrets", async () => {
    const res = await handleHealth({ APP_KEY: "do-not-show", APP_KEY_NEXT: "nor-this" });
    const text = await res.text();
    expect(res.status).toBe(200);
    expect(JSON.parse(text).ok).toBe(true);
    expect(text).not.toMatch(/do-not-show|nor-this/);
  });
});
