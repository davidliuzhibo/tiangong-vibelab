package com.vrgames.tiangong.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.tiangong.core.Vec3

/** Effects, all made by tools/sfx_synth.py (see docs/素材来源.md). */
enum class Sfx(val file: String, val volume: Float) {
    CHOP("chop", 0.8f),
    POUND("pound", 0.8f),
    /** The beat ball at the top. */
    BEAT("beat", 0.35f),
    MISS("miss", 0.5f),
    DIP("dip", 0.7f),
    DROP("drop", 0.6f),
    FIRE("fire", 0.6f),
    TOOL("tool", 0.5f),
    CHOOSE("choose", 0.5f),
    CLOTH("cloth", 0.6f),
    GOOD("good", 0.8f),
    BAD("bad", 0.6f),
    DONE("done", 0.9f),
    GIFT("gift", 0.9f),
}

/**
 * Spatial sound from the scene: each effect has one long-lived emitter with a prepared player,
 * moved to where the sound happens and restarted (creating players per event drops frames).
 * Main thread only.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    var enabled = true

    /** Loads every clip; a clip that fails is skipped (logged). */
    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
    }

    /** Plays [s] at [at] (in [parent]'s frame; by default the middle of the parent). */
    fun play(s: Sfx, at: Vec3 = Vec3(0.0, 0.1, 0.0)) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    companion object {
        private const val TAG = "GameSound"
    }
}
