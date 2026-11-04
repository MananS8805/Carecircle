import { approxTokens, recordSpend, type BudgetEnv } from "./budget.js";

type F = typeof fetch;
export type LlmCallEnv = BudgetEnv & { ANTHROPIC_API_KEY?: string; LLM_MODEL?: string };
export interface LlmResult { text: string; inTokens: number; outTokens: number }

/** One Anthropic call. Records the estimated spend; returns null on any failure so callers can fall back. */
export async function callAnthropicText(
  prompt: string, env: LlmCallEnv, f: F, opts: { maxTokens: number; timeoutMs: number },
): Promise<LlmResult | null> {
  if (!env.ANTHROPIC_API_KEY) return null;
  try {
    const r = await f("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: { "content-type": "application/json", "x-api-key": env.ANTHROPIC_API_KEY, "anthropic-version": "2023-06-01" },
      body: JSON.stringify({ model: env.LLM_MODEL ?? "claude-haiku-4-5", max_tokens: opts.maxTokens, temperature: 0, messages: [{ role: "user", content: prompt }] }),
      signal: AbortSignal.timeout(opts.timeoutMs),
    });
    if (!r.ok) return null;
    const j: any = await r.json();
    const text = j?.content?.[0]?.text;
    if (typeof text !== "string") return null;
    const inTokens = Number.isFinite(j?.usage?.input_tokens) ? j.usage.input_tokens : approxTokens(prompt.length);
    const outTokens = Number.isFinite(j?.usage?.output_tokens) ? j.usage.output_tokens : approxTokens(text.length);
    await recordSpend(env, inTokens, outTokens);
    return { text, inTokens, outTokens };
  } catch { return null; }
}
