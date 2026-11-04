# Backend in production

Everything here is for the Vercel backend in `backend/`. Nothing in this file was done for you: deploys, keys and alerts are set in
the Vercel dashboard by the person who owns the project.

## Environment variables

| Name | Needed | What it does |
|---|---|---|
| `APP_KEY` | yes, in production | The key the app sends as `x-app-key`. With neither key set the API is open (development only) |
| `APP_KEY_NEXT` | during a rotation | A second key that is accepted too. See Rotating the app key |
| `ANTHROPIC_API_KEY` | optional | Policy summary and policy question use it. Without it they fall back to plain extraction and a pointer to the clauses |
| `LLM_MODEL` | optional | Anthropic model name (default `claude-haiku-4-5`) |
| `GEMINI_API_KEY`, `GEMINI_MODEL`, `LLM_PROVIDER` | optional | Medicine card rewriting only. Never given insurance text. The free Gemini tier may use prompts for training |
| `DAILY_LLM_BUDGET_USD` | recommended | Estimated daily spend cap (default 5). Past it the LLM endpoints answer 503 busy; cached cards keep working |
| `LLM_PRICE_IN_PER_MTOK_USD`, `LLM_PRICE_OUT_PER_MTOK_USD` | optional | Prices used for the estimate (defaults 1 and 5). Set them to your model's real prices |
| `ALLOWED_ORIGINS` | optional | Comma-separated browser origins that may call the API. Empty means no CORS header at all; the Android app does not need one |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | phase E | Used only by `/api/notify-missed` |

Secrets live in Vercel project settings, never in the repository, `.env` files or app builds. The app key in the Android app is a
speed bump, not a secret: anyone can extract it from the APK. The real protections are the rate limits, the daily budget and the
fact that no endpoint holds user data.

## Rotating the app key

1. Pick a new key. In Vercel set `APP_KEY_NEXT` to it and redeploy. Both keys now work.
2. Release the app with the new key (`careCircleAppKey` in `gradle.properties`) and wait until most users have updated.
3. In Vercel set `APP_KEY` to the new key and remove `APP_KEY_NEXT`. Old app versions now get 401 and show their offline message.

Keys are compared in constant time and never logged.

## Spend control

- The in-app estimate (`lib/budget.ts`) counts tokens from provider responses against `DAILY_LLM_BUDGET_USD`. The default store lives in
  each serverless instance, so under heavy load the cap is approximate and can be exceeded by roughly one instance's share. For a
  hard cap, pass a shared store with the same `get` and `add` methods to `setBudgetStore` (Vercel KV or Upstash Redis).
- Also set a monthly spend limit and an alert in the Anthropic and Google consoles. Those are the real ceilings.
- `GET /api/health` shows `budgetUsedPct` for the instance that answers. Point an uptime monitor at it.

## Logs

One JSON line per request: `endpoint`, `status`, `latencyMs`, `cacheHit`, `inTokens`, `outTokens`. No bodies, questions, document
text, names or IP addresses (`lib/log.ts` can write only those fields). Vercel keeps its own platform logs under its own policy.
Useful filters in the Vercel log view: `status:503` (budget reached), `status:429` (rate limited), high `latencyMs` on one endpoint.

## Limits

- Request bodies: 200 KB, counted in bytes (413 beyond that). Policy summaries accept 40 pages and 150,000 characters.
- Per IP, per minute: 5 policy summaries, 5 policy questions. Medicine cards and interaction checks have a larger burst allowance.
- Best-effort per instance, so also add Vercel firewall rate limits.

## Load test

```
BASE_URL=https://your-app.vercel.app APP_KEY=... npm run loadtest -- 60 5
```

It reads `/api/health` and one medicine card only. Run it against your own deployment, expect some 429 and no 5xx.

## Rolling back

Vercel keeps every deployment. In the dashboard, open Deployments, pick the last good one and choose Promote to Production. If a new
key was the problem, put the old value back in `APP_KEY` first. A bad `APP_KEY_NEXT` can simply be removed.

## Deploying

Not automated here. `cd backend && npm ci && npm test && npm run typecheck && npm run build:privacy`, then deploy from the Vercel
dashboard or `vercel --prod`. `backend/public/privacy.html` is served as a static page.
