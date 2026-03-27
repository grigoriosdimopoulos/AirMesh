package com.airmesh.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airmesh.bluetooth.BleManager
import com.airmesh.data.db.DeviceStatsEntity
import com.airmesh.data.model.NearbyDevice
import com.airmesh.data.repository.DeviceRepository
import com.airmesh.data.repository.MessageRepository
import com.airmesh.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class StatisticsUiState(
    val nearbyDevices: List<NearbyDevice> = emptyList(),
    val deviceStats: List<DeviceStatsEntity> = emptyList(),
    val totalSent: Int = 0,
    val totalReceived: Int = 0,
    val totalCarried: Int = 0
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val messageRepository: MessageRepository,
    private val preferencesManager: PreferencesManager,
    private val bleManager: BleManager
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        bleManager.nearbyDevicesFlow,
        deviceRepository.getAllStats(),
        preferencesManager.usernameFlow.flatMapLatest { name ->
            if (name.isBlank()) flowOf(Triple(0, 0, 0))
            else combine(
                messageRepository.getSentCount(name),
                messageRepository.getReceivedCount(name),
                messageRepository.getCarriedCount(name)
            ) { sent, received, carried -> Triple(sent, received, carried) }
        }
    ) { nearby, stats, (sent, received, carried) ->
        StatisticsUiState(
            nearbyDevices = nearby,
            deviceStats = stats,
            totalSent = sent,
            totalReceived = received,
            totalCarried = carried
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState())
}
