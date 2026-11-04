package com.vrgames.tiangong.core

/**
 * What the player has made, best of each craft (kept for the gift, which uses the player's own
 * paper, ink, cloth and bowl, and for the cabinet). Saved as one line.
 */
class Made {
    class Paper(val quality: Double, val thick: Double, val young: Boolean)
    class Pot(val rings: IntArray, val height: Double, val pattern: Int, val celadon: Boolean, val redClay: Boolean, val fired: Double, val quality: Double)
    class Ink(val glossy: Boolean, val dragon: Boolean, val quality: Double)
    class Cloth(val dye: Dye, val quality: Double)

    var paper: Paper? = null
    var pot: Pot? = null
    var cloth: Cloth? = null
    var ink: Ink? = null
    var vase: Pot? = null

    /** Keeps [p] if it is better than what was kept. */
    fun keep(p: Paper) { if (p.quality >= (paper?.quality ?: -1.0)) paper = p }
    fun keep(p: Pot, isVase: Boolean = false) { if (isVase) { if (p.quality >= (vase?.quality ?: -1.0)) vase = p } else if (p.quality >= (pot?.quality ?: -1.0)) pot = p }
    fun keep(c: Cloth) { if (c.quality >= (cloth?.quality ?: -1.0)) cloth = c }
    fun keep(i: Ink) { if (i.quality >= (ink?.quality ?: -1.0)) ink = i }

    fun save(): String = listOf(
        paper?.let { "p:${it.quality}:${it.thick}:${it.young}" },
        pot?.let { "c:${pot(it)}" },
        vase?.let { "v:${pot(it)}" },
        cloth?.let { "d:${it.dye.fold.name}:${it.dye.bands.sorted().joinToString(".")}:${it.dye.dips}:${it.quality}" },
        ink?.let { "i:${it.glossy}:${it.dragon}:${it.quality}" },
    ).filterNotNull().joinToString("|")

    private fun pot(p: Pot) = "${p.rings.joinToString(".")}:${p.height}:${p.pattern}:${p.celadon}:${p.redClay}:${p.fired}:${p.quality}"

    companion object {
        fun load(s: String?) = Made().apply {
            for (e in (s ?: "").split("|")) runCatching {
                val f = e.split(":")
                when (f[0]) {
                    "p" -> paper = Paper(f[1].toDouble(), f[2].toDouble(), f[3].toBoolean())
                    "c", "v" -> Pot(f[1].split(".").map { it.toInt() }.toIntArray(), f[2].toDouble(), f[3].toInt(), f[4].toBoolean(), f[5].toBoolean(), f[6].toDouble(), f[7].toDouble())
                        .let { if (f[0] == "c") pot = it else vase = it }
                    "d" -> cloth = Cloth(Dye(Fold.valueOf(f[1]), f[2].split(".").filter { it.isNotEmpty() }.map { it.toInt() }.toSet(), f[3].toInt()), f[4].toDouble())
                    "i" -> ink = Ink(f[1].toBoolean(), f[2].toBoolean(), f[3].toDouble())
                }
            }
        }
    }
}
