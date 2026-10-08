package com.guardianangel.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Round 95: cuts the start off one of her clips on the phone, without re-encoding, so an edge clip
 * starts 10 seconds before your tap (Clips.trimStartMs decides where). Nothing leaves the phone.
 * If anything goes wrong the clip is kept whole.
 */
object ClipTrim {
    /** The clip's length in milliseconds, or 0 if it can't be read. */
    fun durationMs(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    /** Keeps [file] from [startMs] to the end, in place. Returns true if it was cut. */
    fun cutStart(file: File, startMs: Long): Boolean {
        if (startMs <= 0) return false
        val out = File(file.parentFile, "trim_${file.name}.tmp")
        val ok = runCatching { copyFrom(file, out, startMs * 1_000) }.getOrDefault(false)
        if (ok && out.length() > 0 && out.renameTo(file)) return true
        out.delete()
        return false
    }

    private fun rotation(file: File): Int = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } finally {
            retriever.release()
        }
    }.getOrDefault(0)

    /** Copies video and sound from the keyframe at or just before [startUs] to the end. */
    private fun copyFrom(input: File, output: File, startUs: Long): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.path)
            val m = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val tracks = mutableMapOf<Int, Int>()
            var video = -1
            var bufferSize = 1 shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                if (mime.startsWith("video/")) video = i
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                tracks[i] = m.addTrack(format)
            }
            if (video < 0) return false
            m.setOrientationHint(rotation(input))

            // Where the video can start cleanly: its keyframe at or before the cut.
            extractor.selectTrack(video)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val from = extractor.sampleTime.coerceAtLeast(0)
            tracks.keys.filter { it != video }.forEach { extractor.selectTrack(it) }
            extractor.seekTo(from, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            m.start()
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                val track = tracks[extractor.sampleTrackIndex]
                if (track != null && time >= from) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = time - from
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    m.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            m.stop()
            return true
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}
