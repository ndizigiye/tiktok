package com.ndizigiye.loop.server.seed

import com.ndizigiye.loop.server.media.MediaTools
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Renders demo media with ffmpeg's built-in test sources: portrait 540x960 H.264 + AAC,
 * yuv420p and +faststart so clips stream. Output is written to a temporary file and renamed,
 * so an existing target is always complete and can be skipped on the next run.
 */
class ClipRenderer(private val tools: MediaTools) {
    /** Renders [clip] with [tone] as its audio into [target]; returns an error message on failure. */
    suspend fun renderClip(clip: DemoClip, tone: String, target: File): String? {
        val seconds = clip.seconds.toString()
        val fadeOutStart = "%.2f".format(Locale.ROOT, clip.seconds - FADE_OUT_SECONDS)
        val audio = "aformat=sample_rates=44100:channel_layouts=stereo,afade=t=in:d=0.4," +
            "afade=t=out:st=$fadeOutStart:d=$FADE_OUT_SECONDS"
        val (source, graph) = when (val visual = clip.visual) {
            is Visual.Pattern -> visual.source to
                "[0:v]${visual.filters}$FINISH[v];[1:a]$audio[a]"
            is Visual.Waveform -> visual.background to
                "[1:a]$audio,asplit[a][w];" +
                "[w]showwaves=s=540x420:mode=cline:rate=30:scale=cbrt:draw=full:colors=${visual.color}[waves];" +
                "[0:v][waves]overlay=0:270:shortest=1,$FINISH[v]"
        }
        return render(
            target,
            listOf(
                "-f", "lavfi", "-t", seconds, "-i", source,
                "-f", "lavfi", "-t", seconds, "-i", tone,
                "-filter_complex", graph,
                "-map", "[v]", "-map", "[a]",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", "-profile:v", "high",
                "-pix_fmt", "yuv420p", "-g", "30",
                "-c:a", "aac", "-b:a", "96k",
                "-t", seconds, "-shortest", "-movflags", "+faststart", "-f", "mp4",
            ),
        )
    }

    /** A 256 px square gradient avatar. */
    suspend fun renderAvatar(colors: Pair<String, String>, seed: Int, target: File): String? = render(
        target,
        listOf(
            "-f", "lavfi",
            "-i", "gradients=s=256x256:c0=${colors.first}:c1=${colors.second}:nb_colors=2:type=radial:seed=$seed",
            "-frames:v", "1", "-q:v", "3", "-f", "image2", "-c:v", "mjpeg",
        ),
    )

    private suspend fun render(target: File, args: List<String>): String? {
        val partial = File(target.parentFile, "${target.name}.part")
        val result = tools.runFfmpeg(args + partial.absolutePath, if (target.extension == "mp4") CLIP_TIMEOUT else IMAGE_TIMEOUT)
        if (result?.ok != true || partial.length() == 0L) {
            partial.delete()
            return result?.stderr?.trim()?.ifEmpty { null } ?: "ffmpeg failed"
        }
        if (!partial.renameTo(target)) {
            partial.delete()
            return "could not move ${partial.name} into place"
        }
        return null
    }

    private companion object {
        const val FINISH = "fps=30,scale=540:960,setsar=1,format=yuv420p"
        const val FADE_OUT_SECONDS = 0.6
        val CLIP_TIMEOUT = 5.minutes
        val IMAGE_TIMEOUT = 30.seconds
    }
}
