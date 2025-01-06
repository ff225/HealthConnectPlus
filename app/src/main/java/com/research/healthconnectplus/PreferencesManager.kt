package com.research.healthconnectplus

import android.content.Context

class PreferencesManager(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("health_connect_plus", Context.MODE_PRIVATE)

    fun setSendStepData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_step_data", sendData).apply()
    }

    fun setSendHeartRateData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_heart_rate_data", sendData).apply()
    }

    fun getSendStepData(): Boolean {
        return sharedPreferences.getBoolean("send_step_data", false)
    }

    fun getSendHeartRateData(): Boolean {
        return sharedPreferences.getBoolean("send_heart_rate_data", false)
    }

    fun setSendMovesenseData(sendData: Boolean) {
        sharedPreferences.edit().putBoolean("send_movesense_data", sendData).apply()
    }

    fun getSendMovesenseData(): Boolean {
        return sharedPreferences.getBoolean("send_movesense_data", false)
    }
}