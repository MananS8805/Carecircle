// Turns OCR'd strip text or a user-typed salt into clean generic ingredient names.
// Indian strips print e.g. "Paracetamol IP 500 mg" or "Amoxicillin 500mg + Clavulanic Acid 125mg".

const PHARMACOPOEIA = /\b(i\.?p\.?|b\.?p\.?|u\.?s\.?p\.?|ph\.?\s?eur\.?|ip|bp|usp)\b\.?/gi;
const STRENGTH = /\b\d+([.,]\d+)?\s?(mg|mcg|µg|ug|g|ml|iu|%)\b(\s?\/\s?\d*\s?(ml|mg|g))?/gi;
const NOISE = /\b(tablets?|tabs?|capsules?|caps?|syrup|suspension|injection|film[- ]coated|sustained release|sr|er|xr|cr|dispersible|each|contains|composition|uncoated|coated)\b/gi;

export function cleanIngredient(raw: string): string {
  return raw
    .replace(STRENGTH, " ")
    .replace(PHARMACOPOEIA, " ")
    .replace(NOISE, " ")
    .replace(/[^a-zA-Z\s-]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

/** Split a combination ("A 500mg + B 125mg", "A & B", "A, B") into ingredients. */
export function parseIngredients(input: string): string[] {
  const parts = input.split(/\s*(?:\+|&|,|;|\band\b|\/)\s*/i);
  const out: string[] = [];
  for (const p of parts) {
    const c = cleanIngredient(p);
    if (c.length >= 3 && !out.includes(c)) out.push(c);
  }
  return out;
}
