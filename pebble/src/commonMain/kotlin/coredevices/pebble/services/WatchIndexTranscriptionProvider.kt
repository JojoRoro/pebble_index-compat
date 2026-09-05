package coredevices.pebble.services

import coredevices.speex.SpeexCodec
import coredevices.speex.SpeexDecodeResult
import coredevices.util.dictation.WatchIndexAudioSink
import coredevices.util.dictation.WatchIndexContract
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.Uuid

/** Only the dedicated app is routed to Index; its audio must never fall back to ordinary STT. */
class WatchIndexTranscriptionProvider(
    private val delegate: TranscriptionProvider,
    private val sink: WatchIndexAudioSink?,
    private val decode: suspend (VoiceEncoderInfo.Speex, List<UByteArray>) -> ByteArray = ::decodeWatchIndexAudio,
) : TranscriptionProvider {
    override suspend fun canServeSession(): Boolean = delegate.canServeSession()

    override suspend fun canServeSession(appUuid: Uuid): Boolean =
        if (appUuid == WatchIndexContract.APP_UUID) sink?.isEnabled() == true
        else delegate.canServeSession(appUuid)

    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean,
    ): TranscriptionResult = delegate.transcribe(encoderInfo, audioFrames, isNotificationReply)

    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean,
        appUuid: Uuid,
    ): TranscriptionResult {
        if (appUuid != WatchIndexContract.APP_UUID) {
            return delegate.transcribe(encoderInfo, audioFrames, isNotificationReply, appUuid)
        }
        val destination = sink
        if (isNotificationReply || destination?.isEnabled() != true) return TranscriptionResult.Disabled
        // The pinned native codec always uses Speex wideband (320 samples), regardless of
        // metadata. Reject other layouts before allocating its native output buffer.
        if (encoderInfo !is VoiceEncoderInfo.Speex || !validWatchIndexEncoder(encoderInfo)) {
            return TranscriptionResult.Error("Unsupported Index watch audio format")
        }
        return try {
            val frames = withTimeoutOrNull(65_000L) {
                collectWatchIndexFrames(audioFrames)
            } ?: return TranscriptionResult.Error("Index recording timed out")
            if (frames.isEmpty()) return TranscriptionResult.Error("No Index audio received")
            withTimeoutOrNull(10_000L) {
                val pcm = decode(encoderInfo, frames)
                require(pcm.isNotEmpty() && pcm.size <= WatchIndexContract.MAX_PCM_BYTES && pcm.size % 2 == 0)
                currentCoroutineContext().ensureActive()
                if (!destination.isEnabled()) return@withTimeoutOrNull TranscriptionResult.Disabled
                destination.enqueue(pcm)
                TranscriptionResult.Success(listOf(TranscriptionWord(WatchIndexContract.QUEUED_ACK, 1.0f)))
            } ?: TranscriptionResult.Error("Index save timed out; check phone before retrying")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TranscriptionResult.Error("Index capture failed; check phone before retrying")
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun validWatchIndexEncoder(info: VoiceEncoderInfo.Speex): Boolean =
    info.sampleRate == WatchIndexContract.SAMPLE_RATE.toLong() &&
        info.frameSize == 320 &&
        info.bitRate in 1..64000

@OptIn(ExperimentalUnsignedTypes::class)
internal suspend fun collectWatchIndexFrames(audio: Flow<UByteArray>): List<UByteArray> {
    val frames = ArrayList<UByteArray>()
    audio.collect { frame ->
        currentCoroutineContext().ensureActive()
        require(frame.size in 2..256) { "Invalid Speex frame size" }
        require(frames.size < WatchIndexContract.MAX_SECONDS * 50) { "Index recording too long" }
        frames.add(frame.copyOf())
    }
    return frames
}

@OptIn(ExperimentalUnsignedTypes::class)
private suspend fun decodeWatchIndexAudio(
    info: VoiceEncoderInfo.Speex,
    frames: List<UByteArray>,
): ByteArray = withContext(Dispatchers.Default) {
    require(validWatchIndexEncoder(info))
    val framePcm = ByteArray(320 * Short.SIZE_BYTES)
    val output = ByteArray(frames.size * framePcm.size)
    SpeexCodec(info.sampleRate, info.bitRate, info.frameSize).use { codec ->
        frames.forEachIndexed { index, frame ->
            currentCoroutineContext().ensureActive()
            check(codec.decodeFrame(frame.asByteArray(), framePcm, hasHeaderByte = true) == SpeexDecodeResult.Success)
            framePcm.copyInto(output, index * framePcm.size)
        }
    }
    output
}
