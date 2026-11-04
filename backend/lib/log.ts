// One structured JSON line per request. Only the fields below can ever be written: no bodies, questions, OCR text,
// medicine or person names, and no IP addresses. The type is the guard, and a test checks the output keys.

export interface RequestLog {
  endpoint: string;
  status: number;
  latencyMs: number;
  cacheHit: boolean;
  inTokens: number;
  outTokens: number;
}

export const LOG_KEYS = ["endpoint", "status", "latencyMs", "cacheHit", "inTokens", "outTokens"] as const;

export function formatLog(e: RequestLog): string {
  const clean: Record<string, unknown> = { t: new Date().toISOString() };
  for (const k of LOG_KEYS) clean[k] = (e as unknown as Record<string, unknown>)[k];
  return JSON.stringify(clean);
}

type Sink = (line: string) => void;
let sink: Sink = (line) => console.log(line);
export function setLogSink(s: Sink) { sink = s; }

export function logRequest(e: RequestLog) {
  try { sink(formatLog(e)); } catch { /* logging never breaks a request */ }
}

/** Wraps a handler so every response is logged with its latency. Token counts come from [usage]. */
export async function withLog(endpoint: string, run: (usage: { inTokens: number; outTokens: number; cacheHit: boolean }) => Promise<Response>): Promise<Response> {
  const started = Date.now();
  const usage = { inTokens: 0, outTokens: 0, cacheHit: false };
  let res: Response;
  try {
    res = await run(usage);
  } catch (e) {
    logRequest({ endpoint, status: 500, latencyMs: Date.now() - started, cacheHit: false, inTokens: usage.inTokens, outTokens: usage.outTokens });
    throw e;
  }
  logRequest({ endpoint, status: res.status, latencyMs: Date.now() - started, cacheHit: usage.cacheHit || res.headers.get("x-cache") === "HIT", inTokens: usage.inTokens, outTokens: usage.outTokens });
  return res;
}
