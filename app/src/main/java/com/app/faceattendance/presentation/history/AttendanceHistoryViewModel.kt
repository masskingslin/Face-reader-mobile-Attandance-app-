package com.app.faceattendance.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.faceattendance.data.local.AttendanceDao
import com.app.faceattendance.data.local.AttendanceRecordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class FilterPunchType { ALL, IN, OUT }

data class HistoryUiState(
    val records: List<AttendanceRecordEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedPunchFilter: FilterPunchType = FilterPunchType.ALL,
    val selectedDateMillis: Long? = null,
    val previewImageUri: String? = null,
    val isLoading: Boolean = false
)

class AttendanceHistoryViewModel(
    private val attendanceDao: AttendanceDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _punchFilter = MutableStateFlow(FilterPunchType.ALL)
    private val _selectedDate = MutableStateFlow<Long?>(null)
    private val _previewImageUri = MutableStateFlow<String?>(null)

    private val ninetyDaysCutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)

    val uiState: StateFlow<HistoryUiState> = combine(
        attendanceDao.getRecentAttendance(ninetyDaysCutoff),
        _searchQuery,
        _punchFilter,
        _selectedDate,
        _previewImageUri
    ) { allRecords, query, punchFilter, selectedDate, previewUri ->
        val filtered = allRecords.filter { record ->
            val matchesQuery = query.isBlank() ||
                    record.userName.contains(query, ignoreCase = true) ||
                    record.userId.contains(query, ignoreCase = true)

            val matchesPunch = when (punchFilter) {
                FilterPunchType.ALL -> true
                FilterPunchType.IN -> record.type.equals("IN", ignoreCase = true)
                FilterPunchType.OUT -> record.type.equals("OUT", ignoreCase = true)
            }

            val matchesDate = if (selectedDate == null) {
                true
            } else {
                isSameDay(record.timestamp, selectedDate)
            }

            matchesQuery && matchesPunch && matchesDate
        }

        HistoryUiState(
            records = filtered,
            searchQuery = query,
            selectedPunchFilter = punchFilter,
            selectedDateMillis = selectedDate,
            previewImageUri = previewUri,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onPunchFilterChanged(filter: FilterPunchType) {
        _punchFilter.value = filter
    }

    fun onDateSelected(dateMillis: Long?) {
        _selectedDate.value = dateMillis
    }

    fun onOpenPhotoPreview(uri: String) {
        _previewImageUri.value = uri
    }

    fun onClosePhotoPreview() {
        _previewImageUri.value = null
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
