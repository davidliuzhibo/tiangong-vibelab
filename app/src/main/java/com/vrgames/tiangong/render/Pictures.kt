package com.vrgames.tiangong.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.vrgames.tiangong.core.Dye

/** A tie-dyed cloth as a picture: white with indigo where the dye took. */
fun clothBitmap(d: Dye, n: Int = 128): Bitmap {
    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    for (y in 0 until n) for (x in 0 until n) b.setPixel(x, y, indigoPixel(d.at((x + 0.5) / n, (y + 0.5) / n)))
    return b
}

/** Indigo of depth [c] (0 white .. 1 deepest) on cotton. */
fun indigoPixel(c: Double): Int {
    val r = (245 - c * 222).toInt(); val g = (243 - c * 196).toInt(); val bl = (236 - c * 120).toInt()
    return (255 shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
}

/**
 * The pot's outline as the order draws it: [rings] (levels 1..6, bottom to mouth) filled in white,
 * and [target] dashed in gold over it, so the two can be compared side by side. The four ring
 * heights are marked, numbered from the bottom.
 */
fun potOutline(rings: IntArray, target: IntArray?, w: Int = 240, h: Int = 220): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(b)
    val ys = floatArrayOf(0f, 0.3f, 0.65f, 0.95f)
    val base = h - 14f
    val top = 18f
    fun y(t: Float) = base - (base - top) * t
    fun x(level: Int) = 12f + level * 16f
    fun path(r: IntArray): Path {
        val p = Path()
        p.moveTo(w / 2f - x(r[0]), y(0f))
        for (k in 1 until 4) p.lineTo(w / 2f - x(r[k]), y(ys[k]))
        p.lineTo(w / 2f + x(r[3]), y(ys[3]))
        for (k in 2 downTo 0) p.lineTo(w / 2f + x(r[k]), y(ys[k]))
        p.close()
        return p
    }
    val guide = Paint().apply { color = 0x55FFFFFF; strokeWidth = 1.5f }
    val label = Paint().apply { color = 0x99FFFFFF.toInt(); textSize = 15f; isAntiAlias = true }
    for (k in 0 until 4) {
        cv.drawLine(8f, y(ys[k]), w - 8f, y(ys[k]), guide)
        cv.drawText("${k + 1}", 2f, y(ys[k]) - 3f, label)
    }
    cv.drawPath(path(rings), Paint().apply { color = 0xFFEDEAE2.toInt(); isAntiAlias = true })
    if (target != null) cv.drawPath(path(target), Paint().apply {
        color = 0xFFE6C06A.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true; pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    })
    return b
}
