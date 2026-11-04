import { budgetUsedPct, type BudgetEnv } from "./budget.js";

export const VERSION = "1.3.0";

export async function handleHealth(env: BudgetEnv & { VERCEL_GIT_COMMIT_SHA?: string } = process.env as BudgetEnv): Promise<Response> {
  const body = { ok: true, version: VERSION, budgetUsedPct: await budgetUsedPct(env) };
  return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}
