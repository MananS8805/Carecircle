package com.carecircle.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.carecircle.R
import com.carecircle.domain.Condition
import com.carecircle.domain.FoodTiming
import com.carecircle.domain.OrganKind

@StringRes
fun Condition.labelRes(): Int = when (this) {
    Condition.DIABETES -> R.string.cond_diabetes
    Condition.BLOOD_PRESSURE -> R.string.cond_blood_pressure
    Condition.CHOLESTEROL -> R.string.cond_cholesterol
    Condition.THYROID -> R.string.cond_thyroid
    Condition.HEART -> R.string.cond_heart
    Condition.ASTHMA -> R.string.cond_asthma
    Condition.ARTHRITIS -> R.string.cond_arthritis
    Condition.KIDNEY -> R.string.cond_kidney
    Condition.BRAIN -> R.string.cond_brain
    Condition.INFECTION -> R.string.cond_infection
    Condition.PAIN -> R.string.cond_pain
    Condition.OTHER -> R.string.cond_other
}

@StringRes
fun OrganKind.labelRes(): Int = when (this) {
    OrganKind.HEART -> R.string.organ_heart
    OrganKind.PANCREAS -> R.string.organ_pancreas
    OrganKind.LUNGS -> R.string.organ_lungs
    OrganKind.KIDNEY -> R.string.organ_kidney
    OrganKind.BRAIN -> R.string.organ_brain
    OrganKind.THYROID -> R.string.organ_thyroid
    OrganKind.JOINT -> R.string.organ_joint
    OrganKind.BODY -> R.string.organ_body
}

@Composable
fun Condition.label(): String = stringResource(labelRes())

@Composable
fun OrganKind.label(): String = stringResource(labelRes())

@StringRes
fun FoodTiming.labelRes(): Int = when (this) {
    FoodTiming.NONE -> R.string.food_none
    FoodTiming.BEFORE_FOOD -> R.string.food_before
    FoodTiming.WITH_FOOD -> R.string.food_with
    FoodTiming.AFTER_FOOD -> R.string.food_after
    FoodTiming.EMPTY_STOMACH -> R.string.food_empty
    FoodTiming.BEDTIME -> R.string.food_bedtime
}
