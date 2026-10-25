// Week 2: insurance policy summary. Accepts OCR text ONLY after explicit in-app consent; never store the document.
export function POST(): Response {
  return new Response(JSON.stringify({ error: "not_implemented", planned: "week-2" }), {
    status: 501,
    headers: { "content-type": "application/json" },
  });
}
