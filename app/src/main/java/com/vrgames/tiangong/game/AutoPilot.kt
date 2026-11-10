package com.vrgames.tiangong.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.tiangong.core.Orders
import com.vrgames.tiangong.core.Step
import com.vrgames.tiangong.core.Vec3

/**
 * Test hook, debug builds only: makes orders without hands, so each can be checked on the
 * emulator. Results go to logcat (tag TiangongTest).
 *
 *   adb shell am broadcast -a com.vrgames.tiangong.TEST -p com.vrgames.tiangong --es cmd walk
 *
 * cmd: start · open --es id <order> · begin · solve (every step done right) · sloppy (every step a
 * little wrong) · walk (all twelve orders and the gift) · grant --es id <stars> · view --es id
 * "x,y,z,yaw,scale,pitch" · window --es id orders|book|menu|close · reset · stop
 */
object AutoPilot {
    const val ACTION = "com.vrgames.tiangong.TEST"
    const val TAG = "TiangongTest"
    private val main = Handler(Looper.getMainLooper())
    var debug = false; private set
    private var walkAt = 0
    private var sloppy = false

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun ready() = Log.i(TAG, "workshop ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "start" -> Game.start()
            "open" -> runCatching { Orders.byId(id ?: "") }.getOrNull()?.let { Game.open(it) }
            "begin" -> Game.begin()
            "solve" -> { sloppy = false; main.removeCallbacksAndMessages(null); solve() }
            "sloppy" -> { sloppy = true; main.removeCallbacksAndMessages(null); solve() }
            "walk" -> { sloppy = false; main.removeCallbacksAndMessages(null); walkAt = 0; main.postDelayed(::walkStep, 300) }
            "grant" -> Game.grant(id?.toIntOrNull() ?: 3)
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "window" -> Game.debugOpen = id
            "reset" -> { main.removeCallbacksAndMessages(null); Game.restart() }
            "stop" -> main.removeCallbacksAndMessages(null)
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    /**
     * Does the current step the way a hand would (v0.2): takes the step's tool, moves it, lets go,
     * through the same moves as the stage's drags, until the order is made.
     */
    private fun solve() {
        val w = Game.work ?: return
        if (Game.phase != Game.Phase.MAKING) return
        var wait = 500L
        val v = Game.view
        when (val s = w.step ?: return) {
            is Step.Choice -> Game.choose(if (sloppy) (s.answer + 1) % s.options.size else s.answer)
            is Step.Gauge -> {
                val aim = (if (sloppy) s.target + 2.5 * s.tolerance else s.target).coerceIn(0.0, 1.0)
                if (s.tool == "竹帘") {
                    // Down into the pulp as deep as the paper is thick, then up and out.
                    if (Game.grab("grip:mould")) {
                        Game.dragBy(Vec3(0.0, -aim * 0.044, 0.0))
                        Game.dragBy(Vec3(0.0, aim * 0.044 + 0.03, 0.0))
                        Game.release()
                    }
                } else if (Game.grab("grip:" + GRIPS.getValue(s.tool))) {
                    Game.dragBy(Vec3(0.0, aim * 0.06 + 1e-6, 0.0))
                    Game.release()
                    Game.stop()
                }
            }
            is Step.Rhythm -> {
                if (Game.grab("grip:" + if (s.tool == "墨锤") "hammer" else "pestle")) {
                    Game.dragBy(Vec3(0.0, -0.045, 0.0))
                    Game.release()
                }
                wait = 250
            }
            is Step.Shape -> {
                for (i in 0 until 4) {
                    val diff = s.target[i] - w.rings[i] + if (sloppy && i == 3) -2 else 0
                    if (diff != 0 && Game.grab("grip:ring$i")) {
                        Game.dragBy(Vec3(diff * 0.0105, 0.0, 0.0))
                        Game.release()
                    }
                }
                Game.shaped()
            }
            is Step.Tie -> {
                val t = s.target
                if (w.fold == null) {
                    Game.fold(t.fold)
                } else if (w.dips == 0 && !w.bands.containsAll(t.bands)) {
                    // The rope from its coil onto the bundle at the next band place.
                    val b = t.bands.first { it !in w.bands }
                    if (Game.grab("grip:rope")) {
                        Game.dragBy(Vec3(0.02 * (b - 2) + 0.03, 0.0, -0.05))
                        Game.release()
                    }
                } else if (sloppy && w.dips == 0 && w.bands.size == t.bands.size) {
                    val extra = if (0 in t.bands) 4 else 0
                    if (Game.grab("grip:rope")) {
                        Game.dragBy(Vec3(0.02 * (extra - 2) + 0.03, 0.0, -0.05))
                        Game.release()
                    }
                } else if (w.dips < t.dips) {
                    // Over to the vat, into it, up out of it, back.
                    if (Game.grab("grip:bundle")) {
                        Game.dragBy(Vec3(0.065, 0.0, -0.02))
                        Game.dragBy(Vec3(0.0, 0.08, 0.0))
                        Game.release()
                    }
                } else {
                    Game.dye()
                }
                wait = 400
            }
            is Step.Tap -> {
                val id = TAP_GRIPS[s.action] ?: if (Game.order.craft == com.vrgames.tiangong.core.Craft.DYE) "cloth" else "stick"
                when (s.gesture) {
                    Step.Gesture.STROKES -> if (Game.grab("grip:$id")) {
                        Game.dragBy(Vec3(0.0, -0.045, 0.0))
                        Game.release()
                    }
                    Step.Gesture.RUB -> if (Game.grab("grip:$id")) {
                        for (k in 0 until 6) Game.dragBy(Vec3(if (k % 2 == 0) 0.03 else -0.03, 0.0, 0.0))
                        Game.release()
                    }
                    Step.Gesture.CARRY -> if (Game.grab("grip:$id")) {
                        val at = v?.gripAt(id)
                        val to = v?.dropAt(id)
                        if (at != null && to != null) Game.dragBy(to - at)
                        Game.release()
                    }
                }
                wait = 300
            }
        }
        main.postDelayed(::solve, wait)
    }

    private val GRIPS = mapOf("柴火" to "fire", "风箱" to "bellows", "灯芯" to "wick")
    private val TAP_GRIPS = mapOf("chop" to "knife", "press" to "press", "trim" to "trimmer", "paint" to "brush", "write" to "pen", "wrap" to "wrapcloth", "give" to "gift")

    private fun walkStep() {
        val o = Game.progress.next()
        if (o == null || walkAt++ > 20) {
            val p = Game.progress
            Log.i(TAG, "walk done stars=${p.stars}/${p.maxStars} gift=${p.giftGiven} bronze=${p.bronze} silver=${p.silver} gold=${p.gold}")
            return
        }
        Game.open(o)
        main.postDelayed({
            Game.begin()
            solve()
            waitResult(o.id)
        }, 2000)
    }

    private fun waitResult(id: String) {
        if (Game.phase == Game.Phase.RESULT) {
            Log.i(TAG, "result $id stars=${Game.progress.best[id]} quality=${Game.work?.quality}")
            main.postDelayed(::walkStep, 1500)
        } else main.postDelayed({ waitResult(id) }, 500)
    }
}
