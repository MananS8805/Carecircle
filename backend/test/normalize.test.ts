import { describe, expect, it } from "vitest";
import { parseIngredients } from "../lib/normalize.js";

describe("parseIngredients", () => {
  it("handles Indian strip text", () => {
    expect(parseIngredients("Paracetamol IP 500 mg")).toEqual(["paracetamol"]);
  });
  it("splits combinations", () => {
    expect(parseIngredients("Amoxicillin 500mg + Clavulanic Acid 125mg")).toEqual(["amoxicillin", "clavulanic acid"]);
  });
  it("strips pharmacopoeia and form words", () => {
    expect(parseIngredients("Each film coated tablet contains Metformin Hydrochloride IP 500 mg")).toEqual(["metformin hydrochloride"]);
  });
  it("dedupes and drops junk", () => {
    expect(parseIngredients("Aspirin, aspirin, 12")).toEqual(["aspirin"]);
  });
});
