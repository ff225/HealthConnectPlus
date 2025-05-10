package com.research.healthconnectplus

import android.content.Context

class PreferencesManager(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("health_connect_plus", Context.MODE_PRIVATE)

    fun getEnabledHealthDataTypes(): Set<String> {
        return sharedPreferences.getStringSet("enabled_health_data_types", emptySet()) ?: emptySet()
    }

    private fun removeHealthDataType(type: String) {
        val types = getEnabledHealthDataTypes().toMutableSet()
        types.remove(type)
        sharedPreferences.edit().putStringSet("enabled_health_data_types", types).apply()
    }

    private fun addHealthDataType(type: String) {
        val types = getEnabledHealthDataTypes().toMutableSet()
        types.add(type)
        sharedPreferences.edit().putStringSet("enabled_health_data_types", types).apply()
    }

    fun setCollectStepData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_step_data", sendData).apply()
        when (sendData) {
            true -> addHealthDataType("steps")
            false -> removeHealthDataType("steps")
        }
    }

    fun setCollectHeartRateData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_heart_rate_data", sendData).apply()
        when (sendData) {
            true -> addHealthDataType("heart_rate")
            false -> removeHealthDataType("heart_rate")
        }
    }

    fun getCollectStepData(): Boolean {
        return sharedPreferences.getBoolean("send_step_data", false)
    }

    fun getCollectHeartRateData(): Boolean {
        return sharedPreferences.getBoolean("send_heart_rate_data", false)
    }

    fun setCollectBloodPressureData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_blood_pressure_data", sendData).apply()
        when (sendData) {
            true -> addHealthDataType("blood_pressure")
            false -> removeHealthDataType("blood_pressure")
        }
    }

    fun getCollectBloodPressureData(): Boolean {
        return sharedPreferences.getBoolean("send_blood_pressure_data", false)
    }

    fun setCollectMovesenseData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_movesense_data", sendData).apply()
    }

    fun getCollectMovesenseData(): Boolean {
        return sharedPreferences.getBoolean("send_movesense_data", false)
    }

    // store movesense info using object MovesensePair and shared preferences
    fun setMovesenseInfo(name: String, address: String) {
        sharedPreferences.edit().putString("movesense_name", name).apply()
        sharedPreferences.edit().putString("movesense_address", address).apply()
    }

    // get movesense info using object MovesensePair and shared preferences
    fun getMovesenseInfo(): Pair<String?, String?> {
        val name = sharedPreferences.getString("movesense_name", null)
        val address = sharedPreferences.getString("movesense_address", null)
        return Pair(name, address)
    }

    // reset movesense info
    fun resetMovesenseInfo() {
        sharedPreferences.edit().remove("movesense_name").apply()
        sharedPreferences.edit().remove("movesense_address").apply()
    }

    fun getSerialId(): String? {
        return sharedPreferences.getString("movesense_name", null)?.removePrefix("Movesense ")
    }

}