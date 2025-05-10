package com.research.healthconnectplus.screen.home

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.healthconnectplus.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * TODO:
 * - Check which health data is available
 * - Get the health data
 * - Show the health data in the UI
 */

class HomeViewModel(
    val healthConnectClient: HealthConnectClient,
    val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _selectedTypes = MutableStateFlow<Set<String>>(emptySet())

    private val _healthData = MutableStateFlow<List<uiHealthData>>(emptyList())
    val healthData: StateFlow<List<uiHealthData>> = _healthData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        val types = preferencesManager.getEnabledHealthDataTypes()
        _selectedTypes.value = types
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            val types = preferencesManager.getEnabledHealthDataTypes()
            _selectedTypes.value = types
            loadForDataTypes(types)
            _isLoading.value = false
        }
    }

    private suspend fun loadForDataTypes(types: Set<String>) {
        val result = mutableListOf<uiHealthData>()

        for (type in types) {
            when (type) {
                // get data from the last hour (example, we can change this to get data from the last day, week, month, etc)
                "steps" -> {
                    val response = healthConnectClient.aggregate(
                        AggregateRequest(
                            setOf(
                                StepsRecord.COUNT_TOTAL
                            ),
                            timeRangeFilter = TimeRangeFilter.between(
                                Instant.now().minus(1, ChronoUnit.HOURS),
                                Instant.now()
                            )
                        )
                    )
                    result.add(
                        uiHealthData(
                            type = "Steps",
                            value = response[StepsRecord.COUNT_TOTAL]?.toString() ?: "0",
                            lastUpdate = formatInstant(
                                Instant.now()
                            )
                        )
                    )
                }

                "heart_rate" -> {
                    val response = healthConnectClient.aggregate(
                        AggregateRequest(
                            setOf(
                                HeartRateRecord.BPM_AVG
                            ),
                            timeRangeFilter = TimeRangeFilter.between(
                                Instant.now().minus(1, ChronoUnit.HOURS),
                                Instant.now()
                            )
                        )
                    )
                    result.add(
                        uiHealthData(
                            type = "Heart AVG",
                            value = (response[HeartRateRecord.BPM_AVG]?.toString() ?: "0") + " bpm",
                            lastUpdate = formatInstant(
                                Instant.now()
                            )
                        )
                    )
                }

                "blood_pressure" -> {
                    /* TODO: need to update healthconnect dependency to use aggregate on blood pressure
                    val response = healthConnectClient.aggregate(
                        AggregateRequest(
                            setOf(
                                BloodPressureRecord.SYSTOLIC_AVG,
                                BloodPressureRecord.DIASTOLIC_AVG
                            ),
                            timeRangeFilter = TimeRangeFilter.between(
                                Instant.now().minus(1, ChronoUnit.HOURS),
                                Instant.now()
                            )
                        )
                    )*/

                    result.add(
                        uiHealthData(
                            type = "Blood Pressure",
                            value = "120/80 mmHg",//"${response[BloodPressureRecord.SYSTOLIC_AVG]} / ${response[BloodPressureRecord.DIASTOLIC_AVG]} mmHg",
                            lastUpdate = formatInstant(
                                Instant.now()
                            )
                        )
                    )
                }

                else -> {
                    // Load other data
                }
            }
        }
        _healthData.value = result
    }

    private fun formatInstant(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}

data class uiHealthData(
    val type: String,
    val value: String,
    val lastUpdate: String
)