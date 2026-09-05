package coredevices.ring.service.recordings

import coredevices.ring.database.WatchIndexPreferences
import coredevices.ring.storage.RecordingStorage
import coredevices.util.CoreConfigFlow
import coredevices.util.Platform
import coredevices.util.dictation.WatchIndexAudioSink
import coredevices.util.dictation.WatchIndexContract
import coredevices.util.isAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class WatchIndexRecordingSink(
    private val storage: RecordingStorage,
    private val queue: RecordingProcessingQueue,
    private val preferences: WatchIndexPreferences,
    private val coreConfig: CoreConfigFlow,
    private val platform: Platform,
) : WatchIndexAudioSink {
    override fun isEnabled(): Boolean =
        platform.isAndroid && coreConfig.value.enableIndex && preferences.enabled.value

    override suspend fun enqueue(pcm: ByteArray) {
        saveWatchIndexRecording(storage, pcm, ::isEnabled) { queue.queueLocalAudioProcessing(it) }
    }
}

internal suspend fun saveWatchIndexRecording(
    storage: RecordingStorage,
    pcm: ByteArray,
    isEnabled: () -> Boolean,
    enqueue: suspend (String) -> Unit,
) = withContext(Dispatchers.IO) {
    check(isEnabled()) { "Watch Index capture is disabled" }
    require(pcm.isNotEmpty() && pcm.size <= WatchIndexContract.MAX_PCM_BYTES && pcm.size % 2 == 0)
    val fileId = "watch-index-${Uuid.random()}"
    var queueStarted = false
    try {
        currentCoroutineContext().ensureActive()
        storage.openOriginalRecordingSink(fileId, WatchIndexContract.SAMPLE_RATE, "audio/raw").use {
            it.write(pcm)
        }
        currentCoroutineContext().ensureActive()
        storage.openRecordingSink(fileId, WatchIndexContract.SAMPLE_RATE, "audio/raw").use {
            it.write(pcm)
        }
        currentCoroutineContext().ensureActive()
        check(isEnabled()) { "Watch Index capture was disabled during recording" }
        // Queue insertion is durable. If cancellation races its return, keep the files:
        // deleting them could corrupt a task that was already inserted in the database.
        queueStarted = true
        enqueue(fileId)
    } finally {
        if (!queueStarted) {
            runCatching { storage.deleteRecordingFromCache(fileId) }
            runCatching { storage.deleteRecordingFromCache("$fileId-original") }
        }
    }
}
