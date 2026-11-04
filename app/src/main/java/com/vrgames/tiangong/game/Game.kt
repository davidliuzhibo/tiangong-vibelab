package com.vrgames.tiangong.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.tiangong.core.Craft
import com.vrgames.tiangong.core.Fold
import com.vrgames.tiangong.core.Made
import com.vrgames.tiangong.core.Order
import com.vrgames.tiangong.core.Orders
import com.vrgames.tiangong.core.Progress
import com.vrgames.tiangong.core.Step
import com.vrgames.tiangong.core.Vec3
import com.vrgames.tiangong.core.Work
import com.vrgames.tiangong.render.Music
import com.vrgames.tiangong.render.Sfx
import com.vrgames.tiangong.render.Sound
import com.vrgames.tiangong.render.WorkshopView
import kotlin.math.abs

/**
 * State the stage, its panels and both windows share (one process). [work] is the order being
 * made; [progress] the best stars per order and [made] the best piece of each craft (both saved).
 * The panels and the pinches on the workshop call the moves here; [tick] runs the timed steps and
 * keeps the workshop's fires, steam and beat in step with them.
 */
object Game {
    private const val PREFS = "tiangong"
    private const val TAG = "TiangongGame"

    enum class Phase { CARD, ORDER, MAKING, DONE, RESULT }

    private val main = Handler(Looper.getMainLooper())
    private var prefs: android.content.SharedPreferences? = null
    var progress = Progress(); private set
    var made = Made(); private set
    var order by mutableStateOf(Orders.ALL[0]); private set
    var work: Work? = null; private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var trophiesBefore = 0
    var music: Music? = null
    var sound: Sound? = null
    var view: WorkshopView? = null

    /** The option picked at each choice step and where each gauge was stopped, by step title. */
    val picks = HashMap<String, Int>()
    val stops = HashMap<String, Double>()
    /** What the last scored step came to, said under the step panel. */
    var lastVerdict by mutableStateOf<String?>(null)
    private var lastBounce = -1
    private var dyeing = false

    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    var closeWindow: (String) -> Unit = {}

    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            progress = Progress.load(p.getString("progress", null))
            made = Made.load(p.getString("made", null))
        }
        AutoPilot.register(context)
    }

    val trophies get() = progress.let { if (it.gold) 3 else if (it.silver) 2 else if (it.bronze) 1 else 0 }

    /** Plays [s] at the front station. */
    private fun sfx(s: Sfx) = sound?.play(s, view?.work ?: Vec3(0.0, 0.1, 0.0))

    fun say(text: String?) {
        note = text
        noteVersion++
    }

    fun changed() {
        version++
    }

    /** Puts the trophies and the cabinet as earned (at start and after each order). */
    fun showTrophies() {
        view?.trophies(trophies, made, progress.done(Orders.VASE.id))
    }

    fun start() = open(progress.next() ?: Orders.ALL.last())

    /** The order card of [o]; the bench turns its station to the front. */
    fun open(o: Order) {
        if (!progress.open(o)) {
            say(if (o.bonus) "拿到金奖杯，神秘客人才会来。" else if (o.id == "gift") "十二张订单都交了货，才能准备年礼。" else "先做完前面的订单。")
            return
        }
        order = o
        work = Work(o)
        picks.clear()
        stops.clear()
        lastVerdict = null
        dyeing = false
        view?.let { v ->
            v.face(if (o.craft == Craft.GIFT) Craft.PAPER else o.craft)
            v.reset(o.craft)
            v.meterOff()
            v.beatOff()
            v.spin(false)
            (o.steps.firstOrNull { it is Step.Tie } as? Step.Tie)?.let { v.target(it.target) }
            if (o.craft == Craft.GIFT) v.gift(null, false, made)
        }
        phase = Phase.ORDER
        version++
    }

    fun begin() {
        if (phase != Phase.ORDER) return
        phase = Phase.MAKING
        enterStep()
        version++
    }

    private fun enterStep() {
        val w = work ?: return
        val v = view
        val s = w.step ?: return
        v?.meterOff()
        v?.beatOff()
        v?.spin(s is Step.Shape)
        lastBounce = 0
        when (s) {
            is Step.Gauge -> {
                val (lo, hi) = GAUGE_COLORS[s.title] ?: (0xFF555555.toInt() to 0xFFEEEEEE.toInt())
                v?.meter(s.target, s.tolerance, lo, hi)
                if (s.title == "烧窑") { v?.inKiln(true); sfx(Sfx.FIRE) }
                if (s.title == "烧烟") sfx(Sfx.FIRE)
            }
            is Step.Shape -> showPot()
            is Step.Tie -> {
                w.fold = null
                w.bands.clear()
                w.dips = 1
                v?.bundleShape(null, emptySet(), 0)
            }
            else -> Unit
        }
        say(s.say)
    }

    // ---- The player's moves ----

    fun choose(i: Int) {
        val w = work ?: return
        val s = w.step as? Step.Choice ?: return
        if (phase != Phase.MAKING) return
        picks[s.title] = i
        val ok = w.choose(i)
        sfx(if (ok) Sfx.GOOD else Sfx.BAD)
        lastVerdict = if (ok) "${s.title}：选对了。" else "${s.title}：选了「${s.options[i]}」，订单要的是「${s.options[s.answer]}」。"
        when (s.title) {
            "画青花", "上釉" -> showPot()
            "选春联" -> view?.gift(couplet(), false, made)
        }
        afterStep()
    }

    /** Stops the gauge where the needle is. */
    fun stop() {
        val w = work ?: return
        val s = w.step as? Step.Gauge ?: return
        if (phase != Phase.MAKING) return
        stopped(s, w.gauge, w.stopGauge())
        afterStep()
    }

    private fun stopped(s: Step.Gauge, at: Double, score: Double) {
        stops[s.title] = at
        val d = at - s.target
        lastVerdict = s.title + "：" + when {
            kotlin.math.abs(d) <= s.tolerance -> "正好！"
            d < 0 -> "${s.low.ifEmpty { "早" }}了一点（${(score * 100).toInt()} 分）。"
            else -> "${s.high.ifEmpty { "晚" }}了一点（${(score * 100).toInt()} 分）。"
        }
        sfx(if (score >= 0.99) Sfx.GOOD else Sfx.MISS)
        val v = view
        when (s.title) {
            "抄纸" -> { v?.wetSheet(at); sfx(Sfx.DIP) }
            "烧窑" -> { v?.inKiln(false); v?.kiln(0.0); showPot() }
            "蒸煮" -> v?.pulpColor(at)
        }
        v?.meterOff()
    }

    /** One strike of the pestle or the hammer. */
    fun strike() {
        val w = work ?: return
        val s = w.step as? Step.Rhythm ?: return
        if (phase != Phase.MAKING) return
        val ok = w.strike()
        view?.strike(ok)
        sfx(if (ok) Sfx.POUND else Sfx.MISS)
        if (w.step !== s) {
            lastVerdict = "${s.title}：${w.scores.last().let { if (it >= 0.99) "每一下都在点上！" else "有几下没打在点上（${(it * 100).toInt()} 分）。" }}"
            afterStep()
        }
        version++
    }

    fun ring(i: Int, by: Int) {
        val w = work ?: return
        if (w.step !is Step.Shape || phase != Phase.MAKING) return
        w.ring(i, by)
        sfx(Sfx.TOOL)
        showPot()
        version++
    }

    fun shaped() {
        val w = work ?: return
        val s = w.step as? Step.Shape ?: return
        if (phase != Phase.MAKING) return
        val sc = w.shaped()
        lastVerdict = "${s.title}：" + if (sc >= 0.99) "和图样一模一样！" else "和图样差了一些（${(sc * 100).toInt()} 分）。"
        sfx(if (sc >= 0.99) Sfx.GOOD else Sfx.MISS)
        view?.spin(false)
        afterStep()
    }

    fun fold(f: Fold) {
        val w = work ?: return
        if (w.step !is Step.Tie || dyeing) return
        w.fold = f
        sfx(Sfx.CLOTH)
        view?.bundleShape(f, w.bands, 0)
        version++
    }

    fun band(b: Int) {
        val w = work ?: return
        if (w.step !is Step.Tie || dyeing) return
        if (!w.bands.remove(b)) w.bands += b
        sfx(Sfx.TOOL)
        view?.bundleShape(w.fold, w.bands, 0)
        version++
    }

    fun dips(by: Int) {
        val w = work ?: return
        if (w.step !is Step.Tie || dyeing) return
        w.dips = (w.dips + by).coerceIn(1, 5)
        sfx(Sfx.TOOL)
        version++
    }

    /** Into the vat: the bundle dips, then the cloth is opened on the rack and scored. */
    fun dye() {
        val w = work ?: return
        val s = w.step as? Step.Tie ?: return
        if (phase != Phase.MAKING || dyeing) return
        val d = w.dye() ?: run { say("先选一种折法。"); return }
        dyeing = true
        view?.bundleShape(d.fold, d.bands, d.dips)
        view?.dyeing()
        sfx(Sfx.DIP)
        main.postDelayed({
            if (work !== w) return@postDelayed
            dyeing = false
            val sc = w.dyed()
            view?.bundleShape(null, emptySet(), 0)
            view?.cloth(d)
            sfx(if (sc >= 0.99) Sfx.GOOD else Sfx.CLOTH)
            lastVerdict = "${s.title}：" + if (sc >= 0.99) "花纹和订单一模一样！" else "和订单的花纹${if (sc > 0.5) "有点" else "很"}不一样（${(sc * 100).toInt()} 分）。"
            afterStep()
        }, 1700)
        version++
    }

    /** A plain step (cut, press, dry, write, wrap, give). */
    fun tap() {
        val w = work ?: return
        val s = w.step as? Step.Tap ?: return
        if (phase != Phase.MAKING) return
        val v = view
        when (s.action) {
            "chop" -> { v?.chop(); sfx(Sfx.CHOP) }
            "press" -> { v?.paper(w.quality, stops["抄纸"] ?: 0.5, picks["选竹"] != 1); sfx(Sfx.DROP) }
            "trim" -> { v?.trim(); sfx(Sfx.TOOL) }
            "dry" -> { if (order.craft == Craft.INK) v?.ink(picks["和胶"] == 1, picks["入模"] == 1, w.quality); sfx(Sfx.CLOTH) }
            "write" -> { v?.gift(couplet(), false, made); sfx(Sfx.CLOTH) }
            "wrap" -> { v?.gift(couplet(), true, made); sfx(Sfx.CLOTH) }
            "give" -> { v?.bow(); sfx(Sfx.GIFT) }
        }
        w.tap()
        afterStep()
    }

    /** A pinch on the front station or a tool: does the step's action when it has one. */
    fun pinchWork(id: String) {
        if (phase != Phase.MAKING) return
        when (work?.step) {
            is Step.Gauge -> stop()
            is Step.Rhythm -> strike()
            is Step.Tap -> tap()
            is Step.Tie -> if (id == "tool:dyevat") dye()
            else -> Unit
        }
    }

    fun hint() {
        val w = work ?: return
        if (phase != Phase.MAKING) return
        say("提示：" + w.hint())
        version++
    }

    private fun couplet(): Pair<String, String>? {
        val s = Orders.GIFT.steps.first() as Step.Choice
        val i = picks[s.title] ?: return null
        val parts = s.options[i].split(" · ")
        return parts[0] to parts.getOrElse(1) { "" }
    }

    private fun showPot() {
        val w = work ?: return
        val shape = order.steps.firstOrNull { it is Step.Shape } as? Step.Shape ?: return
        view?.pot(w.rings, shape.height, picks["画青花"], picks["上釉"]?.let { it == 1 }, picks["选土"] == 1, stops["烧窑"])
    }

    private fun afterStep() {
        val w = work ?: return
        if (w.done) finish() else enterStep()
        version++
    }

    private fun finish() {
        val w = work ?: return
        val o = order
        trophiesBefore = trophies
        val q = w.quality
        when (o.craft) {
            Craft.PAPER -> made.keep(Made.Paper(q, stops["抄纸"] ?: 0.5, picks["选竹"] != 1))
            Craft.PORCELAIN -> {
                val shape = o.steps.first { it is Step.Shape } as Step.Shape
                made.keep(Made.Pot(w.rings.copyOf(), shape.height, picks["画青花"] ?: 0, picks["上釉"] == 1, picks["选土"] == 1, stops["烧窑"] ?: 0.78, q), isVase = o.bonus)
            }
            Craft.DYE -> w.dye()?.let { made.keep(Made.Cloth(it, q)) }
            Craft.INK -> made.keep(Made.Ink(picks["和胶"] == 1, picks["入模"] == 1, q))
            Craft.GIFT -> progress.giftGiven = true
        }
        if (o.craft != Craft.GIFT) progress.record(w)
        save()
        phase = Phase.DONE
        view?.meterOff()
        view?.beatOff()
        view?.present(o.craft)
        view?.sparkle()
        if (o.craft != Craft.GIFT) view?.bow()
        sfx(Sfx.DONE)
        say(verdict(o, w))
        Log.i(TAG, "made ${o.id} stars=${w.stars()} quality=${"%.3f".format(q)} scores=${w.scores.joinToString { "%.2f".format(it) }} hints=${w.hints}")
        main.postDelayed({
            if (phase == Phase.DONE && order === o) {
                phase = Phase.RESULT
                view?.present(null)
                showTrophies()
                version++
            }
        }, 4500)
    }

    /** The customer's words on the piece. */
    private fun verdict(o: Order, w: Work): String {
        if (o.craft == Craft.GIFT) {
            val avg = Orders.CRAFTS.map { progress.bestOf(it) }.average()
            return when {
                avg >= 0.9 -> "老学者捧着年礼连声称赞：「纸白如雪，墨黑如漆，瓷润如玉，布蓝如天——巧夺天工！」"
                avg >= 0.75 -> "老学者笑着收下：「好！一看就是用心做的，明年还来你这里订货。」"
                else -> "老学者收下了年礼：「心意到了。手艺嘛，还可以再练练。」"
            }
        }
        val s = w.stars()
        return "${o.who}：" + when (s) {
            3 -> "「正是我要的，好手艺！」"
            2 -> "「不错，可以交货。」"
            1 -> "「勉强能用吧……」"
            else -> "「这可不行，得重做。」"
        }
    }

    fun tick(dt: Double) {
        val w = work ?: return
        if (phase != Phase.MAKING) return
        val s = w.step
        val v = view
        when (s) {
            is Step.Gauge -> {
                val at = w.gauge
                w.tick(dt)
                if (w.step !== s) {
                    // The needle ran to the end.
                    stopped(s, 1.0, w.scores.last())
                    lastVerdict = "${s.title}：太晚了，指针走到了头。"
                    afterStep()
                    return
                }
                v?.gauge(w.gauge)
                when (s.title) {
                    "蒸煮" -> v?.boil(at)
                    "烧窑" -> v?.kiln(at)
                    "烧烟" -> v?.soot(at)
                }
            }
            is Step.Rhythm -> {
                w.tick(dt)
                v?.beat(w.beat)
                val bounce = ((w.rhythmT + s.period / 2) / s.period).toInt()
                if (bounce != lastBounce) {
                    lastBounce = bounce
                    sfx(Sfx.BEAT)
                }
            }
            else -> Unit
        }
    }

    fun next() = progress.next()?.let { open(it) } ?: run { say("所有订单都做完了！在「订单簿」里可以重做任何一张，把星星拿满。"); version++ }

    fun again() = open(order)

    private fun save() {
        prefs?.edit()?.putString("progress", progress.save())?.putString("made", made.save())?.apply()
    }

    fun restart() {
        progress = Progress()
        made = Made()
        save()
        showTrophies()
        phase = Phase.CARD
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    /** Debug: every order made at [stars] (the gift given too). */
    fun grant(stars: Int) {
        for (o in Orders.ALL) {
            progress.best[o.id] = stars
            progress.quality[o.id] = if (stars == 3) 0.95 else 0.7
        }
        progress.giftGiven = true
        save()
        showTrophies()
        version++
    }

    fun toCard() {
        phase = Phase.CARD
        version++
    }

    /** The gauge's two ends per step: what too little and too much look like. */
    private val GAUGE_COLORS = mapOf(
        "蒸煮" to (0xFF7F8C55.toInt() to 0xFFC98E3A.toInt()),
        "抄纸" to (0xFFF5F2E8.toInt() to 0xFFA89D80.toInt()),
        "烧窑" to (0xFF5A1A0A.toInt() to 0xFFFFF2B0.toInt()),
        "烧烟" to (0xFF402010.toInt() to 0xFFFF9A30.toInt()),
    )

    // Debug hooks (AutoPilot).
    var debugOpen by mutableStateOf<String?>(null)
    var debugView by mutableStateOf<String?>(null)
    var faceRequest by mutableIntStateOf(0)
}
