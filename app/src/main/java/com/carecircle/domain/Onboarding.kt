package com.carecircle.domain

/** The five first-run steps, in order. Each can be skipped, and the guide as a whole too. */
enum class OnboardingStep { LANGUAGE_PRIVACY, PERSON, MEDICINE, PERMISSIONS, SUMMARY }

sealed interface OnboardingState {
    /** Nothing to show: the guide was finished or skipped. */
    data object Done : OnboardingState
    /** Someone who used CareCircle before this guide existed: a dismissible "What's new" card instead. */
    data object WhatsNew : OnboardingState
    data class Show(val step: OnboardingStep) : OnboardingState
}

data class OnboardingInputs(
    val guideDone: Boolean,
    /** The step reached so far (an index into [OnboardingStep]), kept across process death. 0 when never started. */
    val savedStep: Int,
    /** An earlier version already ran its permissions screen. */
    val legacyOnboardingDone: Boolean,
    val hasMedicines: Boolean,
    /** A person with a real name or a caregiver number: data entered by hand or restored from a backup. */
    val hasPersonDetails: Boolean,
    val whatsNewSeen: Boolean,
)

object Onboarding {
    fun onboardingState(i: OnboardingInputs): OnboardingState {
        if (i.guideDone) return OnboardingState.Done
        val existingUser = i.hasMedicines || (i.legacyOnboardingDone && i.savedStep == 0)
        if (existingUser && i.savedStep == 0) return if (i.whatsNewSeen) OnboardingState.Done else OnboardingState.WhatsNew
        val step = OnboardingStep.entries[i.savedStep.coerceIn(0, OnboardingStep.entries.lastIndex)]
        return OnboardingState.Show(if (step == OnboardingStep.PERSON && i.hasPersonDetails) next(step, i.hasPersonDetails) else step)
    }

    /** The step after [from]. The person step is skipped when a person already exists, for example after restoring a backup. */
    fun next(from: OnboardingStep, hasPersonDetails: Boolean): OnboardingStep {
        var n = OnboardingStep.entries.getOrNull(from.ordinal + 1) ?: return OnboardingStep.SUMMARY
        if (n == OnboardingStep.PERSON && hasPersonDetails) n = OnboardingStep.MEDICINE
        return n
    }

    const val MAX_NAME = 40

    /** Whether a name may be saved as the first person. */
    fun validName(raw: String) = raw.trim().length in 1..MAX_NAME
}
