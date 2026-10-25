package com.carecircle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carecircle.R
import com.carecircle.ui.theme.NavItem
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.PillNavBar

private val topLevel = setOf("today", "meds", "insurance", "settings")

@Composable
fun CareCircleApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        NavItem("today", stringResource(R.string.tab_today), Icons.Default.Alarm),
        NavItem("meds", stringResource(R.string.tab_medicines), Icons.Default.Medication),
        NavItem("insurance", stringResource(R.string.tab_insurance), Icons.Default.Shield),
        NavItem("settings", stringResource(R.string.tab_settings), Icons.Default.Settings),
    )
    Scaffold(
        containerColor = Palette.Canvas,
        bottomBar = {
            if (route == null || route in topLevel) {
                PillNavBar(tabs, route) { r -> nav.navigate(r) { launchSingleTop = true; popUpTo("today") } }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            NavHost(nav, startDestination = "today") {
                composable("today") { TodayScreen(vm, onHistory = { nav.navigate("history") }) }
                composable("history") { HistoryScreen(vm, onBack = { nav.popBackStack() }) }
                composable("meds") {
                    MedicinesScreen(vm, onAdd = { nav.navigate("edit/0") }, onOpen = { nav.navigate("detail/$it") }, onInteractions = { nav.navigate("interactions") })
                }
                composable("interactions") { InteractionScreen(vm, onBack = { nav.popBackStack() }) }
                composable("edit/{id}") { e ->
                    EditScreen(vm, e.arguments?.getString("id")?.toLongOrNull() ?: 0L, onScan = { nav.navigate("scan") }) { nav.popBackStack() }
                }
                composable("scan") {
                    StripScanScreen(
                        onResult = { vm.pendingScan.value = it; nav.popBackStack() },
                        onTypeInstead = { nav.popBackStack() },
                    )
                }
                composable("insurance") { InsuranceScreen(vm) }
                composable("detail/{id}") { e ->
                    DetailScreen(
                        vm, e.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                        onEdit = { nav.navigate("edit/$it") }, onBack = { nav.popBackStack() },
                        onOpenInsurance = { nav.navigate("insurance") { launchSingleTop = true; popUpTo("today") } },
                    )
                }
                composable("settings") { SettingsScreen(vm, onManagePeople = { nav.navigate("people") }) }
                composable("people") { ManagePeopleScreen(vm, onBack = { nav.popBackStack() }) }
            }
        }
    }
}
