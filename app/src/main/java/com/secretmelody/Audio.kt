package com.secretmelody

import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Custom command from the app: the whole [AudioSettings] as a Bundle. */
const val CMD_AUDIO = "secretmelody.audio"

/** Sound options. [eqLevels] are millibels per band of the phone's own equalizer, empty = flat. */
data class AudioSettings(
    val skipSilence: Boolean = false,
    val evenVolume: Boolean = false,
    val eqOn: Boolean = false,
    val eqLevels: List<Int> = emptyList(),
)

fun AudioSettings.toJson(): String = JSONObject()
    .put("skipSilence", skipSilence).put("evenVolume", evenVolume).put("eqOn", eqOn)
    .put("eqLevels", JSONArray(eqLevels)).toString()

fun audioSettingsFrom(json: String): AudioSettings = runCatching {
    val o = JSONObject(json)
    val levels = o.optJSONArray("eqLevels")
    AudioSettings(
        o.optBoolean("skipSilence"), o.optBoolean("evenVolume"), o.optBoolean("eqOn"),
        (0 until (levels?.length() ?: 0)).map { levels!!.getInt(it) },
    )
}.getOrDefault(AudioSettings())

fun AudioSettings.toBundle() = Bundle().apply { putString("json", toJson()) }

fun audioSettingsFrom(b: Bundle) = audioSettingsFrom(b.getString("json").orEmpty())

/**
 * A plain file rather than SharedPreferences: the app writes it and the playback process (a separate process, which
 * would keep a stale SharedPreferences copy) reads it fresh each time it starts.
 */
object AudioStore {
    private fun file(dir: File) = File(dir, "audio.json")
    fun read(dir: File): AudioSettings = runCatching { audioSettingsFrom(file(dir).readText()) }.getOrDefault(AudioSettings())
    fun save(dir: File, s: AudioSettings) {
        runCatching {
            val tmp = File(dir, "audio.json.tmp")
            tmp.writeText(s.toJson())
            tmp.renameTo(file(dir))
        }
    }
}

/** Applies [AudioSettings] to the player's audio session. Lives in the playback process with the player. */
class AudioEffects(private val player: ExoPlayer) {
    private var eq: Equalizer? = null
    private var dyn: DynamicsProcessing? = null

    fun apply(s: AudioSettings) {
        player.skipSilenceEnabled = s.skipSilence
        val session = player.audioSessionId
        // Some phones refuse an effect (or have none); the rest of the settings still apply.
        if (s.eqOn || eq != null) {
            val e = eq ?: runCatching { Equalizer(0, session) }.getOrNull()?.also { eq = it }
            e?.let {
                runCatching {
                    val (lo, hi) = it.bandLevelRange.let { r -> r[0].toInt() to r[1].toInt() }
                    s.eqLevels.take(it.numberOfBands.toInt()).forEachIndexed { band, level ->
                        it.setBandLevel(band.toShort(), level.coerceIn(lo, hi).toShort())
                    }
                    it.enabled = s.eqOn
                }
            }
        }
        if (s.evenVolume || dyn != null) {
            val d = dyn ?: runCatching { DynamicsProcessing(0, session, evenVolumeConfig()) }.getOrNull()?.also { dyn = it }
            d?.let { runCatching { it.enabled = s.evenVolume } }
        }
    }

    fun release() {
        eq?.release()
        dyn?.release()
        eq = null
        dyn = null
    }
}

/**
 * "Even volume": a gentle one-band compressor lifts quiet songs and tames loud ones, then a limiter stops clipping.
 * shortcut: compresses the sound rather than reading ReplayGain tags (files converted from YouTube have none);
 * switch to per-track gain if people hear the compression.
 */
private fun evenVolumeConfig(): DynamicsProcessing.Config {
    val config = DynamicsProcessing.Config.Builder(
        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
        false, 0, true, 1, false, 0, true,
    ).build()
    val mbc = DynamicsProcessing.Mbc(true, true, 1)
    mbc.getBand(0).apply {
        cutoffFrequency = 20_000f
        attackTime = 20f
        releaseTime = 400f
        ratio = 3f
        threshold = -26f
        kneeWidth = 8f
        noiseGateThreshold = -90f
        expanderRatio = 1f
        preGain = 0f
        postGain = 7f
    }
    config.setMbcAllChannelsTo(mbc)
    config.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
    return config
}

/** What this phone's equalizer offers, read from a throwaway effect so the app needs no player for it. */
data class EqInfo(val bandHz: List<Int>, val minLevel: Int, val maxLevel: Int, val presets: List<Pair<String, List<Int>>>)

fun readEqInfo(audio: AudioManager): EqInfo? = runCatching {
    val e = Equalizer(0, audio.generateAudioSessionId())
    try {
        val bands = e.numberOfBands.toInt()
        val presets = (0 until e.numberOfPresets).map { p ->
            e.usePreset(p.toShort())
            e.getPresetName(p.toShort()) to (0 until bands).map { e.getBandLevel(it.toShort()).toInt() }
        }
        EqInfo((0 until bands).map { e.getCenterFreq(it.toShort()) / 1000 }, e.bandLevelRange[0].toInt(), e.bandLevelRange[1].toInt(), presets)
    } finally {
        e.release()
    }
}.getOrNull()

/** "60 Hz", "14 kHz". */
fun formatHz(hz: Int): String = if (hz >= 1000) "${(hz + 500) / 1000} kHz" else "$hz Hz"
