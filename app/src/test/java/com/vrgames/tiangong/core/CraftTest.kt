package com.vrgames.tiangong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every order can be made perfectly, mistakes cost quality, and the cloths tell their recipes apart. */
class CraftTest {
    /** Makes [o] as well as a careful player can (or with [sloppy] mistakes). */
    private fun make(o: Order, sloppy: Boolean = false): Work {
        val w = Work(o)
        var guard = 0
        while (!w.done && guard++ < 100000) {
            when (val s = w.step!!) {
                is Step.Tap -> w.advance(1.0)
                is Step.Choice -> w.choose(if (sloppy) (s.answer + 1) % s.options.size else s.answer)
                is Step.Gauge -> {
                    w.setGauge(if (sloppy) s.target + 3 * s.tolerance else s.target)
                    w.stopGauge()
                }
                is Step.Rhythm -> w.strike()
                is Step.Shape -> {
                    for (i in 0 until 4) while (w.rings[i] != s.target[i]) w.ring(i, if (w.rings[i] < s.target[i]) 1 else -1)
                    if (sloppy) w.ring(0, 2)
                    w.shaped()
                }
                is Step.Tie -> {
                    w.fold = s.target.fold
                    w.bands.clear(); w.bands.addAll(s.target.bands)
                    val n = s.target.dips.let { if (!sloppy) it else if (it > 2) it - 2 else it + 2 }
                    repeat(n) { w.dip() }
                    w.dyed()
                }
            }
        }
        return w
    }

    @Test
    fun everyOrderCanBeMadePerfectly() {
        for (o in Orders.ALL + Orders.GIFT + Orders.VASE) {
            val w = make(o)
            assertTrue(o.id, w.done)
            assertTrue("${o.id} quality ${w.quality}", w.quality >= 0.92)
            assertEquals(o.id, 3, w.stars())
        }
    }

    @Test
    fun mistakesCostQuality() {
        for (o in Orders.ALL) {
            val w = make(o, sloppy = true)
            assertTrue("${o.id} sloppy ${w.quality}", w.quality < 0.8)
        }
    }

    @Test
    fun clothsTellRecipesApart() {
        val targets = Orders.ALL.filter { it.craft == Craft.DYE }.map { (it.steps[0] as Step.Tie).target }
        for (t in targets) for (f in Fold.entries) if (f != t.fold) {
            val other = Dye(f, t.bands, t.dips)
            assertTrue("$t vs fold $f", Dye.likeness(t.grid(), other.grid()) < 0.95)
        }
        // A wrong band is visible too.
        val t = targets[1]
        assertTrue(Dye.likeness(t.grid(), Dye(t.fold, setOf(0), t.dips).grid()) < 0.95)
    }

    @Test
    fun ordersOpenInTurnAndTrophiesFollow() {
        val p = Progress()
        assertTrue(p.open(Orders.ALL[0]))
        assertTrue(!p.open(Orders.ALL[1]))
        for (o in Orders.ALL) p.record(make(o))
        assertTrue(p.open(Orders.GIFT))
        assertTrue(!p.bronze)
        p.giftGiven = true
        assertTrue(p.gold)
        val back = Progress.load(p.save())
        assertEquals(p.stars, back.stars)
        assertTrue(back.giftGiven)
    }

    @Test
    fun madePiecesSurviveSaving() {
        val m = Made()
        m.keep(Made.Paper(0.9, 0.3, true))
        m.keep(Made.Paper(0.5, 0.8, false))
        m.keep(Made.Pot(intArrayOf(2, 4, 5, 6), 0.07, 0, false, false, 0.78, 0.95))
        m.keep(Made.Pot(intArrayOf(2, 5, 4, 2), 0.18, 2, false, false, 0.8, 1.0), isVase = true)
        m.keep(Made.Cloth(Dye(Fold.FAN, setOf(1, 3), 5), 0.88))
        m.keep(Made.Ink(true, true, 0.97))
        val back = Made.load(m.save())
        assertEquals(0.9, back.paper!!.quality, 1e-9)
        assertEquals(listOf(2, 4, 5, 6), back.pot!!.rings.toList())
        assertEquals(listOf(2, 5, 4, 2), back.vase!!.rings.toList())
        assertEquals(Dye(Fold.FAN, setOf(1, 3), 5), back.cloth!!.dye)
        assertTrue(back.ink!!.dragon)
        assertEquals(null, Made.load(null).paper)
    }
}
