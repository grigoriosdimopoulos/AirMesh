package com.airmesh.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airmesh.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val username = preferencesManager.usernameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setUsername(name: String) {
        viewModelScope.launch { preferencesManager.setUsername(name) }
    }
}
