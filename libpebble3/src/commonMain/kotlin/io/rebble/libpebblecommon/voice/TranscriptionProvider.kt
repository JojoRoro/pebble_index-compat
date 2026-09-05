package io.rebble.libpebblecommon.voice

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

interface TranscriptionProvider {
    suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult
    suspend fun canServeSession(): Boolean

    // Defaults preserve providers that do not need the initiating watchapp identity.
    suspend fun canServeSession(appUuid: Uuid): Boolean = canServeSession()

    suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean,
        appUuid: Uuid,
    ): TranscriptionResult = transcribe(encoderInfo, audioFrames, isNotificationReply)
}

/**
 * The maximum amount of time the Pebble firmware will wait for a transcription result before timing
 * out and cancelling the session.
 */
val PEBBLE_FW_TRANSCRIPTION_TIMEOUT = 15.seconds