package ir.pardava.mobile.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Voice recorder for the support chat — AAC in an .m4a container (the exact
 * format the server accepts). Files live in cacheDir until they are uploaded;
 * callers delete them after a successful send.
 */
class ChatAudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    /** Start recording; returns the output file or null when the mic fails. */
    fun start(): File? {
        if (recorder != null) return null
        val file = File(
            context.cacheDir,
            "chat-voice-" + System.currentTimeMillis() + ".m4a",
        )
        val r = if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("USELESS_CAST")
            MediaRecorder(context) as MediaRecorder
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outFile = file
            file
        } catch (_: Exception) {
            try {
                r.release()
            } catch (_: Exception) {
            }
            file.delete()
            null
        }
    }

    /** Stop and return the finished file (null when it never started). */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val file = outFile
        outFile = null
        return try {
            r.stop()
            file
        } catch (_: Exception) {
            file?.delete()
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Stop without keeping the file. */
    fun cancel() {
        stop()?.delete()
    }

    /** Last relative loudness (0..~32767) for the level pulse. */
    fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }
}

/**
 * One-at-a-time audio player for chat voice messages. Authenticated URLs need
 * a Bearer header, so playback goes through MediaPlayer directly.
 */
class ChatAudioPlayer : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    var onStateChanged: ((playing: Boolean, positionMs: Int) -> Unit)? = null
    var onDone: (() -> Unit)? = null

    private var player: MediaPlayer? = null
    private var playingUrl: String? = null

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    fun toggle(context: Context, url: String, bearer: String?) {
        if (playingUrl == url && player != null) {
            if (player?.isPlaying == true) {
                player?.pause()
                onStateChanged?.invoke(false, 0)
            } else {
                player?.start()
                onStateChanged?.invoke(true, 0)
            }
            return
        }
        stop()
        playingUrl = url
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            val headers = bearer?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
            p.setDataSource(context, Uri.parse(url), headers)
            p.setOnCompletionListener(this)
            p.setOnErrorListener(this)
            p.prepareAsync()
            p.setOnPreparedListener { mp ->
                mp.start()
                onStateChanged?.invoke(true, 0)
            }
            player = p
        } catch (_: Exception) {
            stop()
            onDone?.invoke()
        }
    }

    fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        playingUrl = null
        onStateChanged?.invoke(false, 0)
    }

    override fun onCompletion(mp: MediaPlayer) {
        mp.release()
        player = null
        playingUrl = null
        onDone?.invoke()
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        mp.release()
        player = null
        playingUrl = null
        onDone?.invoke()
        return true
    }
}

/* ─────────────── time helpers: server UTC → Tehran, Jalali chips ─────────────── */

private const val TEHRAN_OFFSET_SECONDS = 3 * 3600 + 30 * 60 // fixed +03:30 since 2022

/**
 * Server timestamps are UTC `YYYY-MM-DD HH:MM:SS`. Returns the Tehran clock
 * `HH:MM` for the bubble meta row.
 */
fun tehranClock(raw: String?, lang: String): String? {
    if (raw == null || raw.length < 16) return null
    val hh = raw.substring(11, 13).toIntOrNull() ?: return null
    val mm = raw.substring(14, 16).toIntOrNull() ?: return null
    var total = (hh * 3600 + mm * 60 + TEHRAN_OFFSET_SECONDS) % 86_400
    val th = total / 3600
    val tm = (total % 3600) / 60
    val text = String.format(Locale.US, "%02d:%02d", th, tm)
    return Fmt.digits(text, lang)
}

/** Gregorian → Jalali (the standard FarsiWeb algorithm, exact leap handling). */
private fun gregorianToJalali(gyIn: Int, gmIn: Int, gdIn: Int): IntArray {
    val gDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val jDays = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
    val gy = gyIn - 1600
    val gm = gmIn - 1
    val gd = gdIn - 1
    var gDayNo = 365L * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    for (i in 0 until gm) gDayNo += gDays[i]
    if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo++
    gDayNo += gd
    var jDayNo = gDayNo - 79
    val jNp = (jDayNo / 12053).toInt()
    jDayNo %= 12053
    var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461).toInt()
    jDayNo %= 1461
    if (jDayNo >= 366) {
        jDayNo--
        jy += (jDayNo / 365).toInt()
        jDayNo %= 365
    }
    var jm = 0
    while (jm < 11 && jDayNo >= jDays[jm]) {
        jDayNo -= jDays[jm]
        jm++
    }
    return intArrayOf(jy.toInt(), jm + 1, (jDayNo + 1).toInt())
}

private val JALALI_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

/**
 * Server UTC date `YYYY-MM-DD` (or full timestamp) → Tehran date parts
 * (also shifts when the +3:30 crosses midnight).
 */
private fun tehranDateParts(raw: String?): Triple<Int, Int, Int>? {
    if (raw == null || raw.length < 10) return null
    val y = raw.substring(0, 4).toIntOrNull() ?: return null
    val m = raw.substring(5, 7).toIntOrNull() ?: return null
    val d = raw.substring(8, 10).toIntOrNull() ?: return null
    val hh = if (raw.length >= 13) (raw.substring(11, 13).toIntOrNull() ?: 0) else 0
    val mm = if (raw.length >= 16) (raw.substring(14, 16).toIntOrNull() ?: 0) else 0
    val shifted = (hh * 3600 + mm * 60 + TEHRAN_OFFSET_SECONDS) / 86_400
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply { clear() }
    cal.set(y, m - 1, d)
    cal.add(Calendar.DAY_OF_MONTH, shifted)
    return Triple(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}

/** Today's date in Tehran (y, m, d) for «امروز/دیروز» chips. */
fun tehranToday(): Triple<Int, Int, Int> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"), Locale.US)
    return Triple(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}

/**
 * Day-chip label for a message timestamp: «امروز» / «دیروز» / «۲۴ شهریور».
 */
fun chatDayLabel(raw: String?, lang: String): String? {
    val parts = tehranDateParts(raw) ?: return null
    val today = tehranToday()
    val yesterday = run {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(today.first, today.second - 1, today.third)
            add(Calendar.DAY_OF_MONTH, -1)
        }
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    return when {
        parts == today -> null // «امروز» chip is skipped — same day is obvious
        parts == yesterday -> if (lang == "fa") "دیروز" else "Yesterday"
        else -> {
            val (jy, jm, jd) = gregorianToJalali(parts.first, parts.second, parts.third)
            if (lang == "fa") {
                Fmt.digits("${jd} ${JALALI_MONTHS[jm - 1]}", lang)
            } else {
                "${parts.third}/${parts.second}/${parts.first}"
            }
        }
    }
}
