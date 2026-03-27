package com.airmesh.ui.screens.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airmesh.bluetooth.MeshSyncManager
import com.airmesh.data.db.MessageEntity
import com.airmesh.data.repository.MessageRepository
import com.airmesh.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val meshSyncManager: MeshSyncManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val username: StateFlow<String> = preferencesManager.usernameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun getMessages(peerName: String): StateFlow<List<MessageEntity>> {
        return username
            .flatMapLatest { me ->
                if (me.isBlank()) flowOf(emptyList())
                else messageRepository.getConversation(me, peerName)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun sendMessage(receiverId: String, content: String) {
        viewModelScope.launch {
            meshSyncManager.sendMessage(receiverId, content)
        }
    }
}
