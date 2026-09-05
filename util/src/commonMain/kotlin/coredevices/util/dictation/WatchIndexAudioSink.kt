package coredevices.util.dictation

import kotlin.uuid.Uuid

/** Shared contract with JojoRoro/pebble-index-emu. UUID routing is not authentication. */
object WatchIndexContract {
    val APP_UUID: Uuid = Uuid.parse("569b6a5f-12ee-46d4-90a8-45a8b6d9f210")
    const val QUEUED_ACK = "INDEX_QUEUED_V1"
    const val SAMPLE_RATE = 16000
    const val MAX_SECONDS = 60
    const val MAX_PCM_BYTES = SAMPLE_RATE * MAX_SECONDS * 2
}

interface WatchIndexAudioSink {
    fun isEnabled(): Boolean

    /** Returns only after both audio copies are saved and the local queue accepts the task. */
    suspend fun enqueue(pcm: ByteArray)
}
