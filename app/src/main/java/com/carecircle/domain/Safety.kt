package com.carecircle.domain

/** A medicine as the safety checks see it: the name the user gave and the generic name or salt. */
data class MedLite(val name: String, val salt: String)

/**
 * Local safety checks. They only raise a flag for the person to ask a doctor or pharmacist about; they never say a medicine
 * is safe, and they never use the network.
 */
object Safety {
    private val allergyClasses: Map<String, List<String>> = mapOf(
        "penicillin" to listOf("penicillin", "amoxicillin", "amoxycillin", "ampicillin", "cloxacillin", "piperacillin", "clavulanic acid", "augmentin"),
        "sulfa" to listOf("sulfa", "sulfamethoxazole", "cotrimoxazole", "co-trimoxazole", "sulfasalazine", "sulfadiazine"),
        "cephalosporin" to listOf("cefixime", "cefuroxime", "ceftriaxone", "cefpodoxime", "cephalexin", "cefadroxil", "cefdinir", "cefazolin"),
        "nsaid" to listOf("ibuprofen", "diclofenac", "aspirin", "naproxen", "aceclofenac", "etoricoxib", "ketorolac", "nimesulide", "mefenamic", "piroxicam"),
        "aspirin" to listOf("aspirin", "acetylsalicylic"),
        "paracetamol" to listOf("paracetamol", "acetaminophen"),
        "statin" to listOf("atorvastatin", "rosuvastatin", "simvastatin", "pravastatin"),
        "ace inhibitor" to listOf("ramipril", "enalapril", "lisinopril", "perindopril"),
        "opioid" to listOf("codeine", "tramadol", "morphine", "oxycodone"),
        "macrolide" to listOf("azithromycin", "clarithromycin", "erythromycin"),
        "fluoroquinolone" to listOf("ciprofloxacin", "levofloxacin", "ofloxacin", "moxifloxacin"),
    )

    private val highAlert = listOf(
        "warfarin", "insulin", "digoxin", "methotrexate", "lithium", "heparin", "enoxaparin", "phenytoin", "theophylline",
        "amiodarone", "clozapine", "carbamazepine", "levothyroxine", "apixaban", "rivaroxaban", "dabigatran", "morphine", "fentanyl",
    )

    private val canonical = mapOf("acetaminophen" to "paracetamol", "albuterol" to "salbutamol", "amoxycillin" to "amoxicillin", "epinephrine" to "adrenaline")

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun terms(allergy: String): List<String> {
        val a = norm(allergy)
        if (a.isEmpty()) return emptyList()
        val cls = allergyClasses.entries.firstOrNull { a == it.key || a.contains(it.key) || it.key.contains(a) }
        return (listOf(a) + cls?.value.orEmpty() + (canonical[a]?.let { listOf(it) } ?: emptyList())).distinct()
    }

    /** The allergies from [allergies] that this medicine's name or generic name appears to fall under. */
    fun allergyMatches(medicine: MedLite, allergies: List<String>): List<String> {
        val hay = " " + norm("${medicine.name} ${medicine.salt}") + " "
        return allergies.filter { a -> terms(a).any { t -> t.length >= 3 && hay.contains(t) } }
    }

    /** Ingredient names of a medicine ("amoxicillin 500 mg + clavulanic acid" gives two). */
    fun ingredients(salt: String): List<String> =
        salt.split('+', ',', '&', ';').map { norm(it).replace(Regex("\\b\\d+( \\d+)?\\s*(mg|mcg|g|ml|iu)\\b"), " ").replace(Regex("\\b(ip|bp|usp)\\b"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 3 }.map { canonical[it] ?: it }.distinct()

    /** Ingredients found in two or more of the medicines, with the names of those medicines. */
    fun duplicateIngredients(meds: List<MedLite>): Map<String, List<String>> =
        meds.flatMap { m -> ingredients(m.salt).map { it to m.name } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }.filterValues { it.size >= 2 }

    /** Medicines already on the list that share an ingredient with a new or edited one. */
    fun overlapsWith(candidate: MedLite, others: List<MedLite>): Map<String, List<String>> {
        val mine = ingredients(candidate.salt).toSet()
        if (mine.isEmpty()) return emptyMap()
        return mine.associateWith { ing -> others.filter { ing in ingredients(it.salt) }.map { it.name }.distinct() }.filterValues { it.isNotEmpty() }
    }

    /** High-alert medicines need exact dosing; the app only adds a reminder to follow the prescription. */
    fun isHighAlert(medicine: MedLite): Boolean {
        val hay = " " + norm("${medicine.name} ${medicine.salt}") + " "
        return highAlert.any { hay.contains(it) }
    }
}

/** Tidies raw drug-label text for reading: drops section headings and reference marks, and gives short sentences. */
object LabelText {
    private val heading = Regex("""^\s*(?:\d+(?:\.\d+)*\s+)?[A-Z][A-Z&,/\- ]{3,}?\s+(?=[A-Z][a-z])""")
    private val refs = Regex("""\(\s*\d[\d.,\s-]*\)?""")
    private val splitter = Regex("""(?<=[.!?])\s+(?=[A-Z])""")

    fun sentences(raw: String, max: Int = 40): List<String> {
        var t = raw.replace(Regex("\\s+"), " ").trim()
        t = heading.replace(t, "")
        t = refs.replace(t, "")
        t = t.replace(Regex("\\s+([,.;:])"), "$1").replace(Regex("\\s{2,}"), " ").trim()
        return t.split(splitter).map { it.trim().trim('-', ' ') }.filter { it.length >= 4 }.take(max)
    }
}
