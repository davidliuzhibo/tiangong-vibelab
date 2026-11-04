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
    private var struck = -1

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

    /** Does the current step (polling the timed ones every 30 ms) until the order is made. */
    private fun solve() {
        val w = Game.work ?: return
        if (Game.phase != Game.Phase.MAKING) return
        var wait = 600L
        when (val s = w.step ?: return) {
            is Step.Choice -> Game.choose(if (sloppy) (s.answer + 1) % s.options.size else s.answer)
            is Step.Gauge -> {
                val aim = if (sloppy) s.target + 2.5 * s.tolerance else s.target
                if (w.gauge >= aim - 0.004) Game.stop() else wait = 30
            }
            is Step.Rhythm -> {
                val bounce = (w.rhythmT / s.period).toInt()
                if (w.beat > 0.9 && bounce != struck) {
                    struck = bounce
                    Game.strike()
                }
                wait = 30
            }
            is Step.Shape -> {
                for (i in 0 until 4) while (w.rings[i] != s.target[i]) Game.ring(i, if (w.rings[i] < s.target[i]) 1 else -1)
                if (sloppy) Game.ring(3, -2)
                Game.shaped()
            }
            is Step.Tie -> {
                val t = s.target
                Game.fold(t.fold)
                for (b in t.bands) Game.band(b)
                if (sloppy) Game.band(if (0 in t.bands) 4 else 0)
                while (w.dips < t.dips) Game.dips(1)
                Game.dye()
                wait = 2200
            }
            is Step.Tap -> Game.tap()
        }
        main.postDelayed(::solve, wait)
    }

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
