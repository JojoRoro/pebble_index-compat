package coredevices.ring.database

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchIndexPreferences(private val settings: Settings) {
    private val mutableEnabled = MutableStateFlow(settings.getBoolean(KEY, false))
    val enabled = mutableEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        settings.putBoolean(KEY, enabled)
        mutableEnabled.value = enabled
    }

    private companion object {
        const val KEY = "pt2_index_capture_enabled"
    }
}
