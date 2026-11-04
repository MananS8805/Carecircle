// Small load test with no dependencies. It checks that the rate limit and the daily budget behave under pressure and reports
// latency percentiles. Point it at a deployment you own, never at someone else's.
//
//   BASE_URL=https://your-app.vercel.app APP_KEY=... node scripts/loadtest.mjs [requests] [concurrency]
//
// It only calls GET /api/health and GET /api/medicine-info for one common ingredient, so no personal data is involved.

const base = (process.env.BASE_URL ?? "").replace(/\/$/, "");
if (!base) { console.error("Set BASE_URL, for example BASE_URL=https://your-app.vercel.app"); process.exit(2); }
const total = Number(process.argv[2] ?? 60);
const workers = Math.min(Number(process.argv[3] ?? 5), 20);
const headers = process.env.APP_KEY ? { "x-app-key": process.env.APP_KEY } : {};

const paths = ["/api/health", "/api/medicine-info?salt=paracetamol"];
const times = [];
const codes = new Map();
let next = 0;

async function worker() {
  while (next < total) {
    const i = next++;
    const path = paths[i % paths.length];
    const t0 = performance.now();
    try {
      const r = await fetch(base + path, { headers });
      codes.set(r.status, (codes.get(r.status) ?? 0) + 1);
      await r.arrayBuffer();
    } catch {
      codes.set("error", (codes.get("error") ?? 0) + 1);
    }
    times.push(performance.now() - t0);
  }
}

const started = performance.now();
await Promise.all(Array.from({ length: workers }, worker));
const secs = (performance.now() - started) / 1000;
times.sort((a, b) => a - b);
const pct = (p) => Math.round(times[Math.min(times.length - 1, Math.floor((p / 100) * times.length))] ?? 0);
console.log(`${total} requests, ${workers} at a time, ${secs.toFixed(1)} s (${(total / secs).toFixed(1)} req/s)`);
console.log(`status codes: ${[...codes].map(([k, v]) => `${k}=${v}`).join("  ")}`);
console.log(`latency ms: p50=${pct(50)} p90=${pct(90)} p99=${pct(99)} max=${Math.round(times.at(-1) ?? 0)}`);
console.log("Expect some 429 once the per-IP limit is reached, and 503 only after the daily budget is spent.");
