import { budgetUsedPct, type BudgetEnv } from "./budget.js";
import { secure, type HttpEnv } from "./http.js";

export const VERSION = "1.9.0";

export async function handleHealth(env: BudgetEnv & HttpEnv = process.env as BudgetEnv): Promise<Response> {
  const body = { ok: true, version: VERSION, budgetUsedPct: await budgetUsedPct(env) };
  return secure(new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json", "cache-control": "no-store" } }), null, env);
}
