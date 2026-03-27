package com.airmesh.ui.screens.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airmesh.bluetooth.BleManager
import com.airmesh.data.db.ConversationSummary
import com.airmesh.data.model.NearbyDevice
import com.airmesh.data.repository.MessageRepository
import com.airmesh.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val preferencesManager: PreferencesManager,
    private val bleManager: BleManager
) : ViewModel() {

    val username: StateFlow<String> = preferencesManager.usernameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val nearbyDevices: StateFlow<List<NearbyDevice>> = bleManager.nearbyDevicesFlow

    val conversations: StateFlow<List<ConversationSummary>> = username
        .flatMapLatest { name ->
            if (name.isBlank()) flowOf(emptyList())
            else messageRepository.getConversationPeers(name)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
