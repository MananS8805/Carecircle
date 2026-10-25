import { handlePolicySummary } from "../lib/policySummary.js";

// Accepts OCR text ONLY after explicit in-app consent. The request and the result are never stored or logged.
export function POST(request: Request): Promise<Response> {
  return handlePolicySummary(request);
}
