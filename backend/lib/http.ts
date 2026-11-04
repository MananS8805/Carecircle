import { timingSafeEqual } from "node:crypto";

export type HttpEnv = { APP_KEY?: string; APP_KEY_NEXT?: string; ALLOWED_ORIGINS?: string };

/** Every request body is limited to this many bytes; larger ones get 413. */
export const MAX_BODY_BYTES = 200_000;

export const byteLength = (s: string) => new TextEncoder().encode(s).length;

function same(a: string, b: string): boolean {
  const ab = new TextEncoder().encode(a);
  const bb = new TextEncoder().encode(b);
  // Pad to one length so the comparison time does not depend on how many leading characters match.
  const len = Math.max(ab.length, bb.length, 1);
  const pa = new Uint8Array(len); pa.set(ab);
  const pb = new Uint8Array(len); pb.set(bb);
  return timingSafeEqual(pa, pb) && ab.length === bb.length;
}

/**
 * APP_KEY is the current key and APP_KEY_NEXT the one being rotated in: either is accepted, so a new key can be set on the
 * server first, shipped in the app, and the old key dropped later. With neither set the API is open (development only).
 */
export function authorized(req: Request, env: HttpEnv): boolean {
  const keys = [env.APP_KEY, env.APP_KEY_NEXT].filter((k): k is string => !!k);
  if (keys.length === 0) return true;
  const given = req.headers.get("x-app-key") ?? "";
  // Compare against every key so the time does not reveal which one matched.
  return keys.map((k) => same(given, k)).reduce((a, b) => a || b, false);
}

/**
 * Headers for every API response. No CORS headers unless the caller's Origin is in ALLOWED_ORIGINS (a comma-separated list):
 * the Android app does not need CORS, so by default browsers get nothing.
 */
export function secure(res: Response, req: Request | null, env: HttpEnv): Response {
  const h = new Headers(res.headers);
  h.set("x-content-type-options", "nosniff");
  h.set("referrer-policy", "no-referrer");
  h.set("x-frame-options", "DENY");
  h.set("content-security-policy", "default-src 'none'; frame-ancestors 'none'");
  h.set("strict-transport-security", "max-age=63072000; includeSubDomains");
  if (!h.has("cache-control")) h.set("cache-control", "no-store");
  const origin = req?.headers.get("origin");
  const allowed = (env.ALLOWED_ORIGINS ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  if (origin && allowed.includes(origin)) {
    h.set("access-control-allow-origin", origin);
    h.set("vary", "Origin");
  }
  return new Response(res.body, { status: res.status, statusText: res.statusText, headers: h });
}
