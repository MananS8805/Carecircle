package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    private fun inputs(
        guideDone: Boolean = false, savedStep: Int = 0, legacy: Boolean = false, meds: Boolean = false, person: Boolean = false, seen: Boolean = false,
    ) = OnboardingInputs(guideDone, savedStep, legacy, meds, person, seen)

    @Test fun freshInstallStartsAtTheFirstStep() {
        assertEquals(OnboardingState.Show(OnboardingStep.LANGUAGE_PRIVACY), Onboarding.onboardingState(inputs()))
    }

    @Test fun resumesAtTheSavedStepAfterProcessDeath() {
        assertEquals(OnboardingState.Show(OnboardingStep.PERMISSIONS), Onboarding.onboardingState(inputs(savedStep = 3)))
        assertEquals(OnboardingState.Show(OnboardingStep.MEDICINE), Onboarding.onboardingState(inputs(savedStep = 2)))
    }

    @Test fun anExistingUserGetsNoFlow() {
        assertEquals(OnboardingState.WhatsNew, Onboarding.onboardingState(inputs(meds = true)))
        assertEquals(OnboardingState.WhatsNew, Onboarding.onboardingState(inputs(legacy = true)))
        assertEquals(OnboardingState.Done, Onboarding.onboardingState(inputs(meds = true, seen = true)))
        assertEquals(OnboardingState.Done, Onboarding.onboardingState(inputs(guideDone = true)))
    }

    @Test fun aGuideThatHasStartedIsNotMistakenForAnExistingUser() {
        // Medicines added during step 3, then the process died: the guide carries on with permissions.
        assertEquals(OnboardingState.Show(OnboardingStep.PERMISSIONS), Onboarding.onboardingState(inputs(meds = true, savedStep = 3)))
    }

    @Test fun restoredDataSkipsThePersonStep() {
        assertEquals(OnboardingStep.MEDICINE, Onboarding.next(OnboardingStep.LANGUAGE_PRIVACY, hasPersonDetails = true))
        assertEquals(OnboardingStep.PERSON, Onboarding.next(OnboardingStep.LANGUAGE_PRIVACY, hasPersonDetails = false))
        assertEquals(OnboardingState.Show(OnboardingStep.MEDICINE), Onboarding.onboardingState(inputs(savedStep = 1, person = true)))
    }

    @Test fun deniedPermissionsStillReachTheSummaryAndTheGuideCompletes() {
        // Completion never depends on what the user granted: next() walks to SUMMARY regardless.
        var step = OnboardingStep.LANGUAGE_PRIVACY
        val seen = mutableListOf(step)
        while (step != OnboardingStep.SUMMARY) { step = Onboarding.next(step, false); seen += step }
        assertEquals(OnboardingStep.entries, seen)
        assertEquals(OnboardingStep.SUMMARY, Onboarding.next(OnboardingStep.SUMMARY, false))
    }

    @Test fun savedStepOutOfRangeIsClamped() {
        assertEquals(OnboardingState.Show(OnboardingStep.SUMMARY), Onboarding.onboardingState(inputs(savedStep = 99)))
        assertEquals(OnboardingState.Show(OnboardingStep.LANGUAGE_PRIVACY), Onboarding.onboardingState(inputs(savedStep = -4)))
    }

    @Test fun namesAreTrimmedAndLimited() {
        assertTrue(Onboarding.validName("  Mom "))
        assertFalse(Onboarding.validName("   "))
        assertFalse(Onboarding.validName("x".repeat(Onboarding.MAX_NAME + 1)))
    }
}
