import { beforeEach, describe, expect, it, vi } from "vitest";
import { PER_LINK_PER_HOUR, handleNotifyMissed, parseNotifyBody, resetNotifyState, type NotifyDeps, type PairingInfo } from "../lib/notifyMissed.js";
import { resetRateLimit } from "../lib/ratelimit.js";

const LINK = "ABCD2345";
const active: PairingInfo = { ownerUid: "parent", caregiverUid: "carer", caregiverFcmToken: "tok-1" };

function deps(over: Partial<NotifyDeps> & { pairing?: PairingInfo | null } = {}): NotifyDeps & { sent: any[]; cleared: string[] } {
  const sent: any[] = [];
  const cleared: string[] = [];
  return {
    sent, cleared,
    verifyIdToken: over.verifyIdToken ?? (async (t) => (t === "good" ? { uid: "parent" } : null)),
    getPairing: over.getPairing ?? (async () => (over.pairing === undefined ? active : over.pairing)),
    sendData: over.sendData ?? (async (token, data) => { sent.push({ token, data }); return "sent"; }),
    clearToken: over.clearToken ?? (async (l) => { cleared.push(l); }),
  };
}

const post = (body: unknown, bearer: string | null = "good", headers: Record<string, string> = {}) =>
  new Request("https://x.test/notify", {
    method: "POST", body: typeof body === "string" ? body : JSON.stringify(body),
    headers: { ...(bearer ? { authorization: `Bearer ${bearer}` } : {}), ...headers },
  });
const ok = (extra: object = {}) => ({ linkId: LINK, slotId: "slot-1", slotLabel: "8:00 AM", ...extra });

beforeEach(() => { resetNotifyState(); resetRateLimit(); });

describe("body", () => {
  it("accepts exactly linkId, slotId and slotLabel", () => {
    expect(parseNotifyBody(JSON.stringify(ok()))).toEqual(ok());
  });
  it("rejects any other field, bad link ids, long slot ids and long labels", () => {
    for (const b of [ok({ medicine: "Metformin" }), ok({ linkId: "short" }), ok({ linkId: "abcd2345" }), ok({ slotId: "x".repeat(65) }), ok({ slotId: "has space" }), ok({ slotLabel: "" }), ok({ slotLabel: "x".repeat(21) }), [], "nope"]) {
      expect(parseNotifyBody(typeof b === "string" ? b : JSON.stringify(b)), JSON.stringify(b)).toBe("bad");
    }
  });
});

describe("authorisation", () => {
  it("401 for no token, a bad token, or the wrong app key", async () => {
    const d = deps();
    expect((await handleNotifyMissed(post(ok(), null), {}, d)).status).toBe(401);
    expect((await handleNotifyMissed(post(ok(), "bad"), {}, d)).status).toBe(401);
    expect((await handleNotifyMissed(post(ok(), "good"), { APP_KEY: "k" }, d)).status).toBe(401);
    expect((await handleNotifyMissed(post(ok(), "good", { "x-app-key": "k" }), { APP_KEY: "k" }, d)).status).toBe(200);
  });

  it("403 for a link that is someone else's, unknown, or not yet claimed, all with the same answer", async () => {
    const mine = (p: PairingInfo | null) => handleNotifyMissed(post(ok()), {}, deps({ pairing: p }));
    const a = await mine({ ...active, ownerUid: "other" });
    const b = await mine(null);
    const c = await mine({ ...active, caregiverUid: null });
    expect([a.status, b.status, c.status]).toEqual([403, 403, 403]);
    expect(await a.text()).toBe(await b.text());
  });

  it("409 when the caregiver has no push token", async () => {
    expect((await handleNotifyMissed(post(ok()), {}, deps({ pairing: { ...active, caregiverFcmToken: null } }))).status).toBe(409);
  });

  it("503 when push is not configured at all", async () => {
    expect((await handleNotifyMissed(post(ok()), {})).status).toBe(503);
  });
});

describe("sending", () => {
  it("sends a data-only message with exactly type, slotLabel and linkId", async () => {
    const d = deps();
    const res = await handleNotifyMissed(post(ok()), {}, d);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ sent: true });
    expect(d.sent).toEqual([{ token: "tok-1", data: { type: "missed", slotLabel: "8:00 AM", linkId: LINK } }]);
  });

  it("allows one push per slot and six per hour per link", async () => {
    const d = deps();
    expect((await handleNotifyMissed(post(ok()), {}, d)).status).toBe(200);
    expect((await handleNotifyMissed(post(ok()), {}, d)).status).toBe(429); // same slot again
    for (let i = 2; i <= PER_LINK_PER_HOUR; i++) expect((await handleNotifyMissed(post(ok({ slotId: `slot-${i}` })), {}, d)).status).toBe(200);
    expect((await handleNotifyMissed(post(ok({ slotId: "slot-99" })), {}, d)).status).toBe(429);
    expect(d.sent).toHaveLength(PER_LINK_PER_HOUR);
  });

  it("clears an unregistered token and does not count the slot as sent", async () => {
    const d = deps({ sendData: async () => "unregistered" });
    const res = await handleNotifyMissed(post(ok()), {}, d);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ sent: false, reason: "unregistered" });
    expect(d.cleared).toEqual([LINK]);
    const retry = await handleNotifyMissed(post(ok()), {}, deps());
    expect(retry.status).toBe(200);
  });

  it("502 on a provider failure, which can be retried", async () => {
    expect((await handleNotifyMissed(post(ok()), {}, deps({ sendData: async () => "failed" }))).status).toBe(502);
    expect((await handleNotifyMissed(post(ok()), {}, deps())).status).toBe(200);
  });

  it("never echoes what was sent in an error", async () => {
    const res = await handleNotifyMissed(post(ok({ linkId: "SECRETXX", medicine: "Metformin" })), {}, deps());
    expect(res.status).toBe(400);
    expect(await res.text()).not.toMatch(/SECRET|Metformin/);
  });

  it("is limited to 4 KB", async () => {
    expect((await handleNotifyMissed(post("x".repeat(5000)), {}, deps())).status).toBe(413);
  });

  it("calls no outside service when only fakes are given", async () => {
    const spy = vi.spyOn(globalThis, "fetch");
    await handleNotifyMissed(post(ok()), {}, deps());
    expect(spy).not.toHaveBeenCalled();
    spy.mockRestore();
  });
});
