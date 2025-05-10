package com.research.healthconnectplus.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.healthconnectplus.data.UserInfo
import com.research.healthconnectplus.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    var uiProfileState = MutableStateFlow(
        UiProfileState(
            user = UserInfo(0, "", "", 0, "")
        )
    )
        private set

    init {
        viewModelScope.launch {
            userRepository.getUserInfo().collect {
                println(it?.dob)
                uiProfileState.value = UiProfileState(it ?: UserInfo(0, "", "", 0, ""))
            }
        }
    }

    fun insertUserInfo() {
        viewModelScope.launch {
            userRepository.insert(
                uiProfileState.value.user
            )
        }
    }

    fun updateUserInfo() = viewModelScope.launch {
        userRepository.update(
            uiProfileState.value.user
        )
    }

    fun updateName(name: String? = null) {
        uiProfileState.update {
            it.copy(
                user =
                it.user.copy(
                    name = name ?: it.user.name
                )
            )
        }
    }

    fun updateLastName(lastName: String) {
        uiProfileState.update {
            it.copy(
                user = it.user.copy(
                    lastName = lastName
                )
            )
        }
    }

    fun updateDob(dob: String) {
        uiProfileState.update {
            it.copy(
                user = it.user.copy(
                    dob = convertDateToMillis(date = dob)
                )

            )
        }
        println(convertDateToMillis(date = dob))
    }

    fun validateInput(): Boolean {
        return uiProfileState.value.user.name.isNotEmpty() && uiProfileState.value.user.lastName.isNotEmpty() && uiProfileState.value.user.dob != 0L
    }

    private fun convertDateToMillis(date: String): Long {
        val dateParts = date.split("/")
        val day = dateParts[1].toInt()
        val month = dateParts[0].toInt()
        val year = dateParts[2].toInt()
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)
        return calendar.timeInMillis
    }


}

data class UiProfileState(
    val user: UserInfo
)