// Best-effort per-instance token bucket. Serverless instances are ephemeral, so this only
// blunts bursts. Real protection = APP_KEY + Vercel firewall rate limits (see README).
type Bucket = { tokens: number; updated: number };
const buckets = new Map<string, Bucket>();

export function allow(key: string, opts = { capacity: 20, refillPerSec: 0.2 }, now = Date.now()): boolean {
  const b = buckets.get(key) ?? { tokens: opts.capacity, updated: now };
  const elapsed = (now - b.updated) / 1000;
  b.tokens = Math.min(opts.capacity, b.tokens + elapsed * opts.refillPerSec);
  b.updated = now;
  const ok = b.tokens >= 1;
  if (ok) b.tokens -= 1;
  buckets.set(key, b);
  if (buckets.size > 5000) buckets.clear();
  return ok;
}

export function resetRateLimit() { buckets.clear(); }
