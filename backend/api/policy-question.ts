import { handlePolicyQuestion } from "../lib/policyQuestion.js";

// Accepts clause text and a question ONLY after explicit in-app consent. Neither is stored, cached or logged.
export function POST(request: Request): Promise<Response> {
  return handlePolicyQuestion(request);
}
