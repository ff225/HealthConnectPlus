package com.research.healthconnectplus.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.healthconnectplus.data.RepositoryProvider
import com.research.healthconnectplus.screen.home.uiHealthData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DataViewModel(
    private val dataType: String? = null,
    private val repositoryProvider: RepositoryProvider
) : ViewModel() {

    val uiData = MutableStateFlow<List<uiHealthData>>(emptyList())

    init {
        loadData()
    }

    /**
     * Load data from the repository based on the data type.
     * This function is called in the init block to load data when the ViewModel is created.
     */

    private fun loadData() {

        // val result = mutableListOf<uiHealthData>()
        viewModelScope.launch {
            val result = when (dataType) {
                "heart_rate" -> {
                    repositoryProvider.heartRepository
                        .getUnsyncedHeartRecords()
                        .map { record ->
                            uiHealthData(
                                type = "Heart Rate",
                                value = "${record.bpm} bpm",
                                lastUpdate = formatInstant(Instant.ofEpochMilli(record.endTime)),
                            )
                        }
                }

                "steps" -> {
                    repositoryProvider.stepRepository
                        .getUnsyncedStepRecords()
                        .map { record ->
                            uiHealthData(
                                type = "Steps",
                                value = "${record.count} steps",
                                lastUpdate = formatInstant(Instant.ofEpochMilli(record.endTime)),
                            )
                        }
                }

                else -> emptyList()
            }

            uiData.value = result
        }

    }

    private fun formatInstant(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}