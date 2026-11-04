package com.vrgames.tiangong.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Craft(val title: String, val book: String) { PAPER("造纸", "杀青"), PORCELAIN("制瓷", "陶埏"), DYE("染布", "彰施"), INK("制墨", "丹青"), GIFT("年礼", "") }

/** How cloth is folded before it is tied: each maps a point of the cloth to where it lies in the bundle. */
enum class Fold(val label: String) { HALF("对折"), QUARTER("四折"), FAN("扇形折"), TRIANGLE("三角折") }

/**
 * One step of an order. Each kind is a small game; each gives a score 0..1 when done (TAP steps
 * give none). [say] tells the player what to do and, read carefully, what the order wants.
 */
sealed class Step(val title: String, val say: String) {
    /** Pick the right one of [options] ([answer]). */
    class Choice(title: String, say: String, val options: List<String>, val answer: Int) : Step(title, say)

    /** A needle sweeps from 0 to 1 over [seconds]; stop it at [target] (within [tolerance] is perfect). */
    class Gauge(title: String, say: String, val target: Double, val tolerance: Double, val seconds: Double = 6.0, val low: String = "", val high: String = "") : Step(title, say)

    /** Strike [count] times on the beat (a marker bounces every [period] seconds; hit within [window] of the top). */
    class Rhythm(title: String, say: String, val count: Int, val period: Double = 0.9, val window: Double = 0.16) : Step(title, say)

    /** Throw the pot: set the radius of each of four rings (levels 1..6, bottom to mouth) to match [target]. */
    class Shape(title: String, say: String, val target: IntArray, val height: Double) : Step(title, say)

    /** Fold the cloth, tie bands at some of five places, dip it a number of times: match [target]. */
    class Tie(title: String, say: String, val target: Dye) : Step(title, say)

    /** Just do it (cut, press, dry): an animation, no score. */
    class Tap(title: String, say: String, val action: String) : Step(title, say)
}

/** A tie-dye recipe: the fold, which of the five band places are tied, how many dips (1..5). */
data class Dye(val fold: Fold, val bands: Set<Int>, val dips: Int) {
    /** The cloth's color at (u, v) in 0..1: 0 white, 1 the deepest indigo. */
    fun at(u: Double, v: Double): Double {
        val (fu, _) = folded(fold, u, v)
        var resist = 0.0
        for (b in bands) {
            val p = 0.1 + 0.2 * b
            val d = abs(fu - p)
            resist = max(resist, (1 - (d - 0.03) / 0.05).coerceIn(0.0, 1.0))
        }
        // The dye creeps a little into the tied parts: their edges are pale blue, not white.
        val depth = dips / 5.0
        return depth * (1 - 0.92 * resist)
    }

    /** The whole cloth on an [n]×[n] grid. */
    fun grid(n: Int = GRID): DoubleArray = DoubleArray(n * n) { k -> at((k % n + 0.5) / n, (k / n + 0.5) / n) }

    companion object {
        const val GRID = 48

        private fun tri(x: Double): Double { val f = x - kotlin.math.floor(x); return if (f < 0.5) f * 2 else (1 - f) * 2 }

        /**
         * Where (u, v) lies along the folded bundle, 0 at the fold (the cloth's middle) and 1 at its
         * outer edge: the bands are tied across this first coordinate.
         */
        fun folded(f: Fold, u: Double, v: Double): Pair<Double, Double> = when (f) {
            Fold.HALF -> abs(2 * u - 1) to v
            Fold.QUARTER -> max(abs(2 * u - 1), abs(2 * v - 1)) to min(abs(2 * u - 1), abs(2 * v - 1))
            Fold.FAN -> tri(u * 2) to v
            Fold.TRIANGLE -> {
                val dx = u - 0.5
                val dy = v - 0.5
                (hypot(dx, dy) * 1.4).coerceAtMost(1.0) to (atan2(dy, dx) / (2 * Math.PI) + 0.5)
            }
        }

        /** How alike two cloths are (1 the same). */
        fun likeness(a: DoubleArray, b: DoubleArray) = 1 - a.indices.sumOf { abs(a[it] - b[it]) } / a.size
    }
}

/** An order: what the customer wants and the steps to make it. */
class Order(val id: String, val craft: Craft, val title: String, val who: String, val want: String, val steps: List<Step>, val bonus: Boolean = false)

/** Making an order: the step reached, each step's score, and the quality made of them. */
class Work(val order: Order) {
    var at = 0; private set
    val scores = ArrayList<Double>()
    var hints = 0; private set
    val done get() = at >= order.steps.size
    val step get() = order.steps.getOrNull(at)

    // The state of the timed steps.
    var gaugeT = 0.0; private set
    var rhythmT = 0.0; private set
    var hits = 0; private set
    var misses = 0; private set
    val rings = IntArray(4) { 3 }
    var fold: Fold? = null
    val bands = HashSet<Int>()
    var dips = 1

    /** Advances timed steps by [dt]; a gauge that ran out stops at the end. */
    fun tick(dt: Double) {
        when (val s = step) {
            is Step.Gauge -> {
                gaugeT += dt
                if (gaugeT >= s.seconds) stopGauge()
            }
            is Step.Rhythm -> rhythmT += dt
            else -> Unit
        }
    }

    val gauge get() = (step as? Step.Gauge)?.let { (gaugeT / it.seconds).coerceIn(0.0, 1.0) } ?: 0.0

    /** Height of the rhythm marker 0..1 (1 at the top of its bounce). */
    val beat get() = (step as? Step.Rhythm)?.let { s -> val ph = (rhythmT % s.period) / s.period; 1 - abs(ph * 2 - 1) } ?: 0.0

    fun choose(i: Int): Boolean {
        val s = step as? Step.Choice ?: return false
        val ok = i == s.answer
        score(if (ok) 1.0 else 0.35)
        return ok
    }

    /** Stops the needle; returns the score. */
    fun stopGauge(): Double {
        val s = step as? Step.Gauge ?: return 0.0
        val x = gauge
        val d = abs(x - s.target)
        val sc = if (d <= s.tolerance) 1.0 else (1 - (d - s.tolerance) / (2 * s.tolerance)).coerceIn(0.0, 1.0)
        score(sc)
        return sc
    }

    /** A strike: on the beat or not. Returns whether it was. */
    fun strike(): Boolean {
        val s = step as? Step.Rhythm ?: return false
        val ph = (rhythmT % s.period) / s.period
        val fromTop = abs(ph - 0.5) * s.period
        val ok = fromTop <= s.window
        if (ok) hits++ else misses++
        if (hits >= s.count) score((hits.toDouble() / (hits + misses * 0.5)).coerceIn(0.0, 1.0))
        return ok
    }

    fun ring(i: Int, by: Int) { rings[i] = (rings[i] + by).coerceIn(1, 6) }

    /** The pot is shaped: scores how close the rings are to the order's. */
    fun shaped(): Double {
        val s = step as? Step.Shape ?: return 0.0
        val err = (0 until 4).sumOf { abs(rings[it] - s.target[it]).toDouble() } / 4
        val sc = (1 - err / 2.5).coerceIn(0.0, 1.0)
        score(sc)
        return sc
    }

    fun dye(): Dye? = fold?.let { Dye(it, bands.toSet(), dips) }

    /** The cloth is dyed and untied: scores how alike it is to the order's. */
    fun dyed(): Double {
        val s = step as? Step.Tie ?: return 0.0
        val d = dye() ?: return 0.0
        val like = Dye.likeness(d.grid(), s.target.grid())
        val sc = ((like - 0.75) / 0.25).coerceIn(0.0, 1.0)
        score(sc)
        return sc
    }

    fun tap() {
        if (step is Step.Tap) next()
    }

    /** A hint: what the order wants at this step; the third hint and on also does it right. */
    fun hint(): String {
        hints++
        return when (val s = step) {
            is Step.Choice -> "应该选「${s.options[s.answer]}」。"
            is Step.Gauge -> "指针走到 ${(s.target * 100).toInt()}% 左右时停。"
            is Step.Rhythm -> "标记跳到最高处时捶。"
            is Step.Shape -> "从下往上依次是 ${s.target.joinToString("、")} 档。"
            is Step.Tie -> "${s.target.fold.label}，在第 ${s.target.bands.sorted().joinToString("、") { (it + 1).toString() }} 道扎绳，染 ${s.target.dips} 次。"
            is Step.Tap -> "捏一下就好。"
            null -> ""
        }
    }

    private fun score(s: Double) {
        scores += s
        next()
    }

    private fun next() {
        at++
        gaugeT = 0.0
        rhythmT = 0.0
        hits = 0
        misses = 0
    }

    /** The finished piece's quality 0..1 (the scored steps' mean, a little less for each hint). */
    val quality get() = if (scores.isEmpty()) 1.0 else (scores.average() - hints * 0.04).coerceIn(0.0, 1.0)

    fun stars() = if (!done) 0 else Orders.stars(quality, hints)
}
