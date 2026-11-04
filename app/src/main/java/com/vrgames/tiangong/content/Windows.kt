package com.vrgames.tiangong.content

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.tiangong.BOOK_ID
import com.vrgames.tiangong.ORDERS_ID
import com.vrgames.tiangong.core.Order
import com.vrgames.tiangong.core.Orders
import com.vrgames.tiangong.game.Game

private val CardBg = Color(0x26F2EEE6)

/** 订单簿: every order by craft with its stars (pick one to make or remake), the gift, the trophies. */
@Composable
fun OrdersWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val pr = Game.progress
    val context = LocalContext.current
    val treasure: ImageBitmap? = remember { runCatching { context.assets.open("textures/treasure.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(ORDERS_ID, "订单簿", 32.sp)
        Text("总星数 ${pr.stars} / ${pr.maxStars}。点一张订单就能做（或重做，把星星拿满）。", fontSize = 18.sp, color = Dim)
        for (c in Orders.CRAFTS) {
            Text("${c.title}（《天工开物·${c.book}》）", fontSize = 24.sp, color = Indigo)
            for (o in Orders.of(c)) OrderRow(o)
        }
        Text("年礼", fontSize = 24.sp, color = Indigo)
        OrderRow(Orders.GIFT)
        if (pr.gold) {
            Text("神秘客人", fontSize = 24.sp, color = Indigo)
            OrderRow(Orders.VASE)
        }
        Text("奖杯", fontSize = 24.sp, color = Indigo)
        Trophy("铜奖杯 ·「巧手」木牌", "十二张订单都交货，并送出年礼。", pr.bronze, "奖杯台上摆着一块「巧手」木牌。")
        Trophy("银奖杯 · 我的作品展柜", "拿到一半以上的星（${pr.maxStars / 2} 颗）。", pr.silver, "作坊右后方的展柜里，摆着你做得最好的纸、瓷、布、墨。")
        Trophy("金奖杯 ·「天工」金匾", "拿满全部 ${pr.maxStars} 颗星。", pr.gold, "门楣挂上「天工」金匾；神秘客人来订「御窑金彩瓶」；还有一幅珍藏长卷。")
        if (pr.gold && treasure != null) Image(treasure, "珍藏长卷", Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun OrderRow(o: Order) {
    val pr = Game.progress
    val open = pr.open(o)
    val s = pr.best[o.id] ?: 0
    val done = if (o.id == "gift") pr.giftGiven else s > 0
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("「${o.title}」　${o.who}", fontSize = 20.sp, color = if (open) Gilt else Dim)
            Text(o.want, fontSize = 15.sp, color = Ink)
            Text(
                when {
                    o.id == "gift" -> if (done) "已送出" else if (open) "可以准备了" else "十二张订单都交货后才能准备"
                    s > 0 -> "已交货　" + "★".repeat(s) + "☆".repeat(3 - s) + "　品质 ${((pr.quality[o.id] ?: 0.0) * 100).toInt()}%"
                    open -> "待做"
                    else -> "还没接到"
                },
                fontSize = 15.sp, color = Indigo,
            )
        }
        if (open) Button(onClick = { Game.open(o); Game.closeWindow(ORDERS_ID) }) { Text(if (done) "重做" else "去做", fontSize = 20.sp) }
    }
}

@Composable
private fun Trophy(name: String, rule: String, got: Boolean, reward: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (got) "$name　已得到" else "$name　？", fontSize = 20.sp, color = if (got) Gilt else Dim)
        Text(rule, fontSize = 16.sp, color = Ink)
        Text(if (got) reward else "得到后揭晓。", fontSize = 16.sp, color = if (got) Indigo else Dim)
    }
}

/** 天工开物图谱: the book, each craft's chapter (a short line of the original and plain words on it). */
@Composable
fun BookWindow() {
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WindowHeader(BOOK_ID, "天工开物图谱", 32.sp)
        for ((title, quote, text) in BOOK) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 24.sp, color = Gilt)
                if (quote.isNotEmpty()) Text(quote, fontSize = 19.sp, color = Indigo)
                Text(text, fontSize = 18.sp, color = Ink)
            }
        }
        Text("游戏里的工序做了简化，火候、次数等数字是为了好玩定的，不是书里的原数。", fontSize = 15.sp, color = Dim)
    }
}

private val BOOK = listOf(
    Triple("《天工开物》", "", "明代宋应星著，崇祯十年（1637 年）刊行，分十八卷，记下了农业和手工业的一百多种技术，配有一百多幅木刻插图。书名取「天工」（自然的造化）与「开物」（人开发万物）之意。"),
    Triple("杀青 · 造竹纸", "「凡造竹纸，事出南方，而闽省独专其盛。」", "竹子砍下后泡在塘里一百多天，再加石灰蒸煮，放进石臼舂成泥一样的竹浆。抄纸时两手持竹帘入槽一荡，纸浆就留在帘上：「厚薄由人手法，轻荡则薄，重荡则厚。」湿纸压去水分，贴在火墙上烘干。"),
    Triple("陶埏 · 制瓷", "「共计一坯工力，过手七十二，方克成器。」", "一只瓷碗要经过七十多道手：淘洗瓷土、在辘轳车上拉坯、修坯、画青花、上釉、入窑烧成。窑火要烧到一千三百度左右，火候差一点，整窑瓷器就可能生烧或开裂。景德镇是明代最大的瓷都。"),
    Triple("彰施 · 染色", "「凡蓝五种，皆可为淀。」", "蓝草泡水、加石灰，沉淀出的「靛」就是染蓝布的染料。布每进一次染缸、出来见空气，颜色就深一层，所以浸几次就决定了深浅。把布折好、用绳扎紧再染，扎住的地方染不上色，打开就是花纹——这种扎染古时叫「绞缬」，唐代已经很流行。"),
    Triple("丹青 · 制墨", "「凡墨，烧烟凝质而为之。」", "书里说，用桐油、清油等烧烟做墨的占十分之一，烧松木取烟的占十分之九。烟灰收集起来，和上胶，反复捶打成墨团，压进刻着花纹的墨模，再慢慢阴干。好墨要烟细、胶匀、捶得够，写出来黑而有光。"),
    Triple("年礼 · 春联", "", "春联古时写在桃木板上，叫「桃符」。上联、下联字数相同，意思相对，平仄相协，过年时贴在门的两边。"),
)
