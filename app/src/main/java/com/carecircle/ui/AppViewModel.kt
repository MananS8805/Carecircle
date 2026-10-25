package com.carecircle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carecircle.R
import com.carecircle.data.*
import com.carecircle.domain.Condition
import com.carecircle.domain.Conditions
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

/** A one-line status for the Settings data card: a string resource, shown once. */
data class DataMsg(@androidx.annotation.StringRes val res: Int)

enum class DoseStatus { UPCOMING, DUE, TAKEN, MISSED }

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
        meds.groupBy { Conditions.infer(it.name, it.salt) }
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
                        MissedDose.isMissed(at, now, null) -> DoseStatus.MISSED
                        now >= at -> DoseStatus.DUE
                        else -> DoseStatus.UPCOMING
                    }
                    TodayItem(m, at, ev, status)
                }
            }.sortedBy { it.scheduledAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun save(m: Medicine, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val withPerson = if (m.personId > 0) m else m.copy(personId = scopePerson.value?.id ?: repo.ensureDefaultPerson().id)
        val id = if (m.id == 0L) repo.medicines.insert(withPerson) else { repo.medicines.update(withPerson); m.id }
        ReminderScheduler.scheduleFor(getApplication(), id)
        onDone(id)
    }

    fun archive(id: Long) = viewModelScope.launch {
        repo.medicines.archive(id)
        ReminderScheduler.cancel(getApplication(), id)
    }

    fun refillAdd(m: Medicine, pills: Int) = viewModelScope.launch { repo.medicines.update(m.copy(pillCount = m.pillCount + pills)) }

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

    // ---- Export / restore / delete all ----
    private val backup = BackupService(app)
    private val _dataMsg = MutableStateFlow<DataMsg?>(null)
    val dataMsg: StateFlow<DataMsg?> = _dataMsg
    private val _restorePreview = MutableStateFlow<BackupFile?>(null)
    val restorePreview: StateFlow<BackupFile?> = _restorePreview

    fun dismissDataMsg() { _dataMsg.value = null }
    fun cancelRestore() { _restorePreview.value = null }

    fun exportTo(uri: android.net.Uri) = viewModelScope.launch {
        _dataMsg.value = try {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: throw java.io.IOException()
            backup.export(out)
            DataMsg(R.string.data_export_done)
        } catch (e: Exception) { DataMsg(R.string.data_export_failed) }
    }

    fun readBackup(uri: android.net.Uri) = viewModelScope.launch {
        val parsed = try {
            val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: throw java.io.IOException()
            backup.read(input)
        } catch (e: Exception) { BackupParse.Malformed }
        when (parsed) {
            is BackupParse.Ok -> _restorePreview.value = parsed.file
            BackupParse.Newer -> _dataMsg.value = DataMsg(R.string.data_restore_newer)
            BackupParse.Malformed -> _dataMsg.value = DataMsg(R.string.data_restore_bad)
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        val f = _restorePreview.value ?: return@launch
        _restorePreview.value = null
        backup.restore(f)
        selectedId.value = Prefs.UNSET
        _dataMsg.value = DataMsg(R.string.data_restore_done)
    }

    fun deleteAllData() = viewModelScope.launch {
        backup.deleteAll()
        selectedId.value = Prefs.UNSET
        _card.value = null; _interactions.value = null; _insurance.value = InsuranceState.Idle
        _dataMsg.value = DataMsg(R.string.data_deleted)
    }

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(zone)
}
