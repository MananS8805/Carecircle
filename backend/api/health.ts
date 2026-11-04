import { handleHealth } from "../lib/health.js";

// Open on purpose: uptime checks need it. It returns no secrets and no user data.
export function GET(): Promise<Response> {
  return handleHealth();
}
