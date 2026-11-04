import { setLogSink } from "../lib/log.js";

// Handlers log one line per request; keep test output readable.
setLogSink(() => {});
