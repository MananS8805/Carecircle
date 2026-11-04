import { allow } from "./ratelimit.js";
import { authorized, byteLength, secure, type HttpEnv } from "./http.js";
import { withLog } from "./log.js";

export interface PairingInfo {
  ownerUid: string;
  caregiverUid: string | null;
  caregiverFcmToken: string | null;
}

/** Everything that touches Firebase, so the rules of this endpoint can be tested without credentials. */
export interface NotifyDeps {
  verifyIdToken(token: string): Promise<{ uid: string } | null>;
  getPairing(linkId: string): Promise<PairingInfo | null>;
  sendData(token: string, data: Record<string, string>): Promise<"sent" | "unregistered" | "failed">;
  clearToken(linkId: string): Promise<void>;
}

type Env = HttpEnv & { FIREBASE_SERVICE_ACCOUNT_JSON?: string };

const MAX_BODY = 4_000;
const LINK_ID = /^[A-Z2-9]{8}$/;
const SLOT_ID = /^[A-Za-z0-9_.:-]{1,64}$/;
const ALLOWED_KEYS = ["linkId", "slotId", "slotLabel"];
export const PER_LINK_PER_HOUR = 6;

const json = (status: number, body: unknown, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store", ...extra } });

const sentSlots = new Map<string, number>(); // "linkId|slotId" -> when, kept in memory only
const DAY = 24 * 3600_000;
export function resetNotifyState() { sentSlots.clear(); }

export function parseNotifyBody(text: string): { linkId: string; slotId: string; slotLabel: string } | "bad" {
  let b: any;
  try { b = JSON.parse(text); } catch { return "bad"; }
  if (!b || typeof b !== "object" || Array.isArray(b)) return "bad";
  if (Object.keys(b).some((k) => !ALLOWED_KEYS.includes(k))) return "bad"; // no other fields, ever
  if (typeof b.linkId !== "string" || !LINK_ID.test(b.linkId)) return "bad";
  if (typeof b.slotId !== "string" || !SLOT_ID.test(b.slotId)) return "bad";
  if (typeof b.slotLabel !== "string" || b.slotLabel.length < 1 || b.slotLabel.length > 20) return "bad";
  return { linkId: b.linkId, slotId: b.slotId, slotLabel: b.slotLabel };
}

/**
 * The parent phone asks the server to wake the caregiver phone with a data-only push. The server checks the Firebase ID token,
 * that the caller owns the link, that a caregiver has claimed it and registered a token, then sends {type, slotLabel, linkId}
 * and nothing else. The caregiver phone builds the visible notification itself. Nothing here is stored or logged.
 */
export function handleNotifyMissed(req: Request, env: Env = process.env as Env, deps?: NotifyDeps): Promise<Response> {
  return withLog("notify-missed", async () => secure(await run(req, env, deps), req, env));
}

async function run(req: Request, env: Env, injected?: NotifyDeps): Promise<Response> {
  if (!authorized(req, env)) return json(401, { error: "unauthorized" });
  if (Number(req.headers.get("content-length") ?? 0) > MAX_BODY) return json(413, { error: "too_large" });
  const text = await req.text();
  if (byteLength(text) > MAX_BODY) return json(413, { error: "too_large" });
  const body = parseNotifyBody(text);
  if (body === "bad") return json(400, { error: "expected {linkId, slotId, slotLabel} and nothing else" });

  const auth = req.headers.get("authorization") ?? "";
  const token = /^Bearer\s+(\S+)$/.exec(auth)?.[1];
  if (!token) return json(401, { error: "unauthorized" });
  const deps = injected ?? (await realDeps(env));
  if (!deps) return json(503, { error: "push_not_configured" });

  const who = await deps.verifyIdToken(token).catch(() => null);
  if (!who) return json(401, { error: "unauthorized" });

  const link = await deps.getPairing(body.linkId).catch(() => null);
  // One answer for "no such link", "someone else's link" and "nobody has claimed it", so codes cannot be probed.
  if (!link || link.ownerUid !== who.uid || !link.caregiverUid) return json(403, { error: "forbidden" });
  if (!link.caregiverFcmToken) return json(409, { error: "no_token" });

  const slotKey = `${body.linkId}|${body.slotId}`;
  const now = Date.now();
  for (const [k, t] of sentSlots) if (now - t > DAY) sentSlots.delete(k);
  if (sentSlots.has(slotKey)) return json(429, { error: "already_sent" }, { "retry-after": "3600" });
  if (!allow(`notify|${body.linkId}`, { capacity: PER_LINK_PER_HOUR, refillPerSec: PER_LINK_PER_HOUR / 3600 })) {
    return json(429, { error: "rate_limited" }, { "retry-after": "600" });
  }

  const result = await deps.sendData(link.caregiverFcmToken, { type: "missed", slotLabel: body.slotLabel, linkId: body.linkId, slotId: body.slotId }).catch(() => "failed" as const);
  if (result === "unregistered") {
    await deps.clearToken(body.linkId).catch(() => undefined);
    return json(200, { sent: false, reason: "unregistered" });
  }
  if (result === "failed") return json(502, { error: "push_failed" });
  sentSlots.set(slotKey, now);
  return json(200, { sent: true });
}

let cached: NotifyDeps | null | undefined;

/** Firebase Admin, created on first use from FIREBASE_SERVICE_ACCOUNT_JSON. The credential is never logged. */
async function realDeps(env: Env): Promise<NotifyDeps | null> {
  if (cached !== undefined) return cached;
  const raw = env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) return (cached = null);
  try {
    const { initializeApp, cert, getApps } = await import("firebase-admin/app");
    const { getAuth } = await import("firebase-admin/auth");
    const { getFirestore, FieldValue } = await import("firebase-admin/firestore");
    const { getMessaging } = await import("firebase-admin/messaging");
    const app = getApps()[0] ?? initializeApp({ credential: cert(JSON.parse(raw)) });
    const db = getFirestore(app);
    cached = {
      async verifyIdToken(t) {
        try { return { uid: (await getAuth(app).verifyIdToken(t)).uid }; } catch { return null; }
      },
      async getPairing(linkId) {
        const snap = await db.collection("pairings").doc(linkId).get();
        if (!snap.exists) return null;
        const d = snap.data() ?? {};
        return { ownerUid: String(d.ownerUid ?? ""), caregiverUid: typeof d.caregiverUid === "string" ? d.caregiverUid : null, caregiverFcmToken: typeof d.caregiverFcmToken === "string" ? d.caregiverFcmToken : null };
      },
      async sendData(token, data) {
        try {
          await getMessaging(app).send({ token, data, android: { priority: "high", ttl: 3600_000 } });
          return "sent";
        } catch (e: any) {
          const code = String(e?.code ?? "");
          return code.includes("registration-token-not-registered") || code.includes("invalid-registration-token") ? "unregistered" : "failed";
        }
      },
      async clearToken(linkId) {
        await db.collection("pairings").doc(linkId).update({ caregiverFcmToken: FieldValue.delete(), tokenUpdatedAt: FieldValue.delete() });
      },
    };
  } catch {
    cached = null;
  }
  return cached ?? null;
}
