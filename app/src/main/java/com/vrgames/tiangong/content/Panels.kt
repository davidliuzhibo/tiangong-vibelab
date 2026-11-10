package com.vrgames.tiangong.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.tiangong.BOOK_ID
import com.vrgames.tiangong.ORDERS_ID
import com.vrgames.tiangong.core.Craft
import com.vrgames.tiangong.core.Fold
import com.vrgames.tiangong.core.Orders
import com.vrgames.tiangong.core.Step
import com.vrgames.tiangong.game.Game
import com.vrgames.tiangong.render.clothBitmap
import com.vrgames.tiangong.render.potOutline

const val PANEL_CARD = "card"
const val PANEL_ORDER = "order"
const val PANEL_STEP = "step"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

val Ink = Color(0xFFF2EEE6)
val Indigo = Color(0xFF9DB8E8)
val Gilt = Color(0xFFE6C06A)
val Dim = Color(0xFFBCB6AA)
val Rust = Color(0xFFE07A5A)
private val PanelBg = Color(0xE61C1814)
val WindowBg = Color(0xF21C1814)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Column(Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0012f), Color(0x66E6C06A), shape).padding(c.m(0.012f)), verticalArrangement = Arrangement.spacedBy(c.m(0.006f))) { content() }
}

@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(text, Modifier.clip(shape).background(Gilt).border(c.m(0.0018f), Color(0xFFFFF1C9), shape).clickable { onClick() }.padding(horizontal = c.m(0.016f), vertical = c.m(0.009f)), fontSize = c.sz(0.018f), color = Color(0xFF1B1408))
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.009f))
    Text(
        text,
        Modifier.clip(shape).background(if (selected) color.copy(alpha = 0.3f) else Color.Transparent).border(c.m(if (selected) 0.0024f else 0.0014f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.01f), vertical = c.m(0.0065f)),
        fontSize = c.sz(0.014f), color = color,
    )
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val pr = Game.progress
    PanelBox(c, 0.46f) {
        Text("天工小作坊", fontSize = c.sz(0.03f), color = Gilt)
        Text("明朝的一间小作坊开张了。照着《天工开物》里的工序，给客人造纸、烧瓷、染布、制墨；年底，再用自己做的东西备一份年礼。", fontSize = c.sz(0.015f), color = Ink)
        Text("怎么玩", fontSize = c.sz(0.017f), color = Indigo)
        Text("· 每张订单写着客人要什么。读仔细：选料、火候、花纹都藏在订单里。\n· 左边的面板一步步告诉你做什么。要选的，点选项；要掐时机的，指针走进绿色区域时捏一下工位（或点「停」）。\n· 捶打：小球跳到最高、碰到光环时捏一下。\n· 扎染要先想好：怎么折、在哪扎绳、染几次，打开才是订单上的花纹。", fontSize = c.sz(0.014f), color = Ink)
        if (pr.stars > 0) Text("已得 ${pr.stars} / ${pr.maxStars} 颗星。", fontSize = c.sz(0.014f), color = Indigo)
        CallToAction(c, if (pr.stars > 0) "继续接单 →" else "开张 →") { Game.start() }
    }
}

@Composable
fun OrderPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val o = Game.order
    PanelBox(c, 0.46f) {
        Text(if (o.craft == Craft.GIFT) "终章 · 年礼" else "${o.craft.title}订单 ·「${o.title}」", fontSize = c.sz(0.026f), color = Gilt)
        Text("客人：${o.who}", fontSize = c.sz(0.014f), color = Dim)
        Text("「${o.want}」", fontSize = c.sz(0.016f), color = Ink)
        Text("工序：" + o.steps.joinToString(" → ") { it.title }, fontSize = c.sz(0.013f), color = Indigo)
        // The order's drawing: the pot's outline, or the cloth's pattern.
        val shape = o.steps.firstOrNull { it is Step.Shape } as? Step.Shape
        val tie = o.steps.firstOrNull { it is Step.Tie } as? Step.Tie
        if (shape != null) {
            val pic = remember(o.id) { potOutline(shape.target, null).asImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
                Image(pic, "图样", Modifier.size(c.m(0.1f)))
                Text("图样：瓶身的轮廓，从底到口四道。", fontSize = c.sz(0.013f), color = Dim)
            }
        }
        if (tie != null) {
            val pic = remember(o.id) { clothBitmap(tie.target, 96).asImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
                Image(pic, "图样", Modifier.size(c.m(0.1f)))
                Text("图样：染好、拆开后的样子。", fontSize = c.sz(0.013f), color = Dim)
            }
        }
        if (o.craft != Craft.GIFT) Text("三星：每道工序都做到位（品质 92% 以上），不用提示。", fontSize = c.sz(0.013f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            CallToAction(c, "开工 →") { Game.begin() }
            Pill(c, if (Game.speaking) "停止讲解" else "听《天工开物》讲解", Indigo) { Game.toggleVoice() }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

/** The step being done and its controls; the left-hand panel while making. */
@Composable
fun StepPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val w = Game.work ?: return
    val o = Game.order
    PanelBox(c, 0.3f) {
        Text("${o.title}　${(w.at + 1).coerceAtMost(o.steps.size)} / ${o.steps.size}", fontSize = c.sz(0.014f), color = Dim)
        val s = w.step
        if (s == null || Game.phase != Game.Phase.MAKING) {
            Text("完成！", fontSize = c.sz(0.022f), color = Gilt)
            Game.lastVerdict?.let { Text(it, fontSize = c.sz(0.013f), color = Indigo) }
            return@PanelBox
        }
        Text(s.title, fontSize = c.sz(0.024f), color = Gilt)
        Text(s.say, fontSize = c.sz(0.0135f), color = Ink)
        when (s) {
            is Step.Choice -> Column(verticalArrangement = Arrangement.spacedBy(c.m(0.005f))) {
                s.options.forEachIndexed { i, opt -> Pill(c, opt, Ink) { Game.choose(i) } }
            }
            is Step.Gauge -> {
                Text("工位上方的刻度条：左边${s.low}，右边${s.high}，绿色是订单要的。现在 ${(w.gauge * 100).toInt()}%。慢慢调，不限时间。", fontSize = c.sz(0.012f), color = Dim)
                if (s.tool != "竹帘") CallToAction(c, GAUGE_DONE[s.title] ?: "定了 →") { Game.stop() }
            }
            is Step.Rhythm -> {
                Text("已捶 ${w.hits} / ${s.count}", fontSize = c.sz(0.016f), color = Ink)
                Text("捏住${s.tool}往下砸到底再抬起来算一下；也可以松手让它弹回去再捏。", fontSize = c.sz(0.012f), color = Dim)
            }
            is Step.Shape -> ShapeControls(c, w.rings, s)
            is Step.Tie -> TieControls(c)
            is Step.Tap -> {
                Text(
                    when (s.gesture) {
                        Step.Gesture.STROKES -> "已经 ${(w.progress * s.strokes + 0.01).toInt()} / ${s.strokes} 下"
                        Step.Gesture.RUB -> "做了 ${(w.progress * 100).toInt()}%"
                        Step.Gesture.CARRY -> "拖到发亮的金圈里松开。"
                    },
                    fontSize = c.sz(0.015f), color = Ink,
                )
            }
        }
        Game.lastVerdict?.let { Text(it, fontSize = c.sz(0.013f), color = Indigo) }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.005f))) {
            Pill(c, "提示（扣星）", Gilt) { Game.hint() }
            Pill(c, if (Game.speaking) "停止讲解" else "听讲解", Indigo) { Game.toggleVoice() }
            Pill(c, "订单簿", Indigo) { toggle(ORDERS_ID) }
            Pill(c, "图谱") { toggle(BOOK_ID) }
        }
    }
}

@Composable
private fun ShapeControls(c: PhysicalLengthConverter, rings: IntArray, s: Step.Shape) {
    val pic = potOutline(rings, s.target).asImageBitmap()
    Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
        Image(pic, "轮廓", Modifier.size(c.m(0.11f)))
        Column(verticalArrangement = Arrangement.spacedBy(c.m(0.004f))) {
            for (i in 3 downTo 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.004f))) {
                Text("${i + 1} ${RING_NAMES[i]}", Modifier.width(c.m(0.04f)), fontSize = c.sz(0.012f), color = Dim)
                Text("${rings[i]} 档", fontSize = c.sz(0.014f), color = if (rings[i] == s.target[i]) Indigo else Gilt)
            }
        }
    }
    Text("白色是你的坯，金色虚线是图样。捏住坯上的金色小珠：往右拉放宽，往左推收窄。", fontSize = c.sz(0.012f), color = Dim)
    CallToAction(c, "拉好了 →") { Game.shaped() }
}

private val RING_NAMES = listOf("底", "腹", "肩", "口")

private val GAUGE_DONE = mapOf("蒸煮" to "火候定了 →", "烧窑" to "封窑 →", "烧烟" to "灯芯定了 →")

@Composable
private fun TieControls(c: PhysicalLengthConverter) {
    val w = Game.work ?: return
    Text("折法", fontSize = c.sz(0.013f), color = Dim)
    Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.004f))) {
        for (f in Fold.entries) Pill(c, f.label, Ink, selected = w.fold == f) { Game.fold(f) }
    }
    Text(FOLD_NOTES[w.fold] ?: "先选一种折法，工位前会摆出折好的布。", fontSize = c.sz(0.012f), color = Dim)
    Text("扎绳：" + if (w.bands.isEmpty()) "还没扎。把绳圈拖到布束上松开（左端红签是折痕，第 1 道最靠近它）。" else "已扎第 ${w.bands.sorted().joinToString("、") { (it + 1).toString() }} 道。", fontSize = c.sz(0.013f), color = Dim)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
        Text("已浸染缸 ${w.dips} 次（最多 5 次）", fontSize = c.sz(0.014f), color = Ink)
        Spacer(Modifier.width(c.m(0.01f)))
        CallToAction(c, "拆开看看 →") { Game.dye() }
    }
}

private val FOLD_NOTES = mapOf(
    Fold.HALF to "对折：沿竖中线对折一次，左右两半一样，花纹是竖条。",
    Fold.QUARTER to "四折：对折再对折，折角在布的正中，花纹是一圈圈方框。",
    Fold.FAN to "扇形折：像折扇一样来回折，花纹是重复的竖条。",
    Fold.TRIANGLE to "三角折：从中心捏起拧成一束，花纹是一圈圈圆环。",
)

@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val w = Game.work ?: return
    val pr = Game.progress
    val o = Game.order
    PanelBox(c, 0.46f) {
        if (o.craft == Craft.GIFT) {
            Text("年礼送出去了", fontSize = c.sz(0.026f), color = Gilt)
            Text("四门手艺的最好品质：" + Orders.CRAFTS.joinToString("，") { "${it.title} ${(pr.bestOf(it) * 100).toInt()}%" }, fontSize = c.sz(0.014f), color = Ink)
        } else {
            Text("「${o.title}」　${stars(w.stars())}", fontSize = c.sz(0.026f), color = Gilt)
            Text("品质 ${(w.quality * 100).toInt()}%" + if (w.hints > 0) "（提示 ${w.hints} 次）" else "", fontSize = c.sz(0.015f), color = Ink)
            val scored = o.steps.filter { it !is Step.Tap }
            Text(scored.zip(w.scores).joinToString("　") { (s, sc) -> "${s.title} ${(sc * 100).toInt()}" }, fontSize = c.sz(0.013f), color = Dim)
            if (w.stars() < 3) Text("三星：每道工序都做到位（品质 92% 以上），不用提示。", fontSize = c.sz(0.013f), color = Dim)
        }
        val t = Game.trophies
        if (t > Game.trophiesBefore) Text(
            when (t) {
                1 -> "得到铜奖杯「巧手」木牌！奖杯台上的布揭开了。"
                2 -> "得到银奖杯！右后方多了一个展柜，摆着你做得最好的纸、瓷、布、墨。"
                else -> "得到金奖杯！门楣挂上了「天工」金匾，订单簿里还来了一位神秘客人，并有一幅珍藏长卷。"
            },
            fontSize = c.sz(0.016f), color = Indigo,
        )
        Text("总星数 ${pr.stars} / ${pr.maxStars}", fontSize = c.sz(0.014f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f)), verticalAlignment = Alignment.CenterVertically) {
            val next = pr.next()
            if (next != null) CallToAction(c, if (next.id == "gift") "备年礼 →" else "下一单 →") { Game.next() }
            if (pr.gold && !pr.done(Orders.VASE.id)) CallToAction(c, "神秘客人 →") { Game.open(Orders.VASE) }
            if (o.craft != Craft.GIFT) Pill(c, "重做") { Game.again() }
            Pill(c, "订单簿", Indigo) { toggle(ORDERS_ID) }
        }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(n, Modifier.widthIn(max = c.m(0.5f)).clip(shape).background(PanelBg).padding(c.m(0.009f)), fontSize = c.sz(0.015f), color = Gilt)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Gilt) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.24f) {
        Pill(c, if (Game.window == ORDERS_ID) "收起订单簿" else "订单簿（选订单、奖杯）", Gilt) { toggle(ORDERS_ID) }
        Pill(c, if (Game.window == BOOK_ID) "收起天工开物图谱" else "天工开物图谱") { toggle(BOOK_ID) }
        Pill(c, "把作坊摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        if (Game.confirmRestart) {
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.005f))) {
                Pill(c, "确定清空进度", Rust) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
                Pill(c, "算了") { Game.confirmRestart = false }
            }
        } else Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.011f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Gilt)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
