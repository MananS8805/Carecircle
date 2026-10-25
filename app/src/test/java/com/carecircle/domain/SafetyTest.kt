package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {
    @Test fun penicillinAllergyCatchesAmoxicillinBrands() {
        assertEquals(listOf("Penicillin"), Safety.allergyMatches(MedLite("Mox 500", "amoxicillin"), listOf("Penicillin")))
        assertEquals(listOf("penicillin"), Safety.allergyMatches(MedLite("Augmentin 625", "amoxycillin + clavulanic acid"), listOf("penicillin")))
    }

    @Test fun classAllergiesAndDirectNames() {
        assertTrue(Safety.allergyMatches(MedLite("Brufen", "ibuprofen"), listOf("NSAIDs")).isNotEmpty())
        assertTrue(Safety.allergyMatches(MedLite("Dolo 650", "paracetamol"), listOf("acetaminophen")).isNotEmpty())
        assertTrue(Safety.allergyMatches(MedLite("Metformin", "metformin"), listOf("penicillin", "sulfa")).isEmpty())
        assertTrue(Safety.allergyMatches(MedLite("Anything", "x"), listOf("  ", "")).isEmpty())
    }

    @Test fun duplicateIngredientsAcrossMedicines() {
        val d = Safety.duplicateIngredients(listOf(MedLite("Dolo 650", "Paracetamol IP 650 mg"), MedLite("Crocin", "acetaminophen"), MedLite("Telma", "telmisartan")))
        assertEquals(setOf("paracetamol"), d.keys)
        assertEquals(listOf("Dolo 650", "Crocin"), d["paracetamol"])
    }

    @Test fun combinationProductsAreSplit() {
        assertEquals(listOf("amoxicillin", "clavulanic acid"), Safety.ingredients("Amoxycillin 500mg + Clavulanic Acid 125 mg"))
        assertTrue(Safety.duplicateIngredients(listOf(MedLite("A", "metformin"), MedLite("B", "glimepiride"))).isEmpty())
    }

    @Test fun overlapWithExistingMedicines() {
        val o = Safety.overlapsWith(MedLite("New", "paracetamol + caffeine"), listOf(MedLite("Dolo", "paracetamol"), MedLite("Telma", "telmisartan")))
        assertEquals(mapOf("paracetamol" to listOf("Dolo")), o)
        assertTrue(Safety.overlapsWith(MedLite("New", ""), listOf(MedLite("Dolo", "paracetamol"))).isEmpty())
    }

    @Test fun highAlertMedicines() {
        assertTrue(Safety.isHighAlert(MedLite("Warf 5", "warfarin")))
        assertTrue(Safety.isHighAlert(MedLite("Mixtard", "human insulin")))
        assertFalse(Safety.isHighAlert(MedLite("Dolo 650", "paracetamol")))
    }

    @Test fun labelTextDropsHeadingsAndReferences() {
        val raw = "1 INDICATIONS AND USAGE Atorvastatin calcium tablets are indicated: To reduce the risk of stroke ( 1.1 ). As an adjunct to diet to reduce LDL-C ( 1.2 , 2.3 ). Adults with hyperlipidemia."
        val s = LabelText.sentences(raw)
        assertTrue(s.first().startsWith("Atorvastatin calcium tablets are indicated"))
        assertTrue(s.none { it.contains("INDICATIONS") || it.contains("( 1") })
        assertTrue(s.size >= 3)
    }

    @Test fun labelTextHandlesInteractionNoise() {
        val s = LabelText.sentences("7 DRUG INTERACTIONS See full prescribing information for details regarding concomitant use with grapefruit juice ( 2. - 5 , 7. - 1 ).")
        assertTrue(s.first().startsWith("See full prescribing"))
        assertFalse(s.first().contains("DRUG INTERACTIONS"))
    }

    @Test fun labelTextEmptyIsEmpty() {
        assertTrue(LabelText.sentences("").isEmpty())
        assertTrue(LabelText.sentences("  ( 2 ) ").isEmpty())
    }
}
