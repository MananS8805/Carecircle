package com.carecircle.data

/** What kind of place a piece of user data lives in. [BackupService] knows how to wipe each kind. */
enum class StoreKind { ROOM_TABLE, PREFS_KEY, ALARMS, NOTIFICATIONS, BACKGROUND_WORK, WIDGET, CACHE_FILES, PRIVATE_FILE }

/**
 * One place the app keeps something about the user.
 * [exportable]: written into a backup file. [restorable]: replaced when a backup is restored.
 * [wipeOnDelete]: removed by "Delete all my data". [backupField]: the [BackupFile] property that carries it.
 */
data class UserDataStore(
    val id: String,
    val kind: StoreKind,
    val description: String,
    val exportable: Boolean,
    val restorable: Boolean,
    val wipeOnDelete: Boolean,
    val backupField: String? = null,
)

/**
 * The single list of everything CareCircle stores. Backup export, restore and delete-all are driven from it, and
 * `UserDataRegistryTest` fails if a Room table or a Prefs key exists that is not listed here. Adding a store means adding a
 * line here, and (if it is health data) a field in [BackupFile].
 */
object UserDataRegistry {
    private fun table(id: String, what: String, field: String? = null, cache: Boolean = false) =
        UserDataStore(id, StoreKind.ROOM_TABLE, what, exportable = field != null, restorable = field != null, wipeOnDelete = true, backupField = field)
            .let { if (cache) it.copy(exportable = false, restorable = false) else it }

    private fun pref(key: String, what: String, wipe: Boolean = true) =
        UserDataStore(key, StoreKind.PREFS_KEY, what, exportable = false, restorable = false, wipeOnDelete = wipe)

    val stores: List<UserDataStore> = listOf(
        table("person", "People with caregiver and pharmacy numbers", "people"),
        table("medicine", "Medicines and schedules", "medicines"),
        table("dose_event", "Dose history (taken, missed, skipped)", "doses"),
        table("symptom_log", "Side-effect diary", "symptoms"),
        table("allergy", "Allergies", "allergies"),
        table("vital_log", "Health readings", "vitals"),
        table("appointment", "Doctor appointments", "appointments"),
        table("refill_event", "Refill history", "refills"),
        table("policy_summary", "Saved insurance summaries (summary only)", "policySummaries"),
        table("info_card", "Cached medicine cards", cache = true),
        table("interaction_cache", "Cached interaction checks", cache = true),
        table("sync_link", "Family sync codes this phone shares", cache = true),
        table("following", "Family members this phone follows", cache = true),

        pref("onboardingDone", "Permissions screen seen"),
        pref("selectedPerson", "Which person is selected"),
        pref("language", "Language choice", wipe = false),
        pref("appMode", "Patient app or family view"),
        pref("lastReminderAt", "When a reminder last fired"),
        pref("rescheduleLog", "Reminder refresh log"),
        pref("textSize", "Text size", wipe = false),
        pref("highContrast", "High contrast", wipe = false),
        pref("simpleMode", "Simple mode", wipe = false),
        pref("crashLogEnabled", "Keep a local crash log"),
        pref("ackedSlots", "Family phone: doses this phone marked as handled"),
        pref("handledCache", "Parent phone: handled doses merged from every link"),
        pref("lastSyncAt", "When a status last reached the server"),
        pref("quietEnabled", "Family phone: quiet hours on"),
        pref("quietStart", "Family phone: quiet hours start"),
        pref("quietEnd", "Family phone: quiet hours end"),
        pref("snoozeUntil", "Family phone: alerts snoozed until"),
        pref("heldAlerts", "Family phone: alerts held for the digest"),
        pref("pushEnabled", "Family phone: missed-dose alerts on this phone"),
        pref("alertedSlots", "Family phone: alerts already shown"),
        pref("pendingNotify", "Pushes waiting to be sent to the family"),
        pref("weeklyReminder", "Sunday-evening weekly summary reminder"),
        pref("shareNames", "Whether family sync may send names"),
        pref("codeFails", "Wrong family codes in a row"),
        pref("codeLockedUntil", "Family code entry locked until"),
        pref("guideDone", "First-run guide finished"),
        pref("guideStep", "First-run guide step"),
        pref("whatsNewSeen", "Last version whose news was seen"),

        UserDataStore("alarms", StoreKind.ALARMS, "Dose, snooze, repeat and appointment alarms", false, false, true),
        UserDataStore("notifications", StoreKind.NOTIFICATIONS, "Posted notifications", false, false, true),
        UserDataStore("background_work", StoreKind.BACKGROUND_WORK, "Scheduled background jobs", false, false, true),
        UserDataStore("widget", StoreKind.WIDGET, "Home-screen widget contents", false, false, true),
        UserDataStore("cache_files", StoreKind.CACHE_FILES, "Temporary files in the cache folder", false, false, true),
        UserDataStore("diag_log", StoreKind.PRIVATE_FILE, "Local diagnostics log (event codes only, at most 50 events)", false, false, true),
    )

    fun of(kind: StoreKind) = stores.filter { it.kind == kind }
    fun wipeList(kind: StoreKind) = of(kind).filter { it.wipeOnDelete }.map { it.id }
    /** Tables emptied before a restore: everything that is wiped on delete, so the restored data is the only data. */
    fun tablesToClearOnRestore() = wipeList(StoreKind.ROOM_TABLE)
    fun exportedFields(): Set<String> = stores.filter { it.exportable }.mapNotNull { it.backupField }.toSet()
}
