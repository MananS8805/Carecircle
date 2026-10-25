import { handleMedicineInfo } from "../lib/medicineInfo.js";

export function GET(request: Request): Promise<Response> {
  return handleMedicineInfo(request);
}
