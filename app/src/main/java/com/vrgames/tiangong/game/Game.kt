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
import com.pico.spatial.core.math.Vector3
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

    // ---- v0.2: narration from 天工开物, one clip per craft and one for the gift ----
    private var app: Context? = null
    private var player: android.media.MediaPlayer? = null
    private val heard = HashSet<Craft>()
    var speaking by mutableStateOf(false); private set

    private fun clipOf(c: Craft) = when (c) {
        Craft.PAPER -> "paper"
        Craft.PORCELAIN -> "porcelain"
        Craft.DYE -> "dye"
        Craft.INK -> "ink"
        Craft.GIFT -> "gift"
    }

    fun speak(c: Craft) {
        stopVoice()
        val a = app ?: return
        runCatching {
            val fd = a.assets.openFd("narration/${clipOf(c)}.ogg")
            player = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopVoice() }
                prepare()
                start()
            }
            speaking = true
        }.onFailure { Log.w(TAG, "narration $c: ${it.message}") }
    }

    fun toggleVoice() {
        if (speaking) stopVoice() else speak(order.craft)
    }

    fun stopVoice() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        speaking = false
    }

    fun init(context: Context) {
        app = context.applicationContext
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
        // The first order of each craft in a session reads the book's words on it.
        if (heard.add(o.craft)) speak(o.craft) else stopVoice()
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
        v?.spin(s is Step.Shape || s is Step.Tap && (s.action == "trim" || s.action == "paint"))
        held = null
        armed = true
        inVat = false
        val (on, glint) = gripsFor(s)
        v?.tools(on, glint)
        v?.dropAt(null)
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
                v?.bundleShape(null, emptySet(), 0)
            }
            is Step.Tap -> {
                if (s.action == "paint") showPot(reveal = 0f)
                if (s.gesture == Step.Gesture.CARRY) {
                    val id = on.first()
                    v?.carryLook(id, made, picks["和胶"] == 1, picks["入模"] == 1, w.quality, w.dye())
                    if (id == "gift") v?.gift(couplet(), true, made, bowl = false)
                    v?.dropAt(id)
                }
            }
            else -> Unit
        }
        say(s.say)
    }

    // ---- v0.2: the hand on the tools ----

    /** The tool held (a grip id), whether the next stroke may count (the tool came back up), the bundle in the dye. */
    private var held: String? = null
    private var armed = true
    private var inVat = false
    private var shapeAcc = 0.0

    /** The tools a step uses: those to bring out and those to make glint. */
    private fun gripsFor(s: Step?): Pair<Set<String>, Set<String>> = when (s) {
        is Step.Gauge -> setOf(GAUGE_GRIP[s.tool] ?: "fire").let { it to it }
        is Step.Rhythm -> setOf(if (s.tool == "墨锤") "hammer" else "pestle").let { it to it }
        is Step.Shape -> (0 until 4).map { "ring$it" }.toSet().let { it to it }
        is Step.Tie -> setOf("rope", "bundle") to setOf(if (work?.fold == null) "" else if ((work?.bands?.size ?: 0) == 0) "rope" else "bundle")
        is Step.Tap -> setOf(
            when (s.action) {
                "chop" -> "knife"
                "press" -> "press"
                "trim" -> "trimmer"
                "paint" -> "brush"
                "write" -> "pen"
                "wrap" -> "wrapcloth"
                "give" -> "gift"
                else -> if (order.craft == Craft.DYE) "cloth" else "stick"
            },
        ).let { it to it }
        else -> emptySet<String>() to emptySet()
    }

    /** The hand closes on a tool: holds it if this step uses it, else says which one to take. */
    fun grab(id: String): Boolean {
        if (phase != Phase.MAKING) return false
        val g = id.removePrefix("grip:")
        val s = work?.step ?: return false
        val (on, _) = gripsFor(s)
        if (g !in on) {
            if (s is Step.Choice) say("这一步先在面板上选：${s.say}") else say("这一步用不到它。" + s.say)
            return false
        }
        if (s is Step.Tie && g == "bundle" && work?.fold == null) {
            say("先在面板上选一种折法，把布折好。")
            return false
        }
        held = g
        shapeAcc = 0.0
        sfx(Sfx.TOOL)
        return true
    }

    /** The held tool moves by [d] (shop meters). */
    fun dragBy(d: Vec3) {
        val id = held ?: return
        val w = work ?: return
        val v = view ?: return
        val o = v.gripOff(id)
        var x = o.x + d.x
        var y = o.y + d.y
        var z = o.z + d.z
        when (val s = w.step) {
            is Step.Gauge -> if (id == "mould") {
                x = x.coerceIn(-0.012, 0.012); z = z.coerceIn(-0.012, 0.012); y = y.coerceIn(-0.046, 0.04)
                val depth = (-y / 0.044).coerceIn(0.0, 1.0)
                if (depth > w.gauge) {
                    w.setGauge(depth)
                    v.gauge(depth)
                    v.wetSheet(depth)
                }
                if (depth > 0.1 && (y + 0.046) < 0.004) v.dipped()
                if (y > 0.025 && w.gauge > 0.08) {
                    v.moveGrip(id, Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
                    held = null
                    stop()
                    return
                }
            } else {
                x = 0.0; z = 0.0; y = y.coerceIn(0.0, 0.06)
                w.setGauge(y / 0.06)
                v.gauge(w.gauge)
            }
            is Step.Rhythm -> { x = 0.0; z = 0.0; y = y.coerceIn(-0.05, 0.03); stroke(y) }
            is Step.Tap -> when (s.gesture) {
                Step.Gesture.STROKES -> { x = 0.0; z = 0.0; y = y.coerceIn(-0.045, 0.03); stroke(y) }
                Step.Gesture.RUB -> {
                    x = x.coerceIn(-0.04, 0.04); y = y.coerceIn(-0.02, 0.02); z = z.coerceIn(-0.03, 0.03)
                    val moved = kotlin.math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z))
                    v.moveGrip(id, Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
                    rub(moved)
                    return
                }
                Step.Gesture.CARRY -> {
                    x = x.coerceIn(-0.35, 0.35); y = y.coerceIn(-0.02, 0.2); z = z.coerceIn(-0.3, 0.2)
                    v.moveGrip(id, Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
                    val at = v.gripAt(id)
                    val to = v.dropAt(id)
                    if (at != null && to != null && at.distanceTo(to) < CARRY_NEAR) carried()
                    return
                }
            }
            is Step.Shape -> {
                // Out widens the ring, in narrows it: one level per centimetre of hand motion.
                val i = id.removePrefix("ring").toIntOrNull() ?: return
                shapeAcc += d.x
                while (shapeAcc > SHAPE_STEP) { shapeAcc -= SHAPE_STEP; ring(i, 1) }
                while (shapeAcc < -SHAPE_STEP) { shapeAcc += SHAPE_STEP; ring(i, -1) }
                return
            }
            is Step.Tie -> {
                x = x.coerceIn(-0.12, 0.12); y = y.coerceIn(-0.01, 0.1); z = z.coerceIn(-0.1, 0.08)
                if (id == "bundle") {
                    v.moveGrip(id, Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
                    val at = v.gripAt("bundle") ?: return
                    val vat = v.dyeVat()
                    val over = kotlin.math.hypot(at.x - vat.x, at.z - vat.z) < 0.04
                    if (over && at.y < vat.y) inVat = true
                    if (inVat && (at.y > vat.y + 0.03 || !over)) {
                        inVat = false
                        dipOnce()
                    }
                    return
                }
            }
            else -> return
        }
        v.moveGrip(id, Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
    }

    /** The hand opens. */
    fun release() {
        val id = held ?: return
        held = null
        val w = work ?: return
        val v = view ?: return
        when (val s = w.step) {
            is Step.Gauge -> if (id == "mould") {
                if (w.gauge > 0.08) stop() else v.moveGrip(id, Vector3(0f, 0f, 0f))
            }
            is Step.Tie -> {
                if (id == "rope") {
                    // Let go on the bundle: tied at the nearest of the five places.
                    val at = v.gripAt("rope")
                    val b = v.gripAt("bundle")
                    if (at != null && b != null && kotlin.math.abs(at.z - b.z) < 0.035 && kotlin.math.abs(at.x - b.x) < 0.07) {
                        // Band places are measured from the bundle's middle (band 2 sits there).
                        val along = (at.x - b.x).toFloat()
                        val pick = (0 until 5).minBy { kotlin.math.abs((v.bandX(it) - v.bandX(2)) - along) }
                        if (pick !in w.bands) band(pick) else say("第 ${pick + 1} 道已经扎好了。捏一下扎好的绳可以拆掉。")
                    }
                } else if (inVat) {
                    inVat = false
                    dipOnce()
                }
                v.moveGrip(id, Vector3(0f, 0f, 0f))
            }
            is Step.Tap -> {
                if (s.gesture != Step.Gesture.CARRY || w.step === s) v.moveGrip(id, Vector3(0f, 0f, 0f))
                // The tool springs back up: the next push down is a new stroke (emulator 2026-11-10: the
                // second chop never counted without this).
                armed = true
            }
            is Step.Rhythm -> { v.moveGrip(id, Vector3(0f, 0f, 0f)); armed = true }
            else -> Unit
        }
    }

    /** The tool at height [y] (from rest): coming down past the bottom after being up is one stroke. */
    private fun stroke(y: Double) {
        if (y > -0.01) armed = true
        if (!armed || y > -0.038) return
        armed = false
        val w = work ?: return
        when (val s = w.step) {
            is Step.Rhythm -> strike()
            is Step.Tap -> {
                val v = view
                if (s.action == "chop") { v?.chop(); sfx(Sfx.CHOP) } else { v?.strike(true); sfx(Sfx.DROP) }
                if (w.advance(1.0 / s.strokes)) tapDone(s)
                version++
            }
            else -> Unit
        }
    }

    private fun rub(moved: Double) {
        val w = work ?: return
        val s = w.step as? Step.Tap ?: return
        val v = view
        held?.let { v?.rubMark(it) }
        if (s.action == "paint") showPot(reveal = w.progress.toFloat().coerceIn(0f, 1f))
        if ((w.progress * 10).toInt() != ((w.progress + moved / Step.RUB_M) * 10).toInt()) sfx(if (s.action == "trim") Sfx.TOOL else Sfx.CLOTH)
        if (w.advance(moved / Step.RUB_M)) {
            held = null
            tapDone(s)
        }
        version++
    }

    private fun carried() {
        val w = work ?: return
        val s = w.step as? Step.Tap ?: return
        held = null
        w.advance(1.0)
        tapDone(s)
    }

    private fun dipOnce() {
        val w = work ?: return
        if (w.step !is Step.Tie) return
        w.dip()
        view?.dipped()
        view?.bundleShape(w.fold, w.bands, w.dips)
        sfx(Sfx.DIP)
        say("浸了 ${w.dips} 次。" + if (w.dips >= 5) "最多浸五次。" else "")
        version++
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
        w.strike()
        view?.strike(true)
        sfx(Sfx.POUND)
        if (w.step !== s) {
            lastVerdict = "${s.title}：捶够了 ${s.count} 下。"
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
        view?.bundleShape(f, w.bands, w.dips)
        view?.tools(gripsFor(w.step).first, gripsFor(w.step).second)
        version++
    }

    fun band(b: Int) {
        val w = work ?: return
        if (w.step !is Step.Tie || dyeing) return
        if (w.dips > 0) { say("已经浸过染缸了，扎绳不能再改。"); return }
        if (!w.bands.remove(b)) w.bands += b
        sfx(Sfx.TOOL)
        view?.bundleShape(w.fold, w.bands, w.dips)
        view?.tools(gripsFor(w.step).first, gripsFor(w.step).second)
        version++
    }


    /** Into the vat: the bundle dips, then the cloth is opened on the rack and scored. */
    fun dye() {
        val w = work ?: return
        val s = w.step as? Step.Tie ?: return
        if (phase != Phase.MAKING || dyeing) return
        if (w.fold == null) { say("先在面板上选一种折法。"); return }
        val d = w.dye() ?: run { say("还没浸过染缸：捏住布束，浸进右边的染缸再提出来。"); return }
        val sc = w.dyed()
        view?.bundleShape(null, emptySet(), 0)
        sfx(if (sc >= 0.99) Sfx.GOOD else Sfx.CLOTH)
        lastVerdict = "${s.title}：" + if (sc >= 0.99) "花纹和订单一模一样！" else "和订单的花纹${if (sc > 0.5) "有点" else "很"}不一样（${(sc * 100).toInt()} 分）。"
        // The next step carries this cloth to the rack.
        view?.carryLook("cloth", made, false, false, w.quality, d)
        afterStep()
        version++
    }

    /** A hand-done step is finished: what it leaves on the bench. */
    private fun tapDone(s: Step.Tap) {
        val w = work ?: return
        val v = view
        v?.dropAt(null)
        when (s.action) {
            "chop" -> sfx(Sfx.GOOD)
            "press" -> { v?.paper(w.quality, stops["抄纸"] ?: 0.5, picks["选竹"] != 1); sfx(Sfx.DROP) }
            "trim" -> { v?.trim(); sfx(Sfx.GOOD) }
            "paint" -> { showPot(); sfx(Sfx.GOOD) }
            "dry" -> {
                if (order.craft == Craft.INK) v?.ink(picks["和胶"] == 1, picks["入模"] == 1, w.quality) else v?.cloth(w.dye())
                sfx(Sfx.CLOTH)
            }
            "write" -> { v?.gift(couplet(), false, made); sfx(Sfx.CLOTH) }
            "wrap" -> { v?.gift(couplet(), true, made); sfx(Sfx.CLOTH) }
            "give" -> { v?.bow(); sfx(Sfx.GIFT) }
        }
        v?.tools(emptySet(), emptySet())
        afterStep()
    }

    /** A quick pinch: on a tied band, unties it. */
    fun pinchWork(id: String) {
        if (phase != Phase.MAKING) return
        if (work?.step is Step.Tie && id.startsWith("band:")) id.removePrefix("band:").toIntOrNull()?.let { band(it) }
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

    private fun showPot(reveal: Float = 1f) {
        val w = work ?: return
        val shape = order.steps.firstOrNull { it is Step.Shape } as? Step.Shape ?: return
        val painted = order.steps.indexOfFirst { it is Step.Tap && it.action == "paint" }
        // The pattern shows once the brush has been round (or while it goes round).
        val r = if (painted >= 0 && w.at < painted) 0f else if (painted >= 0 && w.at == painted) reveal else 1f
        view?.pot(w.rings, shape.height, picks["画青花"], picks["上釉"]?.let { it == 1 }, picks["选土"] == 1, stops["烧窑"], r)
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
        // Nothing is timed: the fires burn as high as the hand has set them.
        if (s is Step.Gauge) when (s.title) {
            "蒸煮" -> v?.boil(w.gauge)
            "烧窑" -> v?.kiln(w.gauge)
            "烧烟" -> v?.soot(w.gauge)
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

    /** Which grip sets each gauge step's amount. */
    private val GAUGE_GRIP = mapOf("柴火" to "fire", "竹帘" to "mould", "风箱" to "bellows", "灯芯" to "wick")
    private const val CARRY_NEAR = 0.035
    private const val SHAPE_STEP = 0.01

    // Debug hooks (AutoPilot).
    var debugOpen by mutableStateOf<String?>(null)
    var debugView by mutableStateOf<String?>(null)
    var faceRequest by mutableIntStateOf(0)
}
