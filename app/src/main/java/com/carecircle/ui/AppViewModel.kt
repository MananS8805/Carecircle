package com.carecircle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carecircle.data.*
import com.carecircle.domain.MissedDose
import com.carecircle.domain.Schedule
import com.carecircle.reminder.DoseActions
import com.carecircle.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class DoseStatus { UPCOMING, DUE, TAKEN, MISSED }

data class TodayItem(val med: Medicine, val scheduledAt: Long, val event: DoseEvent?, val status: DoseStatus)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    val prefs = Prefs(app)
    private val zone = ZoneId.systemDefault()

    val medicines: StateFlow<List<Medicine>> =
        repo.medicines.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(30_000) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<List<TodayItem>> = run {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 24 * 3600_000L
        combine(medicines, repo.doses.observeBetween(start, end), ticker) { meds, events, now ->
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
    }

    fun markTaken(item: TodayItem) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        repo.doses.insert(DoseEvent(medicineId = item.med.id, scheduledAt = item.scheduledAt))
        val ev = repo.doses.find(item.med.id, item.scheduledAt) ?: return@launch
        DoseActions.markTaken(ctx, ev.id)
    }

    fun save(m: Medicine, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = if (m.id == 0L) repo.medicines.insert(m) else { repo.medicines.update(m); m.id }
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

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(zone)
}
