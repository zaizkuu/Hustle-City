package com.hustlecity.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Sfx { TAP, CASH, DOOR, CHIME, ERROR, CAR_DOOR, THUNDER }

/**
 * Procedural audio: a radio with generated music stations, sound effects and an engine hum,
 * mixed on one background thread into a mono 22 kHz AudioTrack. No sound files are shipped.
 */
object Audio {
    const val SR = 22050

    class Station(val name: String, val tracks: List<String>)

    val stations = listOf(
        Station("Lo-fi", listOf("Night Shift – Rooftop Rain", "Paper Cups – Slow Monday", "Kettle – Corner Office", "Mira Lune – Late Inventory")),
        Station("Pop", listOf("Jane & The Boy – Happier Without You", "The Leases – Rent Is Due", "Coco Vale – Open Sign", "Neon Avenue – Grand Opening")),
        Station("Jazz", listOf("Midtown Quartet – Blue Avenue", "Sal's Trio – Uncle's Advice", "Hudson Five – Interest Rates", "Vera Lane – Closing Time")),
        Station("Electro", listOf("Neonbyte – Overtime", "Gridlock – Express Delivery", "VOLT – Profit Season", "Subway Echo – Night Logistics")),
    )

    @Volatile var musicVolume = .55f
    @Volatile var sfxVolume = .8f
    @Volatile var radioOn = true
    @Volatile var station = 0
        private set
    @Volatile var trackIndex = 0
        private set
    /** 0..1 progress through the current track. */
    @Volatile var trackProgress = 0f
        private set
    /** Car engine throttle 0..1, or -1 when not driving. */
    @Volatile var engineLevel = -1f
    /** Rain on the streets 0..1 (quieter indoors). */
    @Volatile var rainLevel = 0f

    private val queue = ConcurrentLinkedQueue<FloatArray>()
    @Volatile private var running = false
    @Volatile private var pendingSkip = false
    @Volatile private var pendingStation = -1
    private var thread: Thread? = null
    private val sfxCache = HashMap<Sfx, FloatArray>()

    val nowPlaying: String get() = stations[station].tracks[trackIndex % stations[station].tracks.size]

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "HustleAudio").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    fun play(s: Sfx) {
        if (!running || sfxVolume <= 0f) return
        if (queue.size > 6) return
        queue += sfxCache.getOrPut(s) { renderSfx(s) }
    }

    fun nextTrack() { pendingSkip = true }

    fun setStation(i: Int) { pendingStation = ((i % stations.size) + stations.size) % stations.size }

    // ------------------------------------------------------------------ mixing thread

    private class Playing(val data: FloatArray) { var pos = 0 }

    private fun runLoop() {
        val track = try {
            val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(minBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) { running = false; return }
        if (track.state != AudioTrack.STATE_INITIALIZED) { track.release(); running = false; return }
        track.play()
        val n = 512
        val out = ShortArray(n)
        val mix = FloatArray(n)
        var music = MusicGen(station, trackIndex)
        val active = ArrayList<Playing>()
        var enginePhase = 0.0
        var engineFreq = 40.0
        var engineLp = 0.0
        var engineAmp = 0.0
        var masterLp = 0.0
        var rainAmp = 0.0
        var rainLp = 0.0
        var rainHp = 0.0
        var rainSeed = 12345
        var drip = 0.0
        try {
            while (running) {
                pendingStation.let { if (it >= 0) { station = it; trackIndex = 0; pendingStation = -1; music = MusicGen(station, trackIndex) } }
                if (pendingSkip) { pendingSkip = false; trackIndex++; music = MusicGen(station, trackIndex) }
                if (music.finished) { trackIndex++; music = MusicGen(station, trackIndex) }
                trackProgress = music.progress
                while (true) { val d = queue.poll() ?: break; active += Playing(d) }
                val mv = if (radioOn) musicVolume else 0f
                val sv = sfxVolume
                val eng = engineLevel
                val rainTarget = rainLevel.toDouble()
                for (i in 0 until n) {
                    var m = music.next() * mv
                    // engine hum
                    val targetAmp = if (eng >= 0f) .05 + .06 * eng else 0.0
                    engineAmp += (targetAmp - engineAmp) * .0008
                    if (engineAmp > 1e-4) {
                        engineFreq += ((38.0 + 75.0 * max(0f, eng)) - engineFreq) * .0005
                        enginePhase += engineFreq / SR
                        if (enginePhase >= 1.0) enginePhase -= 1.0
                        val raw = (enginePhase * 2 - 1) + .5 * (if (enginePhase < .5) 1.0 else -1.0)
                        engineLp += (raw - engineLp) * .08
                        m += (engineLp * engineAmp * sv).toFloat()
                    }
                    // rain: band-passed noise plus the odd heavier drop
                    rainAmp += (rainTarget - rainAmp) * .00005
                    if (rainAmp > 1e-3) {
                        rainSeed = rainSeed * 1103515245 + 12345
                        val white = ((rainSeed ushr 8) and 0xFFFF) / 32768.0 - 1.0
                        rainLp += (white - rainLp) * .35
                        rainHp += (rainLp - rainHp) * .02
                        if ((rainSeed ushr 20) and 1023 == 7) drip = .6
                        drip *= .995
                        m += (((rainLp - rainHp) * .55 + drip * white) * rainAmp * .28 * sv).toFloat()
                    }
                    var fx = 0f
                    for (p in active) if (p.pos < p.data.size) fx += p.data[p.pos++]
                    m += fx * sv
                    // gentle master low-pass and soft clip
                    masterLp += (m - masterLp) * .55
                    val y = masterLp.toFloat()
                    mix[i] = y / (1f + kotlin.math.abs(y))
                }
                active.removeAll { it.pos >= it.data.size }
                for (i in 0 until n) out[i] = (mix[i] * 30000f).toInt().coerceIn(-32768, 32767).toShort()
                track.write(out, 0, n)
            }
        } finally {
            runCatching { track.pause(); track.flush(); track.release() }
        }
    }

    // ------------------------------------------------------------------ synthesis helpers

    private val sineTable = FloatArray(2048) { sin(2 * PI * it / 2048).toFloat() }
    fun sine(phase: Double): Float = sineTable[((phase - kotlin.math.floor(phase)) * 2048).toInt() and 2047]
    fun midiFreq(m: Int): Double = 440.0 * 2.0.pow((m - 69) / 12.0)

    private fun renderSfx(s: Sfx): FloatArray {
        val rnd = Random(s.ordinal)
        fun buf(seconds: Double) = FloatArray((SR * seconds).toInt())
        return when (s) {
            Sfx.TAP -> buf(.045).also { b -> for (i in b.indices) { val t = i.toDouble() / SR; b[i] = sine(t * 1900) * exp(-t * 110).toFloat() * .35f } }
            Sfx.CASH -> buf(.7).also { b ->
                for (i in b.indices) {
                    val t = i.toDouble() / SR
                    val click = if (t < .02) (rnd.nextFloat() * 2 - 1) * (1 - t / .02).toFloat() * .4f else 0f
                    val bell1 = sine(t * 1568) * exp(-t * 7).toFloat() * .3f
                    val bell2 = if (t > .07) sine((t - .07) * 2093) * exp(-(t - .07) * 6).toFloat() * .3f else 0f
                    b[i] = click + bell1 + bell2
                }
            }
            Sfx.DOOR -> buf(.35).also { b ->
                var lp = 0f
                for (i in b.indices) {
                    val t = i.toDouble() / SR
                    lp += ((rnd.nextFloat() * 2 - 1) - lp) * .05f
                    b[i] = (sine(t * 70) * .6f + lp * 1.5f) * exp(-t * 14).toFloat() * .5f
                }
            }
            Sfx.THUNDER -> buf(2.8).also { b ->
                var lp = 0f; var lp2 = 0f
                for (i in b.indices) {
                    val t = i.toDouble() / SR
                    val w = rnd.nextFloat() * 2 - 1
                    lp += (w - lp) * .02f
                    lp2 += (lp - lp2) * .05f
                    val crack = if (t < .12) w * (1 - t / .12).toFloat() * .5f else 0f
                    val roll = (1 - exp(-t * 18)).toFloat() * exp(-t * 1.3).toFloat() * (1f + .4f * sine(t * 3.1))
                    b[i] = (crack * .4f + lp2 * 9f * roll) * .55f
                }
            }
            Sfx.CAR_DOOR -> buf(.25).also { b ->
                var lp = 0f
                for (i in b.indices) {
                    val t = i.toDouble() / SR
                    lp += ((rnd.nextFloat() * 2 - 1) - lp) * .12f
                    b[i] = (sine(t * 110) * .5f + lp) * exp(-t * 28).toFloat() * .55f
                }
            }
            Sfx.CHIME -> buf(.6).also { b ->
                for (i in b.indices) {
                    val t = i.toDouble() / SR
                    val a = sine(t * 1046.5) * exp(-t * 6).toFloat()
                    val c = if (t > .09) sine((t - .09) * 1568) * exp(-(t - .09) * 6).toFloat() else 0f
                    b[i] = (a + c) * .22f
                }
            }
            Sfx.ERROR -> buf(.28).also { b ->
                for (i in b.indices) {
                    val t = i.toDouble() / SR
                    val ph = t * 180
                    val sq = if (ph - kotlin.math.floor(ph) < .5) 1f else -1f
                    b[i] = sq * exp(-t * 9).toFloat() * .16f
                }
            }
        }
    }
}

/**
 * Generates one track of a station as an endless stream of samples: drums, bass, chords and a
 * seeded melody over a short chord progression. Tracks end after a fixed number of bars.
 */
class MusicGen(private val station: Int, track: Int) {
    private val rnd = Random(station * 1000 + track * 7919 + 13)
    private val bpm: Double
    private val swing: Double
    private val key: Int
    private val minor: Boolean
    private val progression: List<Pair<Int, IntArray>>
    private val bars = 48
    private val samplesPerStep: Double
    private var sample = 0L
    private var step = -1
    private val totalSteps get() = bars * 16
    private val melody = IntArray(32) { -1 }

    val finished get() = step >= totalSteps
    val progress get() = (step.coerceAtLeast(0) / totalSteps.toFloat()).coerceIn(0f, 1f)

    private class Voice(val freq: Double, val kind: Int, val amp: Float, val decay: Double, val attack: Double, val length: Int, val detune: Double = 0.0) {
        var t = 0
        var phase = 0.0
        var phase2 = 0.0
        var lp = 0f
    }

    private val voices = ArrayList<Voice>()
    private var noise = 0x2545F491

    companion object {
        const val SINE = 0; const val EP = 1; const val PLUCK = 2; const val SAW = 3; const val KICK = 4; const val SNARE = 5; const val HAT = 6; const val BASS = 7; const val PAD = 8
        private val MAJ7 = intArrayOf(0, 4, 7, 11); private val M7 = intArrayOf(0, 3, 7, 10); private val DOM7 = intArrayOf(0, 4, 7, 10)
        private val MAJ = intArrayOf(0, 4, 7); private val MIN = intArrayOf(0, 3, 7)
    }

    init {
        when (station) {
            0 -> { bpm = 78.0 + rnd.nextInt(10); swing = .12; minor = false; progression = listOf(2 to M7, 7 to DOM7, 0 to MAJ7, 9 to M7) }
            1 -> { bpm = 108.0 + rnd.nextInt(10); swing = 0.0; minor = false; progression = listOf(0 to MAJ, 7 to MAJ, 9 to MIN, 5 to MAJ) }
            2 -> { bpm = 120.0 + rnd.nextInt(20); swing = .3; minor = false; progression = listOf(2 to M7, 7 to DOM7, 0 to MAJ7, 9 to DOM7) }
            else -> { bpm = 120.0 + rnd.nextInt(6); swing = 0.0; minor = true; progression = listOf(0 to MIN, 8 to MAJ, 3 to MAJ, 10 to MAJ) }
        }
        key = 57 + rnd.nextInt(7)
        samplesPerStep = Audio.SR * 60.0 / bpm / 4.0
        val scale = if (minor) intArrayOf(0, 3, 5, 7, 10) else intArrayOf(0, 2, 4, 7, 9)
        // a two-bar motif on the 8th-note grid, repeated with variations
        var note = rnd.nextInt(scale.size)
        for (i in 0 until 32 step 2) {
            if (rnd.nextFloat() < (if (station == 2) .55f else .45f)) {
                note = (note + rnd.nextInt(-2, 3)).coerceIn(0, scale.size * 2 - 1)
                melody[i] = scale[note % scale.size] + 12 * (note / scale.size)
            }
        }
    }

    fun next(): Float {
        tick()
        var s = 0f
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            if (v.t >= v.length) { it.remove(); continue }
            s += render(v)
            v.t++
        }
        return s
    }

    private fun tick() {
        // swing delays every second 16th note
        val stepLen = samplesPerStep
        val pos = sample.toDouble()
        val nextStep = step + 1
        val offset = if (nextStep % 2 == 1) swing * stepLen else 0.0
        if (pos >= nextStep * stepLen + offset) {
            step = nextStep
            if (step < totalSteps) trigger(step)
        }
        sample++
    }

    private fun rand(): Float {
        noise = noise xor (noise shl 13); noise = noise xor (noise ushr 17); noise = noise xor (noise shl 5)
        return (noise and 0xFFFF) / 32768f - 1f
    }

    private fun add(midi: Int, kind: Int, amp: Float, decay: Double, attack: Double, seconds: Double, detune: Double = 0.0) {
        if (voices.size > 22) return
        voices += Voice(Audio.midiFreq(midi), kind, amp, decay, attack, (seconds * Audio.SR).toInt(), detune)
    }

    private fun trigger(step: Int) {
        val inBar = step % 16
        val bar = step / 16
        val chord = progression[bar % progression.size]
        val root = key + chord.first
        val beatLen = 60.0 / bpm
        val intro = bar < 2
        val outro = bar >= bars - 2
        when (station) {
            0 -> { // lo-fi
                if (!intro && (inBar == 0 || inBar == 10)) add(36, KICK, .55f, 9.0, 0.0, .35)
                if (!intro && (inBar == 4 || inBar == 12)) add(60, SNARE, .16f, 18.0, 0.0, .2)
                if (inBar % 2 == 0) add(90, HAT, if (inBar % 4 == 0) .05f else .035f, 45.0, 0.0, .06)
                if (inBar == 0) for (iv in chord.second) add(root + iv, EP, .07f, .9, .02, beatLen * 4)
                if (!intro && (inBar == 0 || inBar == 6)) add(root - 24, BASS, .26f, 1.6, .01, beatLen * 1.5)
                val m = melody[(step % 32)]
                if (!intro && !outro && m >= 0 && step % 2 == 0 && (bar / 4) % 2 == 1) add(key + 12 + m, EP, .06f, 2.5, .01, beatLen)
            }
            1 -> { // pop
                if (inBar % 4 == 0) add(36, KICK, .6f, 10.0, 0.0, .3)
                if (inBar == 4 || inBar == 12) add(62, SNARE, .22f, 16.0, 0.0, .2)
                if (inBar % 2 == 0) add(92, HAT, .04f, 50.0, 0.0, .05)
                if (inBar % 2 == 0) add(root - 24, BASS, .2f, 4.0, .005, beatLen * .5)
                if (inBar % 4 == 2) for (iv in chord.second) add(root + iv + 12, PLUCK, .045f, 7.0, .003, beatLen * .8)
                val m = melody[(step % 32)]
                if (!intro && !outro && m >= 0 && step % 2 == 0) add(key + 12 + m, PLUCK, .08f, 3.0, .005, beatLen * .9)
            }
            2 -> { // jazz
                if (inBar % 4 == 0 || inBar % 4 == 3) add(94, HAT, if (inBar % 4 == 0) .05f else .03f, 20.0, 0.0, .15)
                if (inBar % 4 == 0) {
                    val walk = intArrayOf(0, chord.second[1], chord.second[2], chord.second[1] + 2)
                    add(root - 24 + walk[(inBar / 4) % 4], BASS, .3f, 2.2, .01, beatLen)
                }
                if (inBar == 2 || (inBar == 11 && rnd.nextBoolean())) for (iv in chord.second) add(root + iv, EP, .055f, 3.5, .01, beatLen * 1.2)
                val m = melody[(step % 32)]
                if (!intro && !outro && m >= 0 && step % 2 == 0 && bar % 8 >= 4) add(key + 12 + m, SINE, .07f, 2.5, .01, beatLen * .9)
            }
            else -> { // electro
                if (inBar % 4 == 0) add(35, KICK, .65f, 11.0, 0.0, .3)
                if (inBar == 4 || inBar == 12) add(64, SNARE, .2f, 14.0, 0.0, .22)
                if (inBar % 4 == 2) add(96, HAT, .06f, 14.0, 0.0, .12)
                val arp = chord.second
                add(root - 12 + arp[inBar % arp.size], SAW, .07f, 9.0, .002, beatLen * .25)
                if (inBar == 0) for (iv in chord.second) add(root + iv, PAD, .03f, .25, .3, beatLen * 4, .004)
                val m = melody[(step % 32)]
                if (!intro && !outro && m >= 0 && step % 2 == 0 && bar % 8 >= 4) add(key + 12 + m, SAW, .05f, 4.0, .005, beatLen * .5)
            }
        }
    }

    private fun render(v: Voice): Float {
        val t = v.t.toDouble() / Audio.SR
        val env = (if (v.attack > 0 && t < v.attack) t / v.attack else 1.0) * exp(-t * v.decay) *
            (if (v.length - v.t < 200) (v.length - v.t) / 200.0 else 1.0)
        v.phase += v.freq / Audio.SR
        val ph = v.phase
        val out: Float = when (v.kind) {
            SINE -> Audio.sine(ph)
            EP -> Audio.sine(ph) + .35f * Audio.sine(ph * 2) * exp(-t * 6).toFloat()
            PLUCK -> {
                val sq = if (ph - kotlin.math.floor(ph) < .3) 1f else -1f
                v.lp += (sq - v.lp) * (.08f + .5f * exp(-t * 18).toFloat())
                v.lp
            }
            SAW -> {
                val saw = ((ph - kotlin.math.floor(ph)) * 2 - 1).toFloat()
                v.lp += (saw - v.lp) * (.12f + .4f * exp(-t * 10).toFloat())
                v.lp
            }
            PAD -> {
                v.phase2 += v.freq * (1 + v.detune) / Audio.SR
                ((ph - kotlin.math.floor(ph)) + (v.phase2 - kotlin.math.floor(v.phase2)) - 1).toFloat()
            }
            BASS -> Audio.sine(ph) + .25f * Audio.sine(ph * 2)
            KICK -> {
                // pitch sweeps down for a thump
                v.phase2 += (v.freq * (1 + 3 * exp(-t * 30))) / Audio.SR
                Audio.sine(v.phase2)
            }
            SNARE -> rand() * .8f + Audio.sine(ph) * .3f
            HAT -> {
                val n = rand()
                val hp = n - v.lp
                v.lp += (n - v.lp) * .5f
                hp
            }
            else -> 0f
        }
        return (out * env * v.amp).toFloat()
    }
}
