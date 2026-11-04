// Daily LLM spend guard. The count is an estimate from token usage and the per-token prices in env.
// The default store is per serverless instance (best effort); a shared store with the same interface can be plugged in.
// If the store fails, the guard fails open: a broken counter must never take the service down.

export type BudgetEnv = {
  DAILY_LLM_BUDGET_USD?: string;
  LLM_PRICE_IN_PER_MTOK_USD?: string;
  LLM_PRICE_OUT_PER_MTOK_USD?: string;
};

export interface BudgetStore {
  get(day: string): Promise<number>;
  add(day: string, usd: number): Promise<void>;
}

export class MemoryBudgetStore implements BudgetStore {
  private spent = new Map<string, number>();
  async get(day: string) { return this.spent.get(day) ?? 0; }
  async add(day: string, usd: number) {
    this.spent.set(day, (this.spent.get(day) ?? 0) + usd);
    if (this.spent.size > 3) for (const k of [...this.spent.keys()].sort().slice(0, -3)) this.spent.delete(k);
  }
  reset() { this.spent.clear(); }
}

let store: BudgetStore = new MemoryBudgetStore();
export function setBudgetStore(s: BudgetStore) { store = s; }
export function getBudgetStore(): BudgetStore { return store; }

export const utcDay = (now = Date.now()) => new Date(now).toISOString().slice(0, 10);
const num = (v: string | undefined, d: number) => { const n = Number(v); return v !== undefined && v !== "" && Number.isFinite(n) && n >= 0 ? n : d; };

export const dailyCapUsd = (env: BudgetEnv) => num(env.DAILY_LLM_BUDGET_USD, 5);

export function estimateCostUsd(inTokens: number, outTokens: number, env: BudgetEnv): number {
  return (inTokens * num(env.LLM_PRICE_IN_PER_MTOK_USD, 1) + outTokens * num(env.LLM_PRICE_OUT_PER_MTOK_USD, 5)) / 1_000_000;
}

/** Rough token count when the provider does not report usage. */
export const approxTokens = (chars: number) => Math.ceil(chars / 4);

export async function budgetUsedPct(env: BudgetEnv, now = Date.now(), s: BudgetStore = store): Promise<number> {
  try {
    const cap = dailyCapUsd(env);
    if (cap <= 0) return 100;
    return Math.min(100, Math.round(((await s.get(utcDay(now))) / cap) * 100));
  } catch { return 0; }
}

/** True when today's estimated spend has reached the cap. Fails open. */
export async function overBudget(env: BudgetEnv, now = Date.now(), s: BudgetStore = store): Promise<boolean> {
  try {
    return (await s.get(utcDay(now))) >= dailyCapUsd(env);
  } catch { return false; }
}

export async function recordSpend(env: BudgetEnv, inTokens: number, outTokens: number, now = Date.now(), s: BudgetStore = store): Promise<void> {
  try { await s.add(utcDay(now), estimateCostUsd(inTokens, outTokens, env)); } catch { /* fail open */ }
}

/** Seconds until the next UTC midnight, for the Retry-After hint. */
export function secondsToReset(now = Date.now()): number {
  const d = new Date(now);
  return Math.max(60, Math.ceil((Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1) - now) / 1000));
}

export function busyResponse(now = Date.now()): Response {
  const retryAfterSeconds = secondsToReset(now);
  return new Response(JSON.stringify({ error: "busy", retryAfterSeconds }), {
    status: 503, headers: { "content-type": "application/json", "cache-control": "no-store", "retry-after": String(retryAfterSeconds) },
  });
}
