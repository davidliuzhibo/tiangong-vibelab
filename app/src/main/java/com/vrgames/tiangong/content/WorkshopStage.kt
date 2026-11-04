package com.vrgames.tiangong.content

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.tiangong.BOOK_ID
import com.vrgames.tiangong.ORDERS_ID
import com.vrgames.tiangong.game.AutoPilot
import com.vrgames.tiangong.game.Game
import com.vrgames.tiangong.render.Music
import com.vrgames.tiangong.render.Sound
import com.vrgames.tiangong.render.TableFinder
import com.vrgames.tiangong.render.WorkshopView
import com.vrgames.tiangong.render.setPos
import com.vrgames.tiangong.render.shared
import com.vrgames.tiangong.render.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The workshop on the player's real table (Stage, mixed reality). The left panel leads through the
 * order's steps; a pinch on the front station (or on one of its tools) does the step's action the
 * moment the fingers close: stops the gauge, strikes the beat, cuts, presses, dries.
 */
@Composable
fun WorkshopStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<WorkshopView?>(null) }
    val place = remember { Place() }
    val panels = remember { HashMap<String, Entity>() }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head tracking ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            TableFinder.stop()
            subscription[0]?.cancel()
            Game.closeWindow(ORDERS_ID)
            Game.closeWindow(BOOK_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) place.again() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 40) 10000 else 6000)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.debugView, view) {
        val v = view ?: return@LaunchedEffect
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            place.debug = true
            v.root.setPos(Vector3(d[0], d[1], d[2]))
            v.root.transform().eulerAngles = EulerAngles(d[5], d[3], 0f)
            v.root.transform().setScaleVector(Vector3(d[4]))
        } else {
            place.debug = false
            v.root.transform().setScaleVector(Vector3(1f))
            place.again()
        }
    }
    LaunchedEffect(view) {
        if (view == null) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        val (x, z) = place.lastPose ?: return@LaunchedEffect
        TableFinder.find(x, z, place.heading)?.let { found -> place.table(found.height, found.distance) }
    }
    LaunchedEffect(Game.phase, Game.menuOpen, Game.note, view) {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val ph = Game.phase
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_ORDER, ph == Game.Phase.ORDER)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_STEP, ph == Game.Phase.MAKING || ph == Game.Phase.DONE)
        show(PANEL_NOTE, Game.note != null)
        show(PANEL_MENU, true)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "orders" -> if (Game.window != ORDERS_ID) toggleWindow(ORDERS_ID)
            "book" -> if (Game.window != BOOK_ID) toggleWindow(BOOK_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                val v = view
                if (v != null) for (i in infos) {
                    if (!i.isDownEvent()) continue
                    val id = v.hitOf(i.targetedEntity) ?: continue
                    Log.i(INPUT_TAG, "pinch $id")
                    main.post { Game.pinchWork(id) }
                }
                false
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_ORDER) { OrderPanel(converter) }
            AttachmentPanel(PANEL_STEP) { StepPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments -> view?.let { attach(it, attachments, panels) } },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFF2DE).toColor4(), 1000f, true))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-55f, 25f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            withContext(Dispatchers.IO) {
                for (t in listOf("floor", "wall", "roof")) runCatching { TextureResource.load("textures/$t.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.onSuccess { tex[t] = it }
            }
            val models = HashMap<String, Entity>()
            for (n in MODELS) runCatching { Entity.loadSuspend("asset://models/$n.glb") }.onSuccess { models[n] = it }
            Log.i(TAG, "models ${models.keys.sorted()} textures ${tex.keys.sorted()}")
            val v = WorkshopView(models, tex)
            place.root = v.root
            place.put()
            content.addEntity(v.root)
            attach(v, attachments, panels)
            Game.view = v
            Game.sound = Sound(v.root).also { it.load() }
            Game.showTrophies()
            view = v
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                if (pose != null) place.frame(pose.position, pose.rotation.rotateVector(Vector3(0f, 0f, -1f)), dt, main)
                main.post { Game.tick(dt) }
                v.frame(dt)
            }
        },
    )
}

private val MODELS = listOf("bamboo", "vat", "mortar", "mould", "wheel", "kiln", "dyevat", "inklamp", "inkmold", "inkstone", "guest")

private fun attach(v: WorkshopView, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        v.root.addChild(e)
        e.setPos(pos)
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // Table frame: the cards stand above the shed's eave; the step panel at the left, turned to
    // the player; notes over the bench.
    put(PANEL_CARD, Vector3(0f, 0.46f, -0.12f), 0f)
    put(PANEL_ORDER, Vector3(0f, 0.46f, -0.12f), 0f)
    put(PANEL_RESULT, Vector3(0f, 0.46f, -0.12f), 0f)
    put(PANEL_STEP, Vector3(-0.47f, 0.26f, 0.02f), -12f, 32f)
    put(PANEL_NOTE, Vector3(-0.47f, 0.58f, 0.02f), -8f, 32f)
    put(PANEL_MENU, Vector3(-0.36f, 0.04f, 0.12f), -40f, 25f)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private const val TAG = "WorkshopStage"
/** Every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "WorkshopInput"
