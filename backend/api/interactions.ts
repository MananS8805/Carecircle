import { handleInteractions } from "../lib/interactions.js";

export function GET(request: Request): Promise<Response> {
  return handleInteractions(request);
}
