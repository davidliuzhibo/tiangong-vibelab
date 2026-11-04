package com.vrgames.tiangong.core

/**
 * The customers' orders, three per craft from easy to hard, the New Year gift that ends the game,
 * and the gold trophy's imperial vase. The process follows 天工开物 (the book's chapters are named
 * in [Craft.book]); the details are simplified into small games.
 */
object Orders {
    private fun paper(id: String, title: String, who: String, want: String, young: Boolean, longSoak: Boolean, boil: Double, boilTol: Double, pounds: Int, thick: Double, thickTol: Double) =
        Order(id, Craft.PAPER, title, who, want, listOf(
            Step.Tap("斩竹", "捏一下竹子，把竹子砍成段。", "chop"),
            Step.Choice("选竹", "嫩竹纤维细、老竹纤维粗。订单要什么样的纸？", listOf("嫩竹", "老竹"), if (young) 0 else 1),
            Step.Choice("浸泡", "竹子要在水塘里泡烂，才能捣成浆。泡多久？", listOf("泡十天", "泡一百天"), if (longSoak) 1 else 0),
            Step.Gauge("蒸煮", "加石灰蒸煮，火候到了就捏一下停火：火小煮不烂，火大纤维发黄。", boil, boilTol, 6.0, "火小", "火大"),
            Step.Rhythm("舂捣", "竹料放进石臼，标记跳到最高处时捏一下，捶 $pounds 下，越细的纸要捶得越多。", pounds),
            Step.Gauge("抄纸", "竹帘在纸浆里荡一下再提起：停得越晚，纸越厚。", thick, thickTol, 5.0, "薄", "厚"),
            Step.Tap("压干焙干", "把湿纸一张张压去水分，贴在火墙上烘干。", "press"),
        ))

    private fun pot(id: String, title: String, who: String, want: String, rings: IntArray, height: Double, pattern: Int, glaze: Int, kilnTol: Double) =
        Order(id, Craft.PORCELAIN, title, who, want, listOf(
            Step.Choice("选土", "白瓷要用白色的瓷土。", listOf("高岭土", "红黏土"), 0),
            Step.Shape("拉坯", "转盘转起来了：调整四道轮廓，从下到上，让瓷坯和订单上的图样一样。", rings, height),
            Step.Tap("修坯", "捏一下，把坯修光。", "trim"),
            Step.Choice("画青花", "订单要什么花纹？", listOf("缠枝莲", "山水", "云龙"), pattern),
            Step.Choice("上釉", "订单要什么釉？", listOf("透明釉（白底青花）", "青釉"), glaze),
            Step.Gauge("烧窑", "窑温慢慢升高，到一千三百度左右时捏一下封窑：低了烧不熟，高了会裂。", 0.78, kilnTol, 7.0, "低温", "高温"),
        ))

    private fun cloth(id: String, title: String, who: String, want: String, target: Dye) =
        Order(id, Craft.DYE, title, who, want, listOf(
            Step.Tie("扎染", "看订单上的花纹，想好怎么折、在哪几道扎绳、染几次，再捏「染」。扎了绳的地方染不上色；第 1 道绳最靠近折痕（布的中间），第 5 道在最外边。", target),
            Step.Tap("拆开晾干", "拆开绳子，把布晾在竹竿上。", "dry"),
        ))

    private fun ink(id: String, title: String, who: String, want: String, pine: Boolean, flame: Double, flameTol: Double, moreGlue: Boolean, beats: Int, dragon: Boolean) =
        Order(id, Craft.INK, title, who, want, listOf(
            Step.Choice("选料", "松烟墨烧松木，油烟墨烧桐油。", listOf("松木", "桐油"), if (pine) 0 else 1),
            Step.Gauge("烧烟", "火苗越小，烟越细、墨越好：在合适的时候捏一下，把灯芯定住。", flame, flameTol, 5.0, "火小", "火大"),
            Step.Choice("和胶", "烟灰要和胶揉成墨团。订单的墨要怎样？", listOf("胶少（写字涩、不发亮）", "胶多（流畅、发亮）"), if (moreGlue) 1 else 0),
            Step.Rhythm("捶打", "墨团要千杵万捶：标记跳到最高时捏一下，捶 $beats 下。", beats),
            Step.Choice("入模", "订单要什么样的墨锭？", listOf("素面", "龙纹"), if (dragon) 1 else 0),
            Step.Tap("晾干", "墨锭放进灰里慢慢阴干。", "dry"),
        ))

    val ALL: List<Order> = listOf(
        paper("p1", "书写纸", "村塾的先生", "要一刀薄而匀的书写纸，给学童练字。", young = true, longSoak = true, boil = 0.6, boilTol = 0.1, pounds = 8, thick = 0.3, thickTol = 0.1),
        paper("p2", "包装纸", "茶庄的掌柜", "要厚实耐用的包装纸，包茶叶。用老竹做的就行，厚一点。", young = false, longSoak = true, boil = 0.55, boilTol = 0.1, pounds = 6, thick = 0.75, thickTol = 0.1),
        paper("p3", "书画纸", "城里的画师", "要极薄、极细的书画纸：嫩竹，捣得越细越好。", young = true, longSoak = true, boil = 0.62, boilTol = 0.06, pounds = 14, thick = 0.22, thickTol = 0.06),
        pot("c1", "饭碗", "隔壁的大婶", "要一只白底青花的饭碗：口大底小，矮矮的，画缠枝莲。", intArrayOf(2, 4, 5, 6), 0.07, 0, 0, 0.1),
        pot("c2", "山水瓶", "茶庄的掌柜", "要一只高身窄口的瓶子插花：画山水，上透明釉。", intArrayOf(3, 4, 3, 2), 0.15, 1, 0, 0.08),
        pot("c3", "梅瓶", "城里的画师", "要一只梅瓶：小口、丰肩、瘦底，青釉，画云龙。要求很高。", intArrayOf(2, 3, 6, 1), 0.17, 2, 1, 0.05),
        cloth("d1", "方巾", "村塾的先生", "要一块四角对称的蓝印方巾。", Dye(Fold.QUARTER, setOf(2), 3)),
        cloth("d2", "条纹布", "隔壁的大婶", "要一块一道道竖条纹的门帘布，颜色深一点。", Dye(Fold.FAN, setOf(1, 3), 5)),
        cloth("d3", "团花巾", "城里的画师", "要一块中间一圈圈团花的头巾，淡蓝色。", Dye(Fold.TRIANGLE, setOf(1, 3), 2)),
        ink("i1", "习字墨", "村塾的先生", "要几锭写字用的松烟墨，素面就行。", pine = true, flame = 0.35, flameTol = 0.1, moreGlue = false, beats = 8, dragon = false),
        ink("i2", "画墨", "城里的画师", "要乌黑发亮的油烟墨画画，胶要足。", pine = false, flame = 0.3, flameTol = 0.08, moreGlue = true, beats = 10, dragon = false),
        ink("i3", "贡墨", "县衙的师爷", "要一锭进贡用的龙纹墨：烟要最细，捶得最多。", pine = false, flame = 0.2, flameTol = 0.05, moreGlue = true, beats = 14, dragon = true),
    )

    /** The New Year gift: write the couplet on your paper with your ink, wrap your bowl in your cloth. */
    val GIFT = Order("gift", Craft.GIFT, "年礼", "老学者", "过年了，用自己造的纸、自己制的墨写一副春联，再用自己染的布包好自己烧的瓷碗，送给常来的老学者。", listOf(
        Step.Choice("选春联", "挑一副春联：上下联要字数相同、意思相对。", listOf("天增岁月人增寿 · 春满乾坤福满门", "爆竹一声除旧 · 桃符万户更新岁月", "瑞雪兆丰年 · 春风送暖"), 0),
        Step.Tap("写春联", "捏一下，用自己制的墨写在自己造的纸上。", "write"),
        Step.Tap("包碗", "捏一下，用自己染的布包好自己烧的碗。", "wrap"),
        Step.Tap("送礼", "捏一下，把年礼送给老学者。", "give"),
    ))

    /** The gold trophy's order: the imperial gilded vase, with the strictest tolerances. */
    val VASE = pot("gold", "御窑金彩瓶", "神秘客人", "宫里来的客人：要一只御窑金彩瓶，器形一丝不差，火候分毫不能错。", intArrayOf(2, 5, 4, 2), 0.18, 2, 0, 0.035).let {
        Order(it.id, it.craft, it.title, it.who, it.want, it.steps, bonus = true)
    }

    val CRAFTS = listOf(Craft.PAPER, Craft.PORCELAIN, Craft.DYE, Craft.INK)
    fun of(c: Craft) = ALL.filter { it.craft == c }
    fun byId(id: String) = (ALL + GIFT + VASE).first { it.id == id }

    fun stars(quality: Double, hints: Int) = when {
        quality >= 0.92 && hints == 0 -> 3
        quality >= 0.8 -> 2
        quality >= 0.6 -> 1
        else -> 0
    }
}

/** Best stars and quality per order, the gift given, and the trophies. */
class Progress {
    val best = LinkedHashMap<String, Int>()
    val quality = LinkedHashMap<String, Double>()
    var giftGiven = false

    fun record(w: Work) {
        val id = w.order.id
        val s = w.stars()
        if (s > (best[id] ?: 0)) best[id] = s
        if (w.quality > (quality[id] ?: 0.0)) quality[id] = w.quality
    }

    fun done(id: String) = (best[id] ?: 0) > 0
    val stars get() = Orders.ALL.sumOf { best[it.id] ?: 0 }
    val maxStars get() = Orders.ALL.size * 3
    val bronze get() = Orders.ALL.all { done(it.id) } && giftGiven
    val silver get() = bronze && stars * 2 >= maxStars
    val gold get() = bronze && stars == maxStars

    /** Orders open craft by craft in order; the gift once all twelve are made. */
    fun open(o: Order) = when {
        o.id == "gift" -> Orders.ALL.all { done(it.id) }
        o.bonus -> gold
        else -> Orders.ALL.indexOf(o) <= Orders.ALL.indexOfFirst { !done(it.id) }.let { if (it < 0) Orders.ALL.size else it }
    }

    fun next(): Order? = Orders.ALL.firstOrNull { !done(it.id) } ?: if (!giftGiven) Orders.GIFT else null

    /** The best quality of each craft (for the gift's verdict and the cabinet). */
    fun bestOf(c: Craft) = Orders.of(c).maxOfOrNull { quality[it.id] ?: 0.0 } ?: 0.0

    fun save() = best.entries.joinToString(",") { "${it.key}:${it.value}:${"%.3f".format(quality[it.key] ?: 0.0)}" } + ";$giftGiven"

    companion object {
        fun load(s: String?) = Progress().apply {
            val (a, g) = (s ?: ";false").split(";").let { it[0] to it.getOrElse(1) { "false" } }
            a.split(",").forEach { e ->
                val f = e.split(":")
                if (f.size == 3) { f[1].toIntOrNull()?.let { best[f[0]] = it }; f[2].toDoubleOrNull()?.let { quality[f[0]] = it } }
            }
            giftGiven = g == "true"
        }
    }
}
