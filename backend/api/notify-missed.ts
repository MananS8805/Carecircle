import { handleNotifyMissed } from "../lib/notifyMissed.js";

// Wakes the caregiver phone about a missed dose. Needs a Firebase ID token; stores and logs nothing about the request.
export function POST(request: Request): Promise<Response> {
  return handleNotifyMissed(request);
}
