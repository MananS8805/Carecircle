package com.carecircle.domain

enum class OrganKind { HEART, PANCREAS, LUNGS, KIDNEY, BRAIN, THYROID, JOINT, BODY }

/** What a medicine is for. [label] is also the stored tag text for the built-in chips. */
enum class Condition(val label: String, val organ: OrganKind) {
    DIABETES("Diabetes", OrganKind.PANCREAS),
    BLOOD_PRESSURE("Blood pressure", OrganKind.HEART),
    CHOLESTEROL("Cholesterol", OrganKind.HEART),
    THYROID("Thyroid", OrganKind.THYROID),
    HEART("Heart", OrganKind.HEART),
    ASTHMA("Asthma", OrganKind.LUNGS),
    ARTHRITIS("Arthritis", OrganKind.JOINT),
    KIDNEY("Kidney", OrganKind.KIDNEY),
    BRAIN("Brain and nerves", OrganKind.BRAIN),
    INFECTION("Infection", OrganKind.BODY),
    PAIN("Pain", OrganKind.BODY),
    OTHER("Other", OrganKind.BODY),
}

object Conditions {
    private val generics: Map<Condition, List<String>> = mapOf(
        Condition.DIABETES to listOf("metformin", "glimepiride", "gliclazide", "glipizide", "sitagliptin", "vildagliptin", "teneligliptin", "pioglitazone", "insulin", "dapagliflozin", "empagliflozin", "voglibose"),
        Condition.BLOOD_PRESSURE to listOf("telmisartan", "amlodipine", "losartan", "olmesartan", "ramipril", "enalapril", "atenolol", "metoprolol", "bisoprolol", "nebivolol", "hydrochlorothiazide", "chlorthalidone", "cilnidipine"),
        Condition.CHOLESTEROL to listOf("atorvastatin", "rosuvastatin", "simvastatin", "fenofibrate", "ezetimibe"),
        Condition.THYROID to listOf("levothyroxine", "thyroxine", "carbimazole", "thiamazole", "methimazole"),
        Condition.HEART to listOf("clopidogrel", "aspirin", "warfarin", "isosorbide", "nitroglycerin", "ticagrelor", "ivabradine", "digoxin"),
        Condition.ASTHMA to listOf("salbutamol", "albuterol", "levosalbutamol", "montelukast", "budesonide", "formoterol", "theophylline", "ipratropium"),
        Condition.ARTHRITIS to listOf("methotrexate", "etoricoxib", "hydroxychloroquine", "leflunomide", "sulfasalazine"),
        Condition.KIDNEY to listOf("sevelamer", "calcium acetate", "sodium bicarbonate"),
        Condition.BRAIN to listOf("levetiracetam", "carbamazepine", "phenytoin", "donepezil", "levodopa", "valproate", "gabapentin", "pregabalin"),
        Condition.INFECTION to listOf("azithromycin", "amoxicillin", "amoxycillin", "ciprofloxacin", "cefixime", "doxycycline", "cefuroxime", "levofloxacin", "metronidazole"),
        Condition.PAIN to listOf("paracetamol", "acetaminophen", "ibuprofen", "diclofenac", "tramadol", "naproxen", "aceclofenac"),
    )

    /** Chip text or free text -> built-in condition, or null when it is a custom tag. */
    fun fromTag(tag: String?): Condition? {
        val t = tag?.trim()?.lowercase().orEmpty()
        if (t.isEmpty()) return null
        return Condition.entries.firstOrNull { it.label.lowercase() == t }
    }

    /** Best guess from the medicine or generic name; used until the user sets a tag. */
    fun infer(name: String, salt: String): Condition? {
        val hay = "$salt $name".lowercase()
        return generics.entries.firstOrNull { (_, words) -> words.any { hay.contains(it) } }?.key
    }
}
