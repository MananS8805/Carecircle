export interface Source { name: string; url?: string }

export interface MedicineCard {
  generic: string;
  rxcui?: string;
  whyYouTakeIt: string;
  howToTake: string;
  commonSideEffects: string[];
  callDoctorIf: string[];
  keyInteractions: string[];
  sources: Source[];
  summarizedBy: "llm" | "extract";
  disclaimer: string;
}

export const DISCLAIMER = "Informational only, not medical advice. Always follow your doctor's instructions.";

export interface LabelSections {
  indications?: string;
  dosage?: string;
  adverse?: string;
  warnings?: string;
  interactions?: string;
}
