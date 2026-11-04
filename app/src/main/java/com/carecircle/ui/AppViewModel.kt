package com.carecircle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carecircle.R
import com.carecircle.data.*
import com.carecircle.domain.AdherenceResult
import com.carecircle.domain.Condition
import com.carecircle.domain.Conditions
import com.carecircle.domain.resolve
import com.carecircle.domain.MissedDose
import com.carecircle.domain.PolicyLimits
import com.carecircle.domain.SavedPolicy
import com.carecircle.domain.OrganKind
import com.carecircle.domain.Schedule
import com.carecircle.reminder.DoseActions
import com.carecircle.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReadFailure { PASSWORD, UNREADABLE, TOO_LITTLE_TEXT }
enum class SendFailure { TOO_LARGE, RATE_LIMITED, OFFLINE, OTHER }

/** "Ask about your policy". The question and clauses leave the phone only from [NeedConsent], and the consent is asked every time. */
sealed interface AskState {
    data object Idle : AskState
    data class NeedConsent(val question: String, val clauseCount: Int) : AskState
    data object Sending : AskState
    data class Failed(val kind: AskFailure) : AskState
    data class Done(val question: String, val response: PolicyQuestionResponse) : AskState
}
enum class AskFailure { BUSY, RATE_LIMITED, OFFLINE, TOO_LARGE, NO_POLICY, OTHER }

/** Insurance flow. The OCR text lives only inside [NeedConsent] and is dropped on cancel, decline or send. */
sealed interface InsuranceState {
    data object Idle : InsuranceState
    data class Reading(val done: Int, val total: Int) : InsuranceState
    data class ReadFailed(val kind: ReadFailure) : InsuranceState
    data class NeedConsent(val pages: List<PolicyPageText>, val truncated: Boolean) : InsuranceState
    data object Sending : InsuranceState
    data class Failed(val kind: SendFailure) : InsuranceState
    data class Result(val response: PolicyResponse, val truncated: Boolean, val saved: Boolean, val savedId: Long? = null) : InsuranceState
}

data class SavedPolicyItem(val id: Long, val policy: SavedPolicy)

/** The code dialog on the patient phone. */
sealed interface PairingUi {
    data object None : PairingUi
    data object Creating : PairingUi
    data class Code(val code: String, val personName: String) : PairingUi
    data class Failed(val offline: Boolean) : PairingUi
}

/** A one-line status for the Settings data card: a string resource, shown once. */
data class DataMsg(@androidx.annotation.StringRes val res: Int)

enum class DoseStatus { UPCOMING, DUE, TAKEN, SKIPPED, MISSED }

data class TodayItem(val med: Medicine, val scheduledAt: Long, val event: DoseEvent?, val status: DoseStatus)

/** Medicines grouped by the body part they act on, for the interactive organ view. */
data class OrganGroup(val condition: Condition?, val organ: OrganKind, val meds: List<Medicine>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    val prefs = Prefs(app)
    private val zone = ZoneId.systemDefault()

    // ---- People ----
    val people: StateFlow<List<Person>> =
        repo.people.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedId = MutableStateFlow(prefs.selectedPersonId)

    /** The raw choice: a person id, or [Prefs.ALL_PEOPLE] (Today only). */
    val selection: StateFlow<Long> = selectedId

    /** The person whose data Medicines, Insurance and History show. "All people" falls back to the first person. */
    val scopePerson: StateFlow<Person?> = combine(people, selectedId) { ps, id -> ps.firstOrNull { it.id == id } ?: ps.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init { viewModelScope.launch { repo.ensureDefaultPerson() } }

    fun selectPerson(id: Long) { prefs.selectedPersonId = id; selectedId.value = id }

    private val allMedicines: StateFlow<List<Medicine>> =
        repo.medicines.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Medicines of the scope person (Medicines tab, interaction check, PDF, organ map). */
    val medicines: StateFlow<List<Medicine>> = combine(allMedicines, scopePerson) { meds, p -> if (p == null) meds else meds.filter { it.personId == p.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Medicines on Today: the selected person, or everyone when "All people" is chosen. */
    private val todayMedicines: StateFlow<List<Medicine>> = combine(allMedicines, selectedId, people) { meds, id, ps ->
        if (id == Prefs.ALL_PEOPLE) meds
        else (ps.firstOrNull { it.id == id } ?: ps.firstOrNull())?.let { p -> meds.filter { it.personId == p.id } } ?: meds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyMap: StateFlow<List<OrganGroup>> = todayMedicines.map { meds ->
        meds.groupBy { Conditions.resolve(it.conditionTag, it.name, it.salt) }
            .map { (c, ms) -> OrganGroup(c, c?.organ ?: OrganKind.BODY, ms) }
            .sortedByDescending { it.meds.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(30_000) } }
    private val dayKey = flow { while (true) { emit(LocalDate.now(zone)); kotlinx.coroutines.delay(60_000) } }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayEvents = dayKey.flatMapLatest { d ->
        val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
        repo.doses.observeBetween(start, start + 24 * 3600_000L)
    }

    val today: StateFlow<List<TodayItem>> =
        combine(todayMedicines, todayEvents, ticker) { meds, events, now ->
            val d = LocalDate.now(zone)
            meds.flatMap { m ->
                Schedule.parseTimes(m.timesCsv).map { t ->
                    val at = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
                    val ev = events.firstOrNull { it.medicineId == m.id && it.scheduledAt == at }
                    val status = when {
                        ev?.takenAt != null -> DoseStatus.TAKEN
                        ev?.skipped == true -> DoseStatus.SKIPPED
                        MissedDose.isMissed(at, now, null) -> DoseStatus.MISSED
                        now >= at -> DoseStatus.DUE
                        else -> DoseStatus.UPCOMING
                    }
                    TodayItem(m, at, ev, status)
                }
            }.sortedBy { it.scheduledAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The saved policy summary for a person (the most recent one), or null when none is saved. */
    suspend fun latestPolicy(personId: Long): SavedPolicy? = repo.latestPolicy(personId)

    // ---- History ----
    private val _history = MutableStateFlow<AdherenceResult?>(null)
    val history: StateFlow<AdherenceResult?> = _history
    private val _historyDays = MutableStateFlow(7)
    val historyDays: StateFlow<Int> = _historyDays

    fun loadHistory(days: Int) = viewModelScope.launch {
        _historyDays.value = days
        val p = scopePerson.value ?: return@launch
        _history.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.adherence(p.id, days) }
    }

    private val _weekly = MutableStateFlow<Pair<String, com.carecircle.domain.WeeklySummary>?>(null)
    /** The 7-day summary waiting in the preview dialog: person name and numbers. */
    val weekly: StateFlow<Pair<String, com.carecircle.domain.WeeklySummary>?> = _weekly

    fun openWeeklyPreview() = viewModelScope.launch {
        val p = scopePerson.value ?: return@launch
        val (r, stock) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.adherence(p.id, 7) to repo.medicines.all().filter { it.personId == p.id }.map {
                com.carecircle.domain.StockMed(it.name, it.doseText, it.pillCount, it.pillsPerDose, Schedule.parseTimes(it.timesCsv).size)
            }
        }
        _weekly.value = p.name to com.carecircle.domain.WeeklySummaries.compute(r, stock)
    }

    fun closeWeeklyPreview() { _weekly.value = null }

    /** Opens the share sheet with the exact text the user just saw. User-initiated only; nothing is sent by the app itself. */
    fun shareWeeklyText(text: String) {
        val base = getApplication<Application>()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
        base.startActivity(android.content.Intent.createChooser(send, base.getString(R.string.hist_share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        _weekly.value = null
    }

    private val _weeklyReminder = MutableStateFlow(prefs.weeklyReminder)
    val weeklyReminder: StateFlow<Boolean> = _weeklyReminder
    fun setWeeklyReminder(on: Boolean) {
        prefs.weeklyReminder = on; _weeklyReminder.value = on
        ReminderScheduler.scheduleWeeklyReminder(getApplication())
    }

    /** Builds the doctor-visit PDF for the selected person, with 30 days of adherence, and opens the share sheet. */
    fun shareDoctorPdf() = viewModelScope.launch {
        val p = scopePerson.value ?: return@launch
        val ctx = getApplication<Application>()
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PdfExport.create(ctx, p.name, medicines.value, repo.adherence(p.id, 30), repo.symptomsSince(System.currentTimeMillis() - 30L * 24 * 3600_000L), repo.vitalsSince(p.id, System.currentTimeMillis() - 30L * 24 * 3600_000L))
        }
        PdfExport.share(ctx, file)
    }

    fun personOf(id: Long): Person? = people.value.firstOrNull { it.id == id }

    fun addPerson(name: String) = viewModelScope.launch { selectPerson(repo.addPerson(name)) }

    fun updatePerson(p: Person) = viewModelScope.launch { repo.updatePerson(p) }

    suspend fun medicineCount(personId: Long) = repo.medicineCount(personId)

    fun deletePerson(id: Long) = viewModelScope.launch {
        if (repo.deletePerson(id) && (selectedId.value == id)) selectPerson(Prefs.UNSET)
    }

    fun markTaken(item: TodayItem) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        repo.doses.insert(DoseEvent(medicineId = item.med.id, scheduledAt = item.scheduledAt))
        val ev = repo.doses.find(item.med.id, item.scheduledAt) ?: return@launch
        DoseActions.markTaken(ctx, ev.id)
    }

    fun saveNote(m: Medicine, note: String, onDone: (Medicine) -> Unit) = viewModelScope.launch {
        val updated = m.copy(doctorNote = note.trim().take(500).ifEmpty { null })
        repo.medicines.update(updated)
        onDone(updated)
    }

    fun symptoms(medicineId: Long) = repo.symptoms(medicineId)

    fun logSymptom(medicineId: Long, symptom: String, severity: Int, note: String) = viewModelScope.launch {
        repo.logSymptom(SymptomLog(medicineId = medicineId, loggedAt = System.currentTimeMillis(), symptom = symptom.trim().take(60), severity = severity.coerceIn(1, 3), note = note.trim().take(200)))
    }

    fun deleteSymptom(id: Long) = viewModelScope.launch { repo.deleteSymptom(id) }

    /** Active medicine count and when the oldest was added, for the reminders health check. */
    suspend fun healthInputs(): Pair<Int, Long> {
        val meds = repo.medicines.all()
        return meds.size to (meds.map { it.createdAt }.filter { it > 0 }.minOrNull() ?: 0L)
    }

    // ---- Vitals and appointments (for the selected person) ----
    @OptIn(ExperimentalCoroutinesApi::class)
    fun vitals(kind: String): Flow<List<VitalLog>> = scopePerson.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.vitals(p.id, kind) }

    fun logVital(kind: String, v1: Double, v2: Double?, note: String) = viewModelScope.launch {
        val p = scopePerson.value ?: return@launch
        repo.logVital(VitalLog(personId = p.id, kind = kind, v1 = v1, v2 = v2, measuredAt = System.currentTimeMillis(), note = note.trim().take(120)))
    }

    fun deleteVital(id: Long) = viewModelScope.launch { repo.deleteVital(id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val appointments: StateFlow<List<Appointment>> = scopePerson.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.appointments(p.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveAppointment(title: String, doctor: String, startsAt: Long, notes: String, remindBefore: Int) = viewModelScope.launch {
        val p = scopePerson.value ?: return@launch
        repo.saveAppointment(Appointment(personId = p.id, title = title.trim().take(80), doctor = doctor.trim().take(80), startsAt = startsAt, notes = notes.trim().take(300), remindBefore = remindBefore))
    }

    fun deleteAppointment(id: Long) = viewModelScope.launch { repo.deleteAppointment(id) }

    // ---- Allergies and safety flags ----
    fun allergiesFor(personId: Long) = repo.allergies(personId)
    fun addAllergy(personId: Long, name: String) = viewModelScope.launch { repo.addAllergy(personId, name) }
    fun removeAllergy(id: Long) = viewModelScope.launch { repo.removeAllergy(id) }

    /** Allergy names of a person, for one-off checks in screens. */
    suspend fun allergyNames(personId: Long) = repo.allergyNames(personId)

    fun skip(item: TodayItem) = viewModelScope.launch {
        repo.doses.insert(DoseEvent(medicineId = item.med.id, scheduledAt = item.scheduledAt))
        val ev = repo.doses.find(item.med.id, item.scheduledAt) ?: return@launch
        DoseActions.skip(getApplication(), ev.id)
    }

    fun save(m: Medicine, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val tag = m.conditionTag?.trim()?.take(60)?.ifEmpty { null }
        val stamped = m.copy(conditionTag = tag, createdAt = if (m.createdAt > 0) m.createdAt else System.currentTimeMillis())
        val withPerson = if (stamped.personId > 0) stamped else stamped.copy(personId = scopePerson.value?.id ?: repo.ensureDefaultPerson().id)
        val id = if (m.id == 0L) repo.medicines.insert(withPerson) else { repo.medicines.update(withPerson); m.id }
        ReminderScheduler.scheduleFor(getApplication(), id)
        com.carecircle.reminder.DoseWidget.refreshAll(getApplication())
        onDone(id)
    }

    fun archive(id: Long) = viewModelScope.launch {
        repo.medicines.archive(id, System.currentTimeMillis())
        ReminderScheduler.cancel(getApplication(), id)
        com.carecircle.reminder.DoseWidget.refreshAll(getApplication())
    }

    fun refillHistory(medicineId: Long) = repo.refillHistory(medicineId)

    fun boughtMore(medicineId: Long, quantity: Int, note: String, onDone: (Repository.RefillOutcome?) -> Unit) = viewModelScope.launch {
        onDone(repo.addRefill(medicineId, quantity, note))
    }

    fun undoRefill(outcome: Repository.RefillOutcome, onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(repo.undoRefill(outcome)) }

    private val _card = MutableStateFlow<CardResult?>(null)
    val card: StateFlow<CardResult?> = _card
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun loadCard(salt: String) = viewModelScope.launch {
        _loading.value = true; _card.value = null
        _card.value = repo.medicineCard(salt)
        _loading.value = false
    }

    fun clearCard() { _card.value = null; _loading.value = false }

    // ---- Interaction check ----
    private val _interactions = MutableStateFlow<InteractionResult?>(null)
    val interactions: StateFlow<InteractionResult?> = _interactions
    private val _interactionsLoading = MutableStateFlow(false)
    val interactionsLoading: StateFlow<Boolean> = _interactionsLoading

    fun checkInteractions() = viewModelScope.launch {
        _interactionsLoading.value = true; _interactions.value = null
        _interactions.value = repo.interactionCheck(medicines.value.map { it.salt })
        _interactionsLoading.value = false
    }

    /** Result of the strip scanner, picked up once by the Edit screen. */
    val pendingScan = MutableStateFlow<ScanResult?>(null)

    // ---- Insurance policy summary ----
    private val _insurance = MutableStateFlow<InsuranceState>(InsuranceState.Idle)
    val insurance: StateFlow<InsuranceState> = _insurance
    private var readJob: kotlinx.coroutines.Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val savedPolicies: StateFlow<List<SavedPolicyItem>> = scopePerson.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.savedPolicies(p.id)
    }.map { list ->
        list.mapNotNull { e ->
            runCatching { ApiFactory.json.decodeFromString<SavedPolicy>(e.json) }.getOrNull()?.let { SavedPolicyItem(e.id, it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun readDocuments(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        readJob?.cancel()
        _insurance.value = InsuranceState.Reading(0, 0)
        readJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                val out = PolicyOcr.read(ctx, uris) { d, t -> _insurance.value = InsuranceState.Reading(d, t) }
                _insurance.value =
                    if (!PolicyLimits.hasEnoughText(out.pages.map { it.text })) InsuranceState.ReadFailed(ReadFailure.TOO_LITTLE_TEXT)
                    else InsuranceState.NeedConsent(out.pages, out.truncated)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _insurance.value = InsuranceState.Idle
                throw e
            } catch (e: PolicyOcr.PasswordProtectedException) {
                _insurance.value = InsuranceState.ReadFailed(ReadFailure.PASSWORD)
            } catch (e: Exception) {
                _insurance.value = InsuranceState.ReadFailed(ReadFailure.UNREADABLE)
            } finally {
                ctx.cacheDir.listFiles { f -> f.name.startsWith("policy-") }?.forEach { it.delete() }
            }
        }
    }

    fun cancelReading() { readJob?.cancel(); _insurance.value = InsuranceState.Idle }

    /** Called only from the consent screen. Nothing is sent before this. */
    fun consentAndSend() {
        val s = _insurance.value as? InsuranceState.NeedConsent ?: return
        _insurance.value = InsuranceState.Sending
        viewModelScope.launch {
            _insurance.value = when (val r = repo.policySummary(s.pages)) {
                is PolicyResult.Ok -> InsuranceState.Result(r.response, s.truncated, saved = false)
                PolicyResult.TooLarge -> InsuranceState.Failed(SendFailure.TOO_LARGE)
                PolicyResult.RateLimited -> InsuranceState.Failed(SendFailure.RATE_LIMITED)
                PolicyResult.Offline -> InsuranceState.Failed(SendFailure.OFFLINE)
                PolicyResult.Error -> InsuranceState.Failed(SendFailure.OTHER)
            }
        }
    }

    fun resetInsurance() { readJob?.cancel(); _insurance.value = InsuranceState.Idle }

    fun saveCurrentPolicy() = viewModelScope.launch {
        val r = _insurance.value as? InsuranceState.Result ?: return@launch
        if (r.saved) return@launch
        repo.savePolicy(scopePerson.value?.id ?: return@launch, r.response)
        _insurance.value = r.copy(saved = true)
    }

    fun viewSaved(item: SavedPolicyItem) {
        _insurance.value = InsuranceState.Result(
            PolicyResponse(item.policy.summary, item.policy.unverified, item.policy.mode), truncated = false, saved = true, savedId = item.id,
        )
    }

    fun deleteSaved(id: Long) = viewModelScope.launch {
        repo.deletePolicy(id)
        if ((_insurance.value as? InsuranceState.Result)?.savedId == id) _insurance.value = InsuranceState.Idle
    }

    // ---- First-run guide ----
    private val _guide = MutableStateFlow<com.carecircle.domain.OnboardingState?>(null)
    /** null while it is being worked out. */
    val guide: StateFlow<com.carecircle.domain.OnboardingState?> = _guide
    private var guideHasPerson = false
    /** Set by the guide's "Say it" button; the add-medicine screen opens its voice prompt once. */
    val startWithVoice = MutableStateFlow(false)

    init { loadGuide() }

    private fun currentVersion() = com.carecircle.BuildConfig.VERSION_CODE

    fun loadGuide() = viewModelScope.launch {
        val people = repo.people.all()
        guideHasPerson = people.any { it.name != Persons.DEFAULT_NAME || it.caregiverPhone.isNotBlank() }
        _guide.value = com.carecircle.domain.Onboarding.onboardingState(
            com.carecircle.domain.OnboardingInputs(
                guideDone = prefs.guideDone, savedStep = prefs.guideStep, legacyOnboardingDone = prefs.onboardingDone,
                hasMedicines = repo.medicines.all().isNotEmpty(), hasPersonDetails = guideHasPerson, whatsNewSeen = prefs.whatsNewSeen >= currentVersion(),
            ),
        )
    }

    fun reloadGuide() { loadGuide() }

    fun guideNext(from: com.carecircle.domain.OnboardingStep) {
        val n = com.carecircle.domain.Onboarding.next(from, guideHasPerson)
        prefs.guideStep = n.ordinal
        _guide.value = com.carecircle.domain.OnboardingState.Show(n)
    }

    fun finishGuide() {
        prefs.guideDone = true; prefs.onboardingDone = true; prefs.whatsNewSeen = currentVersion()
        _guide.value = com.carecircle.domain.OnboardingState.Done
    }

    fun dismissWhatsNew() { prefs.whatsNewSeen = currentVersion(); _guide.value = com.carecircle.domain.OnboardingState.Done }

    /** Settings > Replay setup guide. */
    fun replayGuide() {
        prefs.guideDone = false; prefs.guideStep = 0
        _guide.value = com.carecircle.domain.OnboardingState.Show(com.carecircle.domain.OnboardingStep.LANGUAGE_PRIVACY)
    }

    /** Edits the first person rather than adding one, so rotating the phone or retrying never duplicates anyone. */
    fun saveFirstPerson(name: String, phone: String, onDone: () -> Unit) = viewModelScope.launch {
        val first = repo.people.all().firstOrNull()
        if (first != null && com.carecircle.domain.Onboarding.validName(name)) {
            repo.updatePerson(first.copy(name = name.trim(), caregiverPhone = phone.trim()))
            guideHasPerson = true
        }
        onDone()
    }

    // ---- Display (text size, high contrast, simple mode) ----
    private val _display = MutableStateFlow(com.carecircle.domain.DisplayPrefs(com.carecircle.domain.TextSize.fromKey(prefs.textSize), prefs.highContrast, prefs.simpleMode))
    val display: StateFlow<com.carecircle.domain.DisplayPrefs> = _display

    fun setTextSize(size: com.carecircle.domain.TextSize) { prefs.textSize = size.key; _display.value = _display.value.copy(textSize = size) }
    fun setHighContrast(on: Boolean) { prefs.highContrast = on; _display.value = _display.value.copy(highContrast = on) }
    fun setSimpleMode(on: Boolean) { prefs.simpleMode = on; _display.value = _display.value.copy(simpleMode = on) }

    // ---- Family phone: quiet hours and snooze ----
    private val _quiet = MutableStateFlow(com.carecircle.reminder.QuietDigest.settings(app))
    val quiet: StateFlow<com.carecircle.domain.QuietSettings> = _quiet
    private val _held = MutableStateFlow(0)
    /** Alerts waiting although the quiet period is over (for the Status banner, for example after a force-stop). */
    val heldWaiting: StateFlow<Int> = _held

    private fun quietChanged() {
        val ctx = getApplication<Application>()
        _quiet.value = com.carecircle.reminder.QuietDigest.settings(ctx)
        // Turning quiet hours or the snooze off with alerts held shows the digest straight away.
        com.carecircle.reminder.QuietDigest.flushIfDue(ctx)
        _held.value = com.carecircle.reminder.QuietDigest.waitingCount(ctx)
    }

    fun setQuietEnabled(on: Boolean) { prefs.quietEnabled = on; quietChanged() }

    /** Saves a range only when start and end differ; returns false (and saves nothing) otherwise. */
    fun setQuietRange(start: java.time.LocalTime, end: java.time.LocalTime): Boolean {
        if (!com.carecircle.domain.QuietHours.validRange(start, end)) return false
        prefs.quietStart = start.toString(); prefs.quietEnd = end.toString(); quietChanged(); return true
    }

    fun snooze(choice: com.carecircle.domain.SnoozeChoice?) {
        prefs.snoozeUntil = choice?.let { com.carecircle.domain.QuietHours.snoozeUntil(it, System.currentTimeMillis(), java.time.ZoneId.systemDefault()) } ?: 0L
        quietChanged()
    }

    fun refreshHeld() { _held.value = com.carecircle.reminder.QuietDigest.waitingCount(getApplication()) }
    fun dismissHeld() { prefs.heldAlerts = ""; _held.value = 0 }

    // ---- Family phone: missed-dose alerts ----
    private val _push = MutableStateFlow(prefs.pushEnabled)
    val pushEnabled: StateFlow<Boolean> = _push
    fun setPushEnabled(on: Boolean) = viewModelScope.launch {
        prefs.pushEnabled = on; _push.value = on
        com.carecircle.data.PushTokens.sync(getApplication()) // writes the token when on, removes it when off
    }

    // ---- Family sync: names ----
    private val _shareNames = MutableStateFlow(prefs.shareNames)
    val shareNames: StateFlow<Boolean> = _shareNames
    fun setShareNames(on: Boolean) { prefs.shareNames = on; _shareNames.value = on; refreshStatuses() }

    /** A label only this phone knows, for someone who shares no name. */
    fun renameFollowing(linkId: String, name: String) = viewModelScope.launch {
        val f = repo.sync.following().firstOrNull { it.linkId == linkId } ?: return@launch
        repo.sync.putFollowing(f.copy(personName = name.trim().take(40)))
    }

    // ---- Ask about your policy ----
    private val _ask = MutableStateFlow<AskState>(AskState.Idle)
    val ask: StateFlow<AskState> = _ask
    private val _askPrefill = MutableStateFlow<String?>(null)
    /** A question handed over from a medicine's page; the Insurance screen takes it once. */
    val askPrefill: StateFlow<String?> = _askPrefill

    fun prefillAsk(question: String) { _askPrefill.value = question }
    fun takeAskPrefill() { _askPrefill.value = null }

    fun askPolicy(question: String) = viewModelScope.launch {
        val person = scopePerson.value ?: return@launch
        val clauses = repo.latestPolicy(person.id)?.summary?.let(com.carecircle.domain.ClaimAnswer::clausesFrom).orEmpty()
        _ask.value = when {
            !com.carecircle.domain.ClaimAnswer.validQuestion(question) -> AskState.Idle
            clauses.isEmpty() -> AskState.Failed(AskFailure.NO_POLICY)
            else -> AskState.NeedConsent(question.trim(), clauses.size)
        }
    }

    fun confirmAsk() = viewModelScope.launch {
        val s = _ask.value as? AskState.NeedConsent ?: return@launch
        val person = scopePerson.value ?: return@launch
        val clauses = repo.latestPolicy(person.id)?.summary?.let(com.carecircle.domain.ClaimAnswer::clausesFrom).orEmpty()
        _ask.value = AskState.Sending
        _ask.value = when (val r = repo.policyQuestion(s.question, clauses)) {
            is QuestionResult.Ok -> AskState.Done(s.question, r.response)
            QuestionResult.Busy -> AskState.Failed(AskFailure.BUSY)
            QuestionResult.RateLimited -> AskState.Failed(AskFailure.RATE_LIMITED)
            QuestionResult.Offline -> AskState.Failed(AskFailure.OFFLINE)
            QuestionResult.TooLarge -> AskState.Failed(AskFailure.TOO_LARGE)
            QuestionResult.Error -> AskState.Failed(AskFailure.OTHER)
        }
    }

    fun resetAsk() { _ask.value = AskState.Idle }

    // ---- App mode: patient app or family view ----
    private val _mode = MutableStateFlow(prefs.appMode.ifEmpty { if (prefs.onboardingDone) Prefs.MODE_PATIENT else "" })
    /** "" means the role chooser is shown; otherwise [Prefs.MODE_PATIENT] or [Prefs.MODE_FAMILY]. */
    val appMode: StateFlow<String> = _mode

    fun setMode(m: String) { prefs.appMode = m; _mode.value = m }

    // ---- Family sync ----
    val syncLinks: StateFlow<List<SyncLink>> = repo.sync.observeLinks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val following: StateFlow<List<Following>> = repo.sync.observeFollowing().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _linkStates = MutableStateFlow<Map<String, LinkState>>(emptyMap())
    val linkStates: StateFlow<Map<String, LinkState>> = _linkStates
    private val _pairing = MutableStateFlow<PairingUi>(PairingUi.None)
    val pairing: StateFlow<PairingUi> = _pairing
    private val _followResult = MutableStateFlow<FollowResult?>(null)
    val followResult: StateFlow<FollowResult?> = _followResult

    /** Everyone this phone shares with, with a status from the last check. The labels never leave the phone. */
    val parentLinks: StateFlow<List<com.carecircle.domain.ParentLink>> =
        combine(syncLinks, linkStates) { links, states ->
            links.map { l ->
                com.carecircle.domain.ParentLink(
                    l.linkId, l.personId, l.label, createdAt = l.createdAt,
                    status = when (states[l.linkId] ?: LinkState.UNKNOWN) {
                        LinkState.WAITING -> com.carecircle.domain.LinkStatus.PENDING
                        LinkState.LINKED -> com.carecircle.domain.LinkStatus.ACTIVE
                        LinkState.GONE -> com.carecircle.domain.LinkStatus.REVOKED
                        LinkState.UNKNOWN -> com.carecircle.domain.LinkStatus.UNKNOWN
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _labelError = MutableStateFlow<com.carecircle.domain.LabelCheck?>(null)
    val labelError: StateFlow<com.carecircle.domain.LabelCheck?> = _labelError

    fun renameLink(linkId: String, newLabel: String, onDone: () -> Unit) = viewModelScope.launch {
        val link = syncLinks.value.firstOrNull { it.linkId == linkId } ?: return@launch
        val others = syncLinks.value.filter { it.personId == link.personId && it.linkId != linkId }.map { it.label }
        val check = com.carecircle.domain.ParentLinks.checkLabel(newLabel, others)
        _labelError.value = check.takeIf { it != com.carecircle.domain.LabelCheck.OK }
        if (check == com.carecircle.domain.LabelCheck.OK) { repo.sync.setLabel(linkId, newLabel.trim()); onDone() }
    }

    fun clearLabelError() { _labelError.value = null }

    private val _lostAccess = MutableStateFlow<List<String>>(emptyList())
    /** Family phone: people whose link was ended by the other side. Shown once, then forgotten. */
    val lostAccess: StateFlow<List<String>> = _lostAccess
    fun dismissLostAccess() { _lostAccess.value = emptyList() }

    /** Family phone: a removed link is gone on the server. Forget it here (this also stops its push token) and say so. */
    fun checkAccess() = viewModelScope.launch {
        val gone = repo.sync.following().filter { CareSync.linkState(it.linkId) == LinkState.GONE }
        if (gone.isEmpty()) return@launch
        gone.forEach { repo.sync.deleteFollowing(it.linkId) }
        _lostAccess.value = gone.map { it.personName.ifBlank { getApplication<Application>().getString(R.string.family_member_fallback) } }
    }

    fun refreshLinkStates() = viewModelScope.launch {
        _linkStates.value = syncLinks.value.associate { it.linkId to CareSync.linkState(it.linkId) }
    }

    /** Called only after the consent dialog. */
    fun createPairing(personId: Long) = viewModelScope.launch {
        val p = personOf(personId) ?: return@launch
        if (!com.carecircle.domain.ParentLinks.canAddLink(parentLinks.value, personId)) return@launch
        _pairing.value = PairingUi.Creating
        _pairing.value = when (val r = CareSync.createPairing(getApplication(), personId, com.carecircle.domain.SyncPrivacy.personField(p.name, prefs.shareNames))) {
            is PairResult.Ok -> {
                val app = getApplication<Application>()
                val existing = repo.sync.linksFor(personId).filter { it.linkId != r.code }.map { it.label }
                val n = com.carecircle.domain.ParentLinks.nextDefaultNumber(existing) { app.getString(R.string.cg_default_label, it) }
                repo.sync.setLabel(r.code, app.getString(R.string.cg_default_label, n))
                PairingUi.Code(r.code, p.name)
            }
            PairResult.Offline -> PairingUi.Failed(offline = true)
            else -> PairingUi.Failed(offline = false)
        }
    }

    fun dismissPairing() { _pairing.value = PairingUi.None; refreshLinkStates() }

    /** Brings the family ring for today up to date when the app opens. */
    fun refreshStatuses() = viewModelScope.launch {
        repo.sync.links().map { it.personId }.distinct().forEach { CareSync.publishStatus(getApplication(), it, tidy = true) }
    }

    private val _syncNow = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    /** null = not tried, true = the family's copy was updated, false = it could not be reached. */
    val syncNow: kotlinx.coroutines.flow.StateFlow<Boolean?> = _syncNow

    fun syncNow() = viewModelScope.launch {
        _syncNow.value = null
        val ids = repo.sync.links().map { it.personId }.distinct()
        _syncNow.value = ids.isNotEmpty() && ids.map { CareSync.publishStatus(getApplication(), it) }.all { it }
    }

    fun unlink(linkId: String) = viewModelScope.launch { CareSync.unlinkOwner(getApplication(), linkId); refreshLinkStates() }

    fun follow(code: String) = viewModelScope.launch {
        _followResult.value = null
        val r = CareSync.follow(getApplication(), code)
        _followResult.value = r
        if (r is FollowResult.Ok) {
            com.carecircle.reminder.FollowerWorker.ensure(getApplication())
            com.carecircle.data.PushTokens.sync(getApplication()) // push is on by default after adding a family member
        }
    }

    fun unfollow(linkId: String) = viewModelScope.launch { CareSync.unfollow(getApplication(), linkId); _followResult.value = null }

    fun events(linkId: String) = CareSync.observeEvents(linkId)

    fun status(linkId: String) = CareSync.observeStatus(linkId)

    // ---- Export / restore / delete all ----
    private val backup = BackupService(app)
    private val _dataMsg = MutableStateFlow<DataMsg?>(null)
    val dataMsg: StateFlow<DataMsg?> = _dataMsg
    private val _restorePreview = MutableStateFlow<BackupFile?>(null)
    val restorePreview: StateFlow<BackupFile?> = _restorePreview

    fun dismissDataMsg() { _dataMsg.value = null }
    fun cancelRestore() { _restorePreview.value = null }

    private var exportPassword: String? = null
    fun setExportPassword(p: String?) { exportPassword = p }

    fun exportTo(uri: android.net.Uri) = viewModelScope.launch {
        val pw = exportPassword
        exportPassword = null // used once; never kept
        _dataMsg.value = try {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: throw java.io.IOException()
            backup.export(out, password = pw)
            DataMsg(R.string.data_export_done)
        } catch (e: Exception) { DataMsg(R.string.data_export_failed) }
    }

    private val _needPassword = MutableStateFlow<android.net.Uri?>(null)
    /** Set while an encrypted backup waits for its password. */
    val needPassword: StateFlow<android.net.Uri?> = _needPassword
    private val _wrongPassword = MutableStateFlow(false)
    val wrongPassword: StateFlow<Boolean> = _wrongPassword

    fun cancelPassword() { _needPassword.value = null; _wrongPassword.value = false }
    fun submitPassword(p: String) { _needPassword.value?.let { readBackup(it, p) } }

    fun readBackup(uri: android.net.Uri, password: String? = null) = viewModelScope.launch {
        val parsed = try {
            val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: throw java.io.IOException()
            backup.read(input, password)
        } catch (e: Exception) { BackupParse.Malformed }
        _wrongPassword.value = false
        when (parsed) {
            is BackupParse.Ok -> { _needPassword.value = null; _restorePreview.value = parsed.file }
            BackupParse.Newer -> { _needPassword.value = null; _dataMsg.value = DataMsg(R.string.data_restore_newer) }
            BackupParse.Malformed -> { _needPassword.value = null; _dataMsg.value = DataMsg(R.string.data_restore_bad) }
            BackupParse.NeedsPassword -> _needPassword.value = uri
            BackupParse.WrongPassword -> { _needPassword.value = uri; _wrongPassword.value = true }
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        val f = _restorePreview.value ?: return@launch
        _restorePreview.value = null
        backup.restore(f)
        selectedId.value = Prefs.UNSET
        _dataMsg.value = DataMsg(R.string.data_restore_done)
        loadGuide()
    }

    private val _deleteSummary = MutableStateFlow<DeleteSummary?>(null)
    val deleteSummary: StateFlow<DeleteSummary?> = _deleteSummary

    fun deleteAllData() = viewModelScope.launch {
        _deleteSummary.value = backup.deleteAll()
        selectedId.value = Prefs.UNSET
        _card.value = null; _interactions.value = null; _insurance.value = InsuranceState.Idle
        _dataMsg.value = DataMsg(R.string.data_deleted)
        loadGuide()
    }

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(zone)
}
