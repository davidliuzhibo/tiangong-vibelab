package com.vrgames.tiangong.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import com.vrgames.tiangong.core.Craft
import com.vrgames.tiangong.core.Dye
import com.vrgames.tiangong.core.Fold
import com.vrgames.tiangong.core.Made
import com.vrgames.tiangong.core.Vec3
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The little workshop on the player's table (mixed reality). Frame: the root on the table top, +z
 * toward the player. An open shed (a back wall with lattice windows, two posts, a tiled eave) over
 * a round bench that turns its four stations — paper, porcelain, dye, ink — to the front as the
 * game moves on. Each station has its tools (AI models when present, simple shapes otherwise) and
 * shows what is being made, built from the player's own work: the sheet of paper (its fibers and
 * flaws), the pot thrown on the wheel (a lathe of its four rings, its blue pattern, its firing),
 * the tie-dyed cloth (its pattern computed from the fold and the ties), the ink stick. Over the
 * front station float the gauge of the timed steps and the bouncing beat of the pounding.
 * Pinchable: the front station's work ("work"), the tools ("tool:<name>"). The guest stands at the
 * left, the trophy stand at the right.
 */
class WorkshopView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    val root = Entity()
    /** The workshop, built at its design size and shown at [SCALE] (0.84 m wide read as a whole desk). */
    private val shop = Entity().also { it.setScale(SCALE); root.addChild(it) }
    private val hits = HashMap<Entity, String>()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val smoke = Sparks("smoke", 120, sphere, pbr(color(0.12f, 0.12f, 0.12f, 0.55f), roughness = 1f, blending = BlendingMode.TRANSPARENT).shared()).also { shop.addChild(it.entity) }
    private val steam = Sparks("steam", 120, sphere, pbr(color(0.95f, 0.95f, 0.95f, 0.5f), roughness = 1f, blending = BlendingMode.TRANSPARENT).shared()).also { shop.addChild(it.entity) }
    private val sparks = Sparks("sparks", 120, sphere, glow(color(1f, 0.6f, 0.2f, 1f)).shared()).also { shop.addChild(it.entity) }
    private val chips = Sparks("chips", 80, sphere, pbr(color(0.55f, 0.65f, 0.3f), roughness = 0.8f).shared()).also { shop.addChild(it.entity) }
    private val drops = Sparks("drops", 120, sphere, pbr(color(0.2f, 0.3f, 0.6f, 0.85f), roughness = 0.2f, blending = BlendingMode.TRANSPARENT).shared()).also { shop.addChild(it.entity) }
    private val stars = Sparks("stars", 120, sphere, glow(color(1f, 0.85f, 0.45f, 1f)).shared()).also { shop.addChild(it.entity) }
    val bench = Entity().also { it.setPos(Vector3(0f, BENCH_TOP, BENCH_Z)); shop.addChild(it) }
    private val stations = HashMap<Craft, Entity>()
    private var front: Craft = Craft.PAPER
    private var yaw = 0f
    private var yawGoal = 0f
    private var clock = 0.0

    private val wood = pbr(color(0.5f, 0.34f, 0.2f), roughness = 0.6f).shared()
    private val darkWood = pbr(color(0.28f, 0.18f, 0.11f), roughness = 0.7f).shared()
    private val lacquer = pbr(color(0.45f, 0.1f, 0.08f), roughness = 0.35f).shared()
    private val bamboo = pbr(color(0.62f, 0.68f, 0.38f), roughness = 0.6f).shared()
    private val stone = pbr(color(0.55f, 0.55f, 0.52f), roughness = 0.9f).shared()
    private val red = pbr(color(0.75f, 0.12f, 0.1f), roughness = 0.6f).shared()
    private val rope = pbr(color(0.62f, 0.5f, 0.32f), roughness = 0.9f).shared()
    private val gold = pbr(color(1f, 0.78f, 0.3f), metallic = 0.95f, roughness = 0.25f).shared()
    private val bronze = pbr(color(0.72f, 0.45f, 0.25f), metallic = 0.9f, roughness = 0.35f).shared()
    private val pulpMat = pbr(color(0.85f, 0.85f, 0.7f), roughness = 0.9f).shared()
    private val indigo = pbr(color(0.1f, 0.16f, 0.42f), roughness = 0.3f).shared()

    // The pieces being made.
    private val paperSheet: Entity
    private val wetSheet: Entity
    private val paperMat = PhysicallyBasedMaterial.create().apply { setRoughness(0.95f); setCullingMode(MaterialCullingMode.NONE) }.shared()
    private var paperTex: TextureResource? = null
    private val pot = Entity()
    private val potHolder = Entity()
    private var potMesh: Entity? = null
    private val potMat = PhysicallyBasedMaterial.create().apply { setRoughness(0.15f) }.shared()
    private var potTex: TextureResource? = null
    private val clothRack = Entity()
    private val cloth: Entity
    private val clothMat = PhysicallyBasedMaterial.create().apply { setRoughness(0.9f); setCullingMode(MaterialCullingMode.NONE) }.shared()
    private var clothTex: TextureResource? = null
    private val targetMat = PhysicallyBasedMaterial.create().apply { setRoughness(0.9f); setCullingMode(MaterialCullingMode.NONE) }.shared()
    private var targetTex: TextureResource? = null
    private val bundle = Entity()
    private val bundleMat = pbr(color(0.95f, 0.94f, 0.9f), roughness = 0.9f).shared()
    private val inkStick = Entity()
    private val pestle = Entity()
    private val hammer = Entity()
    private val mould = Entity()
    private var mortar: Entity? = null
    private val kilnGlowMat = glow(color(1f, 0.45f, 0.15f, 0f)).shared()
    private var strikeT = 0.0
    private var strikeAt: Craft? = null

    // ---- v0.2: the tools the player's hand moves ----

    /** A tool the hand moves: its rest place in the front station's frame (or the shop's), and where the hand has put it. */
    private class Grip(val node: Entity, val rest: Vector3, val shopFrame: Boolean) {
        var off = Vector3(0f, 0f, 0f)
    }

    private val grips = HashMap<String, Grip>()
    /** Grips that come and go with their step (the rest are always on the bench). */
    private val optional = HashSet<String>()
    private var glinting: Set<String> = emptySet()
    private var glintT = 0.0
    private var potRings = IntArray(4) { 3 }
    private var potH = 0.1f
    private val dropMark = Entity().also { it.enabled = false }
    private var wheelSpin = false
    private var showing: Craft? = null
    private var showT = 0.0
    private val shownBase = HashMap<Craft, Vector3>()

    // The gauge and the beat over the front station.
    private val meter = Entity().also { it.setPos(Vector3(0f, 0.27f, 0.1f)); it.transform().eulerAngles = EulerAngles(-12f, 0f, 0f); it.enabled = false; shop.addChild(it) }
    private val needle: Entity
    private val zone: Entity
    private val meterBarMat = UnlitMaterial.create(BlendingMode.OPAQUE).shared()
    private var meterTex: TextureResource? = null
    private val beatRoot = Entity().also { it.setPos(Vector3(0.15f, 0f, 0.1f)); it.enabled = false; shop.addChild(it) }
    private val ball: Entity
    private val topRing: Entity
    private var flash = 0.0

    private val gift = Entity().also { it.enabled = false; shop.addChild(it) }
    private val guest = Entity().also { shop.addChild(it) }
    private var bowT = -1.0
    private val trophyRoot = Entity().also { shop.addChild(it) }
    private val cabinetRoot = Entity().also { shop.addChild(it) }
    private val plaqueRoot = Entity().also { shop.addChild(it) }

    init {
        shed()
        // The round bench and its foot.
        bench.addChild(model(MeshResource.createCylinder(0.024f, BENCH_R), wood).apply { setPos(Vector3(0f, -0.012f, 0f)) })
        bench.addChild(model(MeshResource.createTorus(BENCH_R + 0.004f, BENCH_R - 0.006f), darkWood))
        shop.addChild(model(MeshResource.createCylinder(BENCH_TOP - BASE_TOP - 0.02f, 0.05f), darkWood).apply { setPos(Vector3(0f, BASE_TOP + (BENCH_TOP - BASE_TOP - 0.02f) / 2, BENCH_Z)) })
        for ((k, c) in ORDER.withIndex()) {
            // Station k faces the player when the bench is turned by -90k degrees.
            val s = Entity().apply {
                val a = Math.toRadians(90.0 * k)
                setPos(Vector3((STATION_R * sin(a)).toFloat(), 0f, (STATION_R * cos(a)).toFloat()))
                transform().eulerAngles = EulerAngles(0f, 90f * k, 0f)
            }
            bench.addChild(s)
            stations[c] = s
        }

        // Paper: vat with pulp, mortar and pestle, the mould, bamboo, the drying board.
        val ps = stations.getValue(Craft.PAPER)
        place(ps, "vat", 0.08f, Vector3(-0.065f, 0f, -0.01f)) { model(MeshResource.createBox(Vector3(0.055f, 0.035f, 0.08f), 0.004f), wood).apply { setPos(Vector3(0f, 0.0175f, 0f)) } }
        // The pulp's surface, inside the vat (the AI vat is long front to back and only 2 cm deep).
        val aiVat = models["vat"] != null
        ps.addChild(model(MeshResource.createBox(if (aiVat) Vector3(0.038f, 0.002f, 0.068f) else Vector3(0.046f, 0.002f, 0.07f), 0.001f), pulpMat).apply { setPos(Vector3(-0.065f, if (aiVat) 0.016f else 0.034f, -0.01f)) })
        mortar = place(ps, "mortar", 0.055f, Vector3(0.065f, 0f, -0.025f)) { model(MeshResource.createCylinder(0.035f, 0.022f), stone).apply { setPos(Vector3(0f, 0.0175f, 0f)) } }
        place(ps, "bamboo", 0.09f, Vector3(0.0f, 0f, 0.055f), yaw = 90f, tool = "bamboo") { bambooFallback() }
        // The pestle the hand lifts and brings down (the AI mortar's own pestle stays leaning in it).
        pestle.addChild(model(MeshResource.createCylinder(0.08f, 0.005f), wood))
        pestle.addChild(model(MeshResource.createCylinder(0.02f, 0.009f), wood).apply { setPos(Vector3(0f, -0.04f, 0f)) })
        grip("pestle", ps, pestle, Vector3(0.065f, 0.1f, -0.025f), Vector3(0.03f, 0.1f, 0.03f))
        (models["mould"]?.clone(Entity.CloneOptions(true, true))?.also { it.setScale(0.07f) } ?: model(MeshResource.createBox(Vector3(0.07f, 0.004f, 0.045f), 0.001f), bamboo)).let { mould.addChild(it) }
        grip("mould", ps, mould, Vector3(-0.065f, 0.06f, -0.01f), Vector3(0.08f, 0.03f, 0.06f))
        // The chopper over the bamboo; the firewood before the vat; the press board.
        grip("knife", ps, tool("chopper", 0.085f, EulerAngles(0f, 0f, 0f), lift = -0.007f) ?: Entity().apply {
            addChild(model(MeshResource.createBox(Vector3(0.045f, 0.016f, 0.003f), 0.001f), pbr(color(0.62f, 0.63f, 0.66f), metallic = 0.85f, roughness = 0.35f)))
            addChild(model(MeshResource.createCylinder(0.03f, 0.004f), darkWood).apply { setPos(Vector3(0.035f, 0.004f, 0f)); transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
        }, Vector3(0f, 0.05f, 0.055f), Vector3(0.08f, 0.04f, 0.03f), optional = true)
        grip("fire", ps, Entity().apply {
            for (k in -1..1) addChild(model(MeshResource.createCylinder(0.035f, 0.0035f), darkWood).apply { setPos(Vector3(k * 0.007f, 0f, 0f)); transform().eulerAngles = EulerAngles(70f, 0f, k * 12f) })
            addChild(model(sphere, glow(color(1f, 0.45f, 0.12f, 0.8f))).apply { setPos(Vector3(0f, 0.004f, -0.012f)); setScale(0.006f) })
        }, Vector3(-0.065f, 0.008f, 0.045f), Vector3(0.04f, 0.04f, 0.04f))
        grip("press", ps, Entity().apply {
            addChild(model(MeshResource.createBox(Vector3(0.07f, 0.006f, 0.05f), 0.002f), darkWood))
            addChild(model(MeshResource.createCylinder(0.02f, 0.004f), wood).apply { setPos(Vector3(0f, 0.013f, 0f)) })
        }, Vector3(-0.065f, 0.075f, -0.01f), Vector3(0.08f, 0.04f, 0.06f), optional = true)
        ps.addChild(model(MeshResource.createBox(Vector3(0.085f, 0.1f, 0.006f), 0.002f), darkWood).apply { setPos(Vector3(0.01f, 0.05f, -0.07f)) })
        paperSheet = model(quad(0.07f, 0.085f), paperMat).apply { setPos(Vector3(0.01f, 0.052f, -0.0665f)); enabled = false }
        ps.addChild(paperSheet)
        wetSheet = model(MeshResource.createBox(Vector3(0.062f, 0.001f, 0.04f), 0.0005f), pbr(color(0.9f, 0.9f, 0.85f, 0.75f), roughness = 0.4f, blending = BlendingMode.TRANSPARENT)).apply { setPos(Vector3(0f, 0.004f, 0f)); enabled = false }
        mould.addChild(wetSheet)

        // Porcelain: the wheel with the pot on it, the kiln.
        val cs = stations.getValue(Craft.PORCELAIN)
        place(cs, "wheel", 0.07f, Vector3(-0.045f, 0f, 0.01f), tool = "wheel") { wheelFallback() }
        // The pot stands on the wheel head (the v0.2 wheel is a plain, taller head with no clay of its own).
        potHolder.setPos(Vector3(-0.045f, if (models["wheel"] != null) 0.063f else 0.048f, 0.01f))
        potHolder.addChild(pot)
        cs.addChild(potHolder)
        // The AI kiln is a long tunnel kiln (龙窑): 14 cm long, its mouth toward the player.
        if (models["kiln"] != null) {
            place(cs, "kiln", 0.14f, Vector3(0.065f, 0f, -0.03f), tool = "kiln") { kilnFallback() }
            cs.addChild(model(sphere, kilnGlowMat).apply { setPos(Vector3(0.065f, 0.012f, 0.038f)); setScale(0.014f) })
        } else {
            place(cs, "kiln", 0.08f, Vector3(0.065f, 0f, -0.03f), tool = "kiln") { kilnFallback() }
            cs.addChild(model(sphere, kilnGlowMat).apply { setPos(Vector3(0.065f, 0.03f, -0.005f)); setScale(0.028f) })
        }

        for (i in 0 until 4) grip("ring$i", cs, model(sphere, glow(color(1f, 0.85f, 0.4f, 1f))).apply { setScale(0.0045f) }, Vector3(0f, 0f, 0f), Vector3(0.022f, 0.022f, 0.022f), optional = true)
        grip("trimmer", cs, Entity().apply {
            addChild(model(MeshResource.createCylinder(0.04f, 0.0035f), wood).apply { transform().eulerAngles = EulerAngles(0f, 0f, 70f) })
            addChild(model(MeshResource.createBox(Vector3(0.012f, 0.004f, 0.008f), 0.001f), pbr(color(0.62f, 0.63f, 0.66f), metallic = 0.85f, roughness = 0.35f)).apply { setPos(Vector3(-0.019f, -0.007f, 0f)) })
        }, Vector3(-0.005f, 0.075f, 0.04f), Vector3(0.05f, 0.04f, 0.04f), optional = true)
        grip("brush", cs, brushModel(), Vector3(-0.005f, 0.08f, 0.04f), Vector3(0.03f, 0.06f, 0.03f), optional = true)
        grip("bellows", cs, Entity().apply {
            addChild(model(MeshResource.createBox(Vector3(0.006f, 0.05f, 0.006f), 0.001f), wood).apply { setPos(Vector3(0f, 0.025f, 0f)) })
            addChild(model(MeshResource.createBox(Vector3(0.02f, 0.008f, 0.008f), 0.002f), darkWood).apply { setPos(Vector3(0f, 0.052f, 0f)) })
        }, Vector3(0.12f, 0.0f, 0.05f), Vector3(0.03f, 0.07f, 0.03f))
        cs.addChild((tool("bellows", 0.05f, EulerAngles(0f, 90f, 0f)) ?: model(MeshResource.createBox(Vector3(0.03f, 0.018f, 0.034f), 0.004f), pbr(color(0.4f, 0.27f, 0.17f), roughness = 0.8f)).apply { setPos(Vector3(0f, 0.009f, 0f)) }).apply { setPos(Vector3(0.12f, 0f, 0.05f)) })

        // Dye: the vat, the cloth on a bamboo rack, the order's pattern on a little board, the bundle.
        val ds = stations.getValue(Craft.DYE)
        place(ds, "dyevat", 0.055f, Vector3(0.065f, 0f, 0f), tool = "dyevat") {
            Entity().apply {
                addChild(model(MeshResource.createCylinder(0.05f, 0.03f), pbr(color(0.35f, 0.25f, 0.18f), roughness = 0.7f)).apply { setPos(Vector3(0f, 0.025f, 0f)) })
                addChild(model(MeshResource.createCylinder(0.002f, 0.027f), indigo).apply { setPos(Vector3(0f, 0.049f, 0f)) })
            }
        }
        clothRack.setPos(Vector3(-0.045f, 0f, -0.04f))
        for (x in listOf(-0.05f, 0.05f)) clothRack.addChild(model(MeshResource.createCylinder(0.12f, 0.003f), bamboo).apply { setPos(Vector3(x, 0.06f, 0f)) })
        clothRack.addChild(model(MeshResource.createCylinder(0.11f, 0.0028f), bamboo).apply { setPos(Vector3(0f, 0.118f, 0f)); transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
        cloth = model(quad(0.09f, 0.09f), clothMat).apply { setPos(Vector3(0f, 0.071f, 0.002f)); enabled = false }
        clothRack.addChild(cloth)
        ds.addChild(clothRack)
        ds.addChild(model(MeshResource.createBox(Vector3(0.058f, 0.058f, 0.004f), 0.001f), darkWood).apply { setPos(Vector3(-0.005f, 0.03f, 0.065f)); transform().eulerAngles = EulerAngles(-60f, 0f, 0f) })
        ds.addChild(model(quad(0.05f, 0.05f), targetMat).apply { setPos(Vector3(-0.005f, 0.031f, 0.067f)); transform().eulerAngles = EulerAngles(-60f, 0f, 0f) })
        grip("bundle", ds, bundle, BUNDLE_REST, Vector3(0.11f, 0.03f, 0.05f))
        grip("rope", ds, model(MeshResource.createTorus(0.008f, 0.005f), rope), Vector3(-0.03f, 0.004f, 0.07f), Vector3(0.03f, 0.02f, 0.03f), optional = true)
        grip("cloth", ds, model(MeshResource.createBox(Vector3(0.05f, 0.008f, 0.04f), 0.003f), clothMat), Vector3(0f, 0.006f, 0.05f), Vector3(0.06f, 0.03f, 0.05f), optional = true)

        // Ink: the lamp gathering soot, the mould, the inkstone, the hammer, the stick.
        val ks = stations.getValue(Craft.INK)
        place(ks, "inklamp", 0.06f, Vector3(-0.065f, 0f, -0.02f), tool = "inklamp") { lampFallback() }
        place(ks, "inkmold", 0.06f, Vector3(0.06f, 0f, -0.035f), tool = "inkmold") { model(MeshResource.createBox(Vector3(0.06f, 0.02f, 0.03f), 0.003f), wood).apply { setPos(Vector3(0f, 0.01f, 0f)) } }
        place(ks, "inkstone", 0.07f, Vector3(-0.005f, 0f, 0.055f)) { model(MeshResource.createBox(Vector3(0.06f, 0.012f, 0.04f), 0.004f), stone).apply { setPos(Vector3(0f, 0.006f, 0f)) } }
        ks.addChild(model(MeshResource.createCylinder(0.012f, 0.03f), stone).apply { setPos(Vector3(0.03f, 0.006f, 0.02f)) })
        // The ink mallet: the model stands head up, so it is turned over (head down, at the holder).
        tool("mallet", 0.07f, EulerAngles(0f, 0f, 180f), lift = 0.07f)?.let { hammer.addChild(it) } ?: run {
            hammer.addChild(model(MeshResource.createCylinder(0.07f, 0.0035f), wood).apply { setPos(Vector3(0f, 0.035f, 0f)) })
            hammer.addChild(model(MeshResource.createBox(Vector3(0.03f, 0.016f, 0.016f), 0.003f), darkWood))
        }
        grip("hammer", ks, hammer, Vector3(0.03f, 0.05f, 0.02f), Vector3(0.04f, 0.08f, 0.03f))
        grip("wick", ks, Entity().apply {
            addChild(model(MeshResource.createCylinder(0.03f, 0.0015f), rope).apply { setPos(Vector3(0f, 0.015f, 0f)) })
            addChild(model(sphere, glow(color(1f, 0.7f, 0.3f, 1f))).apply { setPos(Vector3(0f, 0.032f, 0f)); setScale(0.003f) })
        }, Vector3(-0.035f, 0.01f, 0.015f), Vector3(0.025f, 0.05f, 0.025f))
        // The ash box where the sticks dry; the stick the hand carries there.
        ks.addChild(model(MeshResource.createBox(Vector3(0.05f, 0.014f, 0.035f), 0.003f), darkWood).apply { setPos(Vector3(0.075f, 0.007f, 0.055f)) })
        ks.addChild(model(MeshResource.createBox(Vector3(0.044f, 0.002f, 0.029f), 0.001f), pbr(color(0.62f, 0.6f, 0.58f), roughness = 1f)).apply { setPos(Vector3(0.075f, 0.0145f, 0.055f)) })
        inkStick.setPos(Vector3(0.075f, 0.018f, 0.055f))
        ks.addChild(inkStick)
        grip("stick", ks, Entity(), Vector3(0.03f, 0.008f, 0.02f), Vector3(0.07f, 0.03f, 0.03f), optional = true)

        // The gift's tools (in the shop's frame, by the gift): the brush, the cloth, the wrapped bowl.
        grip("pen", shop, brushModel(), Vector3(-0.2f, BASE_TOP + 0.1f, 0.12f), Vector3(0.03f, 0.06f, 0.03f), optional = true, shopFrame = true)
        grip("wrapcloth", shop, model(MeshResource.createBox(Vector3(0.05f, 0.008f, 0.04f), 0.003f), clothMat), Vector3(-0.12f, BASE_TOP + 0.005f, 0.1f), Vector3(0.06f, 0.03f, 0.05f), optional = true, shopFrame = true)
        grip("gift", shop, Entity(), Vector3(-0.22f, BASE_TOP, 0.06f), Vector3(0.09f, 0.08f, 0.09f), optional = true, shopFrame = true)
        dropMark.addChild(model(MeshResource.createTorus(1f, 0.9f), glow(color(1f, 0.85f, 0.4f, 0.9f))).apply { setScale(0.03f) })
        shop.addChild(dropMark)

        // The gauge: a bar with the good zone and the needle.
        meter.addChild(model(MeshResource.createBox(Vector3(METER_W + 0.012f, 0.03f, 0.004f), 0.003f), darkWood).apply { setPos(Vector3(0f, 0f, -0.003f)) })
        meter.addChild(model(quad(METER_W, 0.016f), meterBarMat))
        zone = model(quad(1f, 0.024f), glow(color(0.6f, 1f, 0.7f, 0.45f))).apply { setPos(Vector3(0f, 0f, 0.001f)) }
        meter.addChild(zone)
        needle = model(MeshResource.createBox(Vector3(0.003f, 0.032f, 0.003f), 0.0005f), glow(color(1f, 1f, 1f, 1f))).apply { setPos(Vector3(-METER_W / 2, 0f, 0.002f)) }
        meter.addChild(needle)
        // The beat: a ball bouncing up to a ring.
        beatRoot.addChild(model(MeshResource.createCylinder(0.13f, 0.0018f), darkWood).apply { setPos(Vector3(0f, 0.175f, -0.012f)) })
        topRing = model(MeshResource.createTorus(0.016f, 0.012f), glow(color(1f, 0.85f, 0.4f, 0.8f))).apply { setPos(Vector3(0f, BEAT_TOP, 0f)); transform().eulerAngles = EulerAngles(90f, 0f, 0f) }
        beatRoot.addChild(topRing)
        ball = model(sphere, pbr(color(1f, 0.75f, 0.3f), metallic = 0.6f, roughness = 0.3f)).apply { setScale(0.009f) }
        beatRoot.addChild(ball)

        // The guest at the left, the trophy stand (covered until earned) at the right.
        val g = models["guest"]?.clone(Entity.CloneOptions(true, true))
        if (g != null) { g.setScale(0.17f); g.transform().eulerAngles = EulerAngles(0f, -90f + 40f, 0f); guest.addChild(g) } else guest.addChild(guestFallback())
        guest.setPos(Vector3(-0.33f, BASE_TOP, 0.0f))
        trophies(0, Made(), false)
    }

    // ---- The shed ----

    private fun shed() {
        val floor = tex["floor"]?.let { texMat(it, 0.3f) } ?: PhysicallyBasedMaterial.create().apply { setBaseColorTexture(TextureResource.create(planks())); setRoughness(0.75f) }.shared()
        shop.addChild(model(MeshResource.createBox(Vector3(BASE_W + 0.02f, 0.012f, BASE_D + 0.02f), 0.004f), darkWood).apply { setPos(Vector3(0f, 0.006f, -0.02f)) })
        shop.addChild(model(MeshResource.createBox(Vector3(BASE_W, 0.008f, BASE_D), 0.002f), floor).apply { setPos(Vector3(0f, 0.016f, -0.02f)) })
        // The back wall: plaster with two lattice windows.
        val plaster = tex["wall"]?.let { texMat(it, 0.3f) } ?: pbr(color(0.88f, 0.84f, 0.76f), roughness = 0.95f).shared()
        shop.addChild(model(MeshResource.createBox(Vector3(BASE_W, WALL_H, 0.012f), 0.002f), plaster).apply { setPos(Vector3(0f, BASE_TOP + WALL_H / 2, WALL_Z)) })
        val lattice = PhysicallyBasedMaterial.create().apply {
            val t = TextureResource.create(latticeBitmap())
            setBaseColorTexture(t); setEmissiveTexture(t); setEmissiveColor(color(0.55f, 0.5f, 0.4f)); setRoughness(0.9f)
        }.shared()
        for (x in listOf(-0.24f, 0.24f)) {
            shop.addChild(model(MeshResource.createBox(Vector3(0.17f, 0.15f, 0.016f), 0.002f), darkWood).apply { setPos(Vector3(x, BASE_TOP + 0.17f, WALL_Z)) })
            shop.addChild(model(quad(0.155f, 0.135f), lattice).apply { setPos(Vector3(x, BASE_TOP + 0.17f, WALL_Z + 0.0085f)) })
        }
        // Posts on stone bases, the beam, the eave of grey tiles.
        for (x in listOf(-BASE_W / 2 + 0.015f, BASE_W / 2 - 0.015f)) {
            shop.addChild(model(MeshResource.createCylinder(WALL_H + 0.05f, 0.011f), lacquer).apply { setPos(Vector3(x, BASE_TOP + (WALL_H + 0.05f) / 2, WALL_Z + 0.03f)) })
            shop.addChild(model(MeshResource.createCylinder(0.012f, 0.02f), stone).apply { setPos(Vector3(x, BASE_TOP + 0.006f, WALL_Z + 0.03f)) })
        }
        shop.addChild(model(MeshResource.createBox(Vector3(BASE_W + 0.04f, 0.022f, 0.022f), 0.003f), lacquer).apply { setPos(Vector3(0f, BASE_TOP + WALL_H + 0.035f, WALL_Z + 0.03f)) })
        val tiles = tex["roof"]?.let { texMat(it, 0.25f) } ?: PhysicallyBasedMaterial.create().apply { setBaseColorTexture(TextureResource.create(tilesBitmap())); setRoughness(0.8f) }.shared()
        shop.addChild(model(MeshResource.createBox(Vector3(BASE_W + 0.1f, 0.012f, 0.13f), 0.003f), tiles).apply {
            setPos(Vector3(0f, BASE_TOP + WALL_H + 0.07f, WALL_Z + 0.035f)); transform().eulerAngles = EulerAngles(18f, 0f, 0f)
        })
        // Lanterns hanging from the beam.
        for (x in listOf(-0.3f, 0.3f)) {
            val l = Entity().apply { setPos(Vector3(x, BASE_TOP + WALL_H - 0.02f, WALL_Z + 0.04f)) }
            l.addChild(model(MeshResource.createCylinder(0.04f, 0.0008f), rope).apply { setPos(Vector3(0f, 0.035f, 0f)) })
            l.addChild(model(sphere, pbr(color(0.85f, 0.15f, 0.1f), roughness = 0.5f, emissive = color(0.6f, 0.15f, 0.05f))).apply { transform().setScaleVector(Vector3(0.022f, 0.026f, 0.022f)) })
            l.addChild(model(MeshResource.createCylinder(0.005f, 0.012f), gold).apply { setPos(Vector3(0f, 0.026f, 0f)) })
            l.addChild(model(MeshResource.createCylinder(0.005f, 0.012f), gold).apply { setPos(Vector3(0f, -0.026f, 0f)) })
            shop.addChild(l)
        }
        // Finished paper and pots against the back wall, for the feel of a working shop.
        for (k in 0 until 3) shop.addChild(model(MeshResource.createCylinder(0.03f + 0.01f * k, 0.012f), pbr(color(0.9f, 0.9f, 0.86f), roughness = 0.2f)).apply { setPos(Vector3(-0.14f + k * 0.03f, BASE_TOP + (0.03f + 0.01f * k) / 2, WALL_Z + 0.03f)) })
        for (k in 0 until 4) shop.addChild(model(MeshResource.createBox(Vector3(0.06f, 0.004f, 0.045f), 0.001f), pbr(color(0.93f, 0.9f, 0.82f), roughness = 0.95f)).apply { setPos(Vector3(0.15f, BASE_TOP + 0.002f + k * 0.0042f, WALL_Z + 0.04f)) })
    }

    private fun texMat(t: TextureResource, glowShare: Float) = PhysicallyBasedMaterial.create().apply {
        setBaseColorTexture(t); setEmissiveTexture(t); setEmissiveColor(color(glowShare, glowShare, glowShare)); setRoughness(0.8f)
    }.shared()

    private fun planks(): Bitmap {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint()
        var seed = 7
        fun r(): Float { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65536f }
        for (k in 0 until 8) {
            val base = 0.55f + r() * 0.15f
            p.color = android.graphics.Color.rgb((base * 150).toInt(), (base * 100).toInt(), (base * 62).toInt())
            cv.drawRect(0f, k * 32f, 256f, k * 32f + 31f, p)
            p.color = 0x22000000
            for (l in 0 until 6) cv.drawRect(0f, k * 32f + 3 + l * 5 + r() * 2, 256f, k * 32f + 4 + l * 5 + r() * 2, p)
            p.color = 0xFF3A2414.toInt()
            cv.drawRect(0f, k * 32f + 31f, 256f, k * 32f + 32f, p)
        }
        return b
    }

    private fun latticeBitmap(): Bitmap {
        val b = Bitmap.createBitmap(256, 224, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(0xFFF1E6CC.toInt())
        val p = Paint().apply { color = 0xFF4A2E1A.toInt(); strokeWidth = 7f; isAntiAlias = true }
        for (x in 0..256 step 32) cv.drawLine(x.toFloat(), 0f, x.toFloat(), 224f, p)
        for (y in 0..224 step 32) cv.drawLine(0f, y.toFloat(), 256f, y.toFloat(), p)
        p.strokeWidth = 4f
        for (k in 0 until 4) cv.drawLine(32f * k, 0f, 32f * k + 64f, 64f, p)
        return b
    }

    private fun tilesBitmap(): Bitmap {
        val b = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(0xFF4C5257.toInt())
        val p = Paint().apply { isAntiAlias = true }
        for (x in 0 until 16) {
            p.color = if (x % 2 == 0) 0xFF5E656B.toInt() else 0xFF3E4448.toInt()
            cv.drawRect(x * 16f, 0f, x * 16f + 14f, 128f, p)
        }
        p.color = 0xFF2E3336.toInt()
        for (y in 0 until 8) cv.drawRect(0f, y * 16f + 14f, 256f, y * 16f + 16f, p)
        return b
    }

    // ---- Simple shapes for missing models, so the game is whole without them ----

    private fun bambooFallback() = Entity().apply {
        for (k in 0 until 4) addChild(model(MeshResource.createCylinder(0.09f, 0.005f), bamboo).apply { setPos(Vector3(0f, 0.006f + (k / 2) * 0.009f, -0.008f + (k % 2) * 0.01f)); transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
        addChild(model(MeshResource.createTorus(0.012f, 0.009f), rope).apply { setPos(Vector3(0f, 0.01f, 0f)); transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
    }

    private fun wheelFallback() = Entity().apply {
        addChild(model(MeshResource.createCylinder(0.04f, 0.01f), darkWood).apply { setPos(Vector3(0f, 0.02f, 0f)) })
        addChild(model(MeshResource.createCylinder(0.008f, 0.04f), wood).apply { setPos(Vector3(0f, 0.044f, 0f)) })
    }

    private fun kilnFallback() = Entity().apply {
        val brick = pbr(color(0.55f, 0.3f, 0.2f), roughness = 0.9f)
        addChild(model(MeshResource.createBox(Vector3(0.07f, 0.06f, 0.06f), 0.01f), brick).apply { setPos(Vector3(0f, 0.03f, 0f)) })
        addChild(model(sphere, brick).apply { setPos(Vector3(0f, 0.06f, 0f)); transform().setScaleVector(Vector3(0.035f, 0.022f, 0.03f)) })
    }

    private fun lampFallback() = Entity().apply {
        addChild(model(MeshResource.createCylinder(0.01f, 0.018f), stone).apply { setPos(Vector3(0f, 0.005f, 0f)) })
        addChild(model(MeshResource.createCylinder(0.05f, 0.003f), darkWood).apply { setPos(Vector3(0.02f, 0.03f, 0f)) })
        addChild(model(sphere, pbr(color(0.1f, 0.1f, 0.1f), roughness = 0.9f)).apply { setPos(Vector3(0f, 0.055f, 0f)); transform().setScaleVector(Vector3(0.025f, 0.01f, 0.025f)) })
    }

    private fun guestFallback() = Entity().apply {
        val robe = pbr(color(0.2f, 0.35f, 0.4f), roughness = 0.7f)
        addChild(model(MeshResource.createCone(0.1f, 0.04f), robe).apply { setPos(Vector3(0f, 0.05f, 0f)) })
        addChild(model(sphere, pbr(color(0.95f, 0.8f, 0.68f), roughness = 0.6f)).apply { setPos(Vector3(0f, 0.12f, 0f)); setScale(0.022f) })
        addChild(model(MeshResource.createBox(Vector3(0.03f, 0.012f, 0.03f), 0.002f), darkWood).apply { setPos(Vector3(0f, 0.142f, 0f)) })
    }

    private fun place(parent: Entity, name: String, size: Float, at: Vector3, yaw: Float = 0f, tool: String? = null, fallback: () -> Entity): Entity {
        val e = Entity().apply { setPos(at); transform().eulerAngles = EulerAngles(0f, yaw, 0f) }
        val m = models[name]?.clone(Entity.CloneOptions(true, true))
        if (m != null) { m.setScale(size); e.addChild(m) } else e.addChild(fallback())
        if (tool != null) hit(e, "tool:$tool", Vector3(size * 1.1f, size, size * 1.1f), Vector3(0f, size / 2, 0f))
        parent.addChild(e)
        return e
    }

    /** Registers [node] as a tool the hand moves, resting at [rest] in [parent] (a station, or the shop). */
    private fun grip(id: String, parent: Entity, node: Entity, rest: Vector3, box: Vector3, optional: Boolean = false, shopFrame: Boolean = false) {
        node.setPos(rest)
        hit(node, "grip:$id", box)
        parent.addChild(node)
        grips[id] = Grip(node, rest, shopFrame)
        if (optional) {
            this.optional += id
            node.enabled = false
        }
    }

    /**
     * Tool model [name] (prepared at 1 m along its longest side, or its height) at [size], turned by
     * [turn] and raised by [lift]; null when it is not in the APK (the code-built stand-in is used).
     */
    private fun tool(name: String, size: Float, turn: EulerAngles, lift: Float = 0f): Entity? {
        val m = models[name]?.clone(Entity.CloneOptions(true, true)) ?: return null
        m.setScale(size)
        m.transform().eulerAngles = turn
        m.setPos(Vector3(0f, lift, 0f))
        return Entity().apply { addChild(m) }
    }

    /** The writing brush: the model lies along z with its tip at +z, so it is turned to hang tip down. */
    private fun brushModel() = tool("brush", 0.06f, EulerAngles(90f, 0f, 0f)) ?: Entity().apply {
        addChild(model(MeshResource.createCylinder(0.05f, 0.0025f), bamboo).apply { setPos(Vector3(0f, 0.012f, 0f)) })
        addChild(model(MeshResource.createCone(0.012f, 0.0035f), pbr(color(0.1f, 0.1f, 0.12f), roughness = 0.8f)).apply { setPos(Vector3(0f, -0.019f, 0f)); transform().eulerAngles = EulerAngles(180f, 0f, 0f) })
    }

    /** A point in the front station's frame (or the shop's, for [shopFrame]) in the shop's frame. */
    private fun frontPos(p: Vector3, shopFrame: Boolean) =
        if (shopFrame) Vec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()) else Vec3(p.x.toDouble(), (BENCH_TOP + p.y).toDouble(), (BENCH_Z + STATION_R + p.z).toDouble())

    /** Where the hand has put grip [id], relative to its rest place (shop meters). */
    fun gripOff(id: String) = grips[id]?.off ?: Vector3(0f, 0f, 0f)

    fun moveGrip(id: String, off: Vector3) {
        grips[id]?.off = off
    }

    /** Where grip [id] is now, in the shop's frame. */
    fun gripAt(id: String): Vec3? = grips[id]?.let { g -> frontPos(Vector3(g.rest.x + g.off.x, g.rest.y + g.off.y, g.rest.z + g.off.z), g.shopFrame) }

    /**
     * The step's tools: the optional grips in [on] come out (the rest of them are put away), and
     * those in [glint] sparkle now and then, so the hand knows what to take.
     */
    fun tools(on: Set<String>, glint: Set<String>) {
        for (id in optional) grips[id]?.node?.enabled = id in on
        for ((id, g) in grips) if (id !in on || id !in optional) g.off = Vector3(0f, 0f, 0f)
        glinting = glint
        glintT = 0.3
    }

    /** Where a carried thing goes (shop frame), shown by a golden ring; null hides the ring. */
    fun dropAt(id: String?): Vec3? {
        val at = when (id) {
            "cloth" -> frontPos(Vector3(-0.045f, 0.07f, -0.035f), false)
            "stick" -> frontPos(Vector3(0.075f, 0.016f, 0.055f), false)
            "wrapcloth" -> Vec3(-0.22, (BASE_TOP + 0.03).toDouble(), 0.06)
            "gift" -> Vec3(-0.3, (BASE_TOP + 0.004).toDouble(), 0.07)
            else -> null
        }
        dropMark.enabled = at != null
        at?.let { dropMark.setPos(Vector3(it.x.toFloat(), it.y.toFloat(), it.z.toFloat())) }
        return at
    }

    /** The beads on the pot's four rings, at their radius (station frame). */
    private fun placeBeads() {
        val ys = floatArrayOf(0f, 0.3f, 0.65f, 0.95f)
        for (i in 0 until 4) grips["ring$i"]?.let { g ->
            val r = 0.006f + 0.006f * potRings[i]
            val p = Vector3(-0.045f + r + 0.005f, 0.048f + ys[i] * potH, 0.01f + 0.004f)
            grips["ring$i"] = Grip(g.node, p, false)
        }
    }

    /** What the carried stick, cloth or gift looks like (set when its step begins). */
    fun carryLook(id: String, made: Made, glossy: Boolean, dragon: Boolean, quality: Double, d: Dye?) {
        val g = grips[id] ?: return
        when (id) {
            "stick" -> { g.node.getChildren().filter { hits[it] == null }.forEach { it.destroy() }; g.node.addChild(inkStickModel(glossy, dragon, quality)) }
            "cloth", "wrapcloth" -> d?.let { clothTex = swapTexture(clothMat, clothBitmap(it), clothTex) } ?: made.cloth?.let { clothTex = swapTexture(clothMat, clothBitmap(it.dye), clothTex) }
            "gift" -> {
                g.node.getChildren().forEach { it.destroy() }
                val m = PhysicallyBasedMaterial.create().apply {
                    val dd = made.cloth?.dye
                    val n = 64
                    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
                    for (y in 0 until n) for (x in 0 until n) b.setPixel(x, y, indigoPixel(dd?.at((x + 0.5) / n, (y + 0.5) / n) ?: 0.6))
                    setBaseColorTexture(TextureResource.create(b)); setRoughness(0.9f)
                }
                g.node.addChild(model(sphere, m).apply { setPos(Vector3(0f, 0.028f, 0f)); transform().setScaleVector(Vector3(0.042f, 0.03f, 0.042f)) })
                g.node.addChild(model(sphere, m).apply { setPos(Vector3(0f, 0.062f, 0f)); setScale(0.012f) })
                g.node.addChild(model(MeshResource.createTorus(0.012f, 0.008f), red).apply { setPos(Vector3(0f, 0.05f, 0f)) })
            }
        }
    }

    /** Ink or blue left by the brush, sawdust by the trimmer, where the tool is. */
    fun rubMark(id: String) {
        val at = gripAt(id) ?: return
        when (id) {
            "trimmer" -> chips.emit(Vec3(at.x - 0.02, at.y - 0.008, at.z), 2, 0.08, 0.35, 0.0018f)
            "brush" -> drops.emit(Vec3(at.x, at.y - 0.02, at.z), 1, 0.01, 0.3, 0.0015f)
            "pen" -> smoke.emit(Vec3(at.x, at.y - 0.02, at.z), 1, 0.01, 0.3, 0.0015f)
        }
    }

    /** The bundle goes into the dye: drops when it comes out. */
    fun dipped() {
        drops.emit(frontPos(Vector3(0.065f, 0.06f, 0f), false), 10, 0.06, 0.5, 0.002f)
    }

    private fun hit(e: Entity, id: String, size: Vector3, center: Vector3 = Vector3(0f, 0f, 0f)) {
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(size).offsetByTranslation(center)), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
        hits[e] = id
    }

    fun hitOf(e: Entity?): String? {
        var cur = e
        while (cur != null) {
            hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    private fun quad(w: Float, h: Float): MeshResource {
        val x = w / 2; val y = h / 2
        val pos = listOf(Vector3(-x, -y, 0f), Vector3(x, -y, 0f), Vector3(x, y, 0f), Vector3(-x, y, 0f))
        // A Bitmap's first row lands at v = 1 here: v runs up the quad, or a sign shows upside
        // down (checked on the emulator with the snack stall's sign).
        val uv = listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f))
        return MeshResource.createWithMeshModel(
            MeshModel(pos, listOf(0, 1, 2, 0, 2, 3), List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, uv),
            BoundingBox(Vector3(-x, -y, -0.001f), Vector3(x, y, 0.001f)), "q${w}x$h",
        )
    }

    /** A flat sign: [text] in [ink] on [paper] (0 for a clear background), across or down. */
    private fun sign(text: String, w: Float, h: Float, ink: Int, paper: Int, vertical: Boolean = false): Entity {
        val px = 64
        val bw = if (vertical) px else px * text.length
        val bh = if (vertical) px * text.length else px
        val b = Bitmap.createBitmap(bw + 16, bh + 16, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        if (paper != 0) cv.drawColor(paper)
        val p = Paint().apply { isAntiAlias = true; color = ink; textSize = px * 0.86f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        text.forEachIndexed { i, ch ->
            val cx = if (vertical) 8f + px / 2f else 8f + px * (i + 0.5f)
            val cy = if (vertical) 8f + px * (i + 0.8f) else 8f + px * 0.8f
            cv.drawText(ch.toString(), cx, cy, p)
        }
        val m = unlit(color(1f, 1f, 1f), if (paper == 0) BlendingMode.MASKED else BlendingMode.OPAQUE).apply {
            setBaseColorTexture(TextureResource.create(b))
            if (paper == 0) setOpacityThreshold(0.3f)
        }
        return model(quad(w, h), m)
    }

    // ---- Which station faces the player ----

    fun face(c: Craft) {
        val k = ORDER.indexOf(c)
        if (k < 0) return
        front = c
        // The shorter way round.
        var goal = -90f * k
        while (goal - yaw > 180f) goal -= 360f
        while (goal - yaw < -180f) goal += 360f
        yawGoal = goal
    }

    /** Clears a station for a new order (its piece hidden until made again). */
    fun reset(c: Craft) {
        present(null)
        when (c) {
            Craft.PAPER -> { paperSheet.enabled = false; wetSheet.enabled = false; pulpColor(0.0) }
            Craft.PORCELAIN -> { potMesh?.destroy(); potMesh = null; pot.enabled = true; kiln(0.0) }
            Craft.DYE -> { cloth(null); bundleShape(null, emptySet(), 0) }
            Craft.INK -> inkStick.getChildren().forEach { it.destroy() }
            Craft.GIFT -> gift.enabled = false
        }
    }

    // ---- What is being made ----

    /** The pulp in the vat: raw green-brown at 0, cream when right, yellowed toward 1. */
    fun pulpColor(cook: Double) {
        val c = if (cook < 0.6) lerp(floatArrayOf(0.55f, 0.6f, 0.4f), floatArrayOf(0.92f, 0.9f, 0.8f), cook / 0.6) else lerp(floatArrayOf(0.92f, 0.9f, 0.8f), floatArrayOf(0.8f, 0.62f, 0.3f), (cook - 0.6) / 0.4)
        pulpMat.setBaseColor(color(c[0], c[1], c[2]))
        pulpMat.setEmissiveColor(color(c[0] * 0.4f, c[1] * 0.4f, c[2] * 0.4f))
    }

    private fun lerp(a: FloatArray, b: FloatArray, t: Double) = FloatArray(3) { (a[it] + (b[it] - a[it]) * t.coerceIn(0.0, 1.0)).toFloat() }

    /** The mould dips and comes out of the vat with a wet sheet on it ([thick] 0..1). */
    fun wetSheet(thick: Double) {
        wetSheet.enabled = true
        wetSheet.transform().setScaleVector(Vector3(1f, (0.6 + thick * 3).toFloat(), 1f))
    }

    /** The sheet of paper on the drying board: cream fibers; flaws (specks, clouds) as quality drops. */
    fun paper(quality: Double, thick: Double, young: Boolean) {
        paperSheet.enabled = true
        wetSheet.enabled = false
        paperTex = swapTexture(paperMat, paperBitmap(quality, thick, young), paperTex)
    }

    private fun paperBitmap(quality: Double, thick: Double, young: Boolean): Bitmap {
        val n = 128
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        var seed = 12345
        fun r(): Double { seed = seed * 1103515245 + 12345; return ((seed ushr 8) and 0xFFFF) / 65536.0 }
        val flaw = (1 - quality).coerceIn(0.0, 1.0)
        val tint = if (young) 0.0 else 0.08
        for (y in 0 until n) for (x in 0 until n) {
            var c = 0.95 - thick * 0.08 - tint + (r() - 0.5) * (0.04 + tint)
            if (r() < flaw * 0.05) c -= 0.3
            val cloud = sin(x * 0.11 + y * 0.07) * sin(y * 0.13 - x * 0.05) * flaw * 0.18
            c = (c + cloud).coerceIn(0.4, 1.0)
            val v = (c * 255).toInt()
            b.setPixel(x, y, (255 shl 24) or (v shl 16) or ((v * (0.96 - tint)).toInt() shl 8) or (v * (0.86 - tint * 2)).toInt())
        }
        return b
    }

    /**
     * The pot from its four ring levels (1..6) and its height; [pattern] painted (null not yet),
     * [celadon] glaze (null not yet), [fired] where the kiln was stopped (null not yet).
     */
    fun pot(rings: IntArray, height: Double, pattern: Int?, celadon: Boolean?, redClay: Boolean, fired: Double?, reveal: Float = 1f) {
        potMesh?.destroy()
        potMesh = model(lathe(rings, height.toFloat()), potMat).also { pot.addChild(it) }
        potTex = swapTexture(potMat, potBitmap(pattern, celadon, redClay, fired, gilded = false, reveal = reveal), potTex)
        potRings = rings.copyOf()
        potH = height.toFloat()
        placeBeads()
        potMat.setRoughness(if (fired == null) 0.85f else if (fired < 0.6) 0.7f else 0.12f)
    }

    private fun potBitmap(pattern: Int?, celadon: Boolean?, redClay: Boolean, fired: Double?, gilded: Boolean, reveal: Float = 1f): Bitmap {
        val n = 256
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(when {
            redClay -> 0xFFB06A4A.toInt()
            fired == null -> 0xFFE4DED2.toInt()
            celadon == true -> 0xFFA9CDB4.toInt()
            else -> 0xFFF6F5F0.toInt()
        })
        if (pattern != null && reveal > 0f) {
            // Painted so far: the brush goes round the pot, so the pattern shows up to [reveal] of the way.
            cv.save()
            cv.clipRect(0f, 0f, n * reveal, n.toFloat())
            val p = Paint().apply { isAntiAlias = true; color = if (fired == null) 0xFF4A4A4A.toInt() else 0xFF23408E.toInt(); strokeWidth = 4f; style = Paint.Style.STROKE }
            cv.drawLine(0f, n * 0.1f, n.toFloat(), n * 0.1f, p)
            cv.drawLine(0f, n * 0.9f, n.toFloat(), n * 0.9f, p)
            when (pattern) {
                0 -> for (k in 0 until 6) { // Lotus scrolls: a winding stem with round flowers.
                    val x0 = k * n / 6f
                    cv.drawCircle(x0 + 20, n * 0.5f, 13f, p)
                    cv.drawCircle(x0 + 20, n * 0.5f, 6f, p)
                    cv.drawArc(x0, n * 0.35f, x0 + n / 6f, n * 0.65f, 200f, 140f, false, p)
                }
                1 -> for (k in 0 until 4) { // Mountains over water.
                    val x0 = k * n / 4f
                    cv.drawPath(Path().apply { moveTo(x0, n * 0.68f); lineTo(x0 + n / 8f, n * 0.28f); lineTo(x0 + n / 4f, n * 0.68f) }, p)
                    cv.drawPath(Path().apply { moveTo(x0 + n / 10f, n * 0.68f); lineTo(x0 + n / 6f, n * 0.45f); lineTo(x0 + n / 4.5f, n * 0.68f) }, p)
                    for (l in 0 until 2) cv.drawLine(x0, n * (0.75f + l * 0.05f), x0 + n / 4f, n * (0.75f + l * 0.05f), p)
                }
                else -> for (k in 0 until 5) { // Clouds round a dragon's body.
                    val x0 = k * n / 5f
                    cv.drawArc(x0, n * 0.3f, x0 + n / 5f, n * 0.5f, 180f, 180f, false, p)
                    cv.drawArc(x0, n * 0.5f, x0 + n / 5f, n * 0.7f, 0f, 180f, false, p)
                    cv.drawCircle(x0 + n / 10f, n * 0.22f, 5f, p)
                }
            }
            cv.restore()
        }
        if (gilded) {
            val g = Paint().apply { isAntiAlias = true; color = 0xFFE2B04A.toInt(); strokeWidth = 7f; style = Paint.Style.STROKE }
            cv.drawLine(0f, n * 0.05f, n.toFloat(), n * 0.05f, g)
            cv.drawLine(0f, n * 0.95f, n.toFloat(), n * 0.95f, g)
        }
        // Fired too hot: a crack runs down it.
        if (fired != null && fired > 0.92) {
            val c = Paint().apply { isAntiAlias = true; color = 0xFF3A2A20.toInt(); strokeWidth = 2f; style = Paint.Style.STROKE }
            var x = n * 0.3f
            var y = 0f
            val path = Path().apply { moveTo(x, y) }
            while (y < n) { x += ((y * 7) % 13) - 6f; y += 14f; path.lineTo(x, y) }
            cv.drawPath(path, c)
        }
        return b
    }

    /** A pot's surface: a lathe through its rings (bottom, lower, upper, mouth), radius 12-42 mm by level. */
    private fun lathe(rings: IntArray, h: Float, grow: Float = 1f): MeshResource {
        val seg = 32
        fun radius(level: Float) = (0.006f + 0.006f * level) * grow
        // Rings at heights 0, 0.3, 0.65, 0.95 of h; between, smooth (Catmull-Rom over the four).
        val ys = floatArrayOf(0f, 0.3f, 0.65f, 0.95f)
        val rs = rings.map { radius(it.toFloat()) }
        val pts = ArrayList<Pair<Float, Float>>()
        pts += 0f to 0f
        val steps = 24
        for (i in 0..steps) {
            val t = i / steps.toFloat() * 3f
            val k = t.toInt().coerceAtMost(2)
            val u = t - k
            val p0 = rs[maxOf(k - 1, 0)]; val p1 = rs[k]; val p2 = rs[k + 1]; val p3 = rs[minOf(k + 2, 3)]
            val r = 0.5f * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)
            val y = ys[k] + (ys[k + 1] - ys[k]) * u
            pts += y * h to maxOf(r, 0.004f * grow)
        }
        // The lip, rolled outward, and a little way down inside.
        pts += h to rs[3] * 1.08f
        pts += h * 0.97f to rs[3] * 0.9f
        val pos = ArrayList<Vector3>(); val nor = ArrayList<Vector3>(); val uv = ArrayList<Vector2>(); val idx = ArrayList<Int>()
        for ((ri, pr) in pts.withIndex()) {
            // The normal from the profile's slope.
            val prev = pts[maxOf(ri - 1, 0)]; val next = pts[minOf(ri + 1, pts.size - 1)]
            val dy = next.first - prev.first; val dr = next.second - prev.second
            val len = sqrt(dy * dy + dr * dr).coerceAtLeast(1e-6f)
            val ny = -dr / len; val nr = dy / len
            for (s in 0..seg) {
                val a = s * 2 * Math.PI / seg
                pos += Vector3((pr.second * cos(a)).toFloat(), pr.first, (pr.second * sin(a)).toFloat())
                nor += Vector3((nr * cos(a)).toFloat(), ny, (nr * sin(a)).toFloat())
                uv += Vector2(s / seg.toFloat(), 1f - ri / (pts.size - 1f))
            }
        }
        for (ri in 0 until pts.size - 1) for (s in 0 until seg) {
            val a = ri * (seg + 1) + s
            val b2 = a + seg + 1
            idx += listOf(a, a + 1, b2, a + 1, b2 + 1, b2)
        }
        return MeshResource.createWithMeshModel(
            MeshModel(pos, idx, nor, List(pos.size) { Vector4(1f, 0f, 0f, 1f) }, uv),
            BoundingBox(Vector3(-0.05f, 0f, -0.05f), Vector3(0.05f, h + 0.01f, 0.05f)), "pot${rings.joinToString("")}$h$grow",
        )
    }

    /** The pot goes into the kiln (hidden) or comes out. */
    fun inKiln(on: Boolean) {
        pot.enabled = !on
    }

    /** The cloth on the rack: white with indigo where the dye took. Null hides it. */
    fun cloth(d: Dye?) {
        cloth.enabled = d != null
        if (d != null) clothTex = swapTexture(clothMat, clothBitmap(d), clothTex)
    }

    /** The order's pattern on the little board. */
    fun target(d: Dye) {
        targetTex = swapTexture(targetMat, clothBitmap(d), targetTex)
    }

    /**
     * The folded cloth before dyeing, lying at the front of the dye station: a long bundle (a cone
     * for the triangle fold) with its fold end to the left (a red tag), and a rope at each band tied
     * ([bands] 0..4 from the fold). [dips] darkens it. A null fold hides it.
     */
    fun bundleShape(fold: Fold?, bands: Set<Int>, dips: Int) {
        bundle.getChildren().forEach { hits.remove(it); it.destroy() }
        if (fold == null) return
        val len = 0.1f
        bundleMat.setBaseColor(color(0.95f - dips * 0.16f, 0.94f - dips * 0.14f, 0.9f - dips * 0.08f))
        val body = when (fold) {
            Fold.TRIANGLE -> model(MeshResource.createCone(len, 0.016f), bundleMat).apply { transform().eulerAngles = EulerAngles(0f, 0f, 90f) }
            Fold.QUARTER -> model(MeshResource.createBox(Vector3(len, 0.02f, 0.02f), 0.004f), bundleMat)
            Fold.HALF -> model(MeshResource.createBox(Vector3(len, 0.008f, 0.04f), 0.003f), bundleMat)
            Fold.FAN -> model(MeshResource.createBox(Vector3(len, 0.016f, 0.024f), 0.004f), bundleMat)
        }
        body.setPos(Vector3(0f, 0.012f, 0f))
        bundle.addChild(body)
        bundle.addChild(model(MeshResource.createBox(Vector3(0.004f, 0.012f, 0.012f), 0.001f), red).apply { setPos(Vector3(-len / 2 - 0.004f, 0.012f, 0f)) })
        for (b in bands) {
            val x = -len / 2 + len * (0.1f + 0.2f * b)
            val rr = if (fold == Fold.TRIANGLE) 0.016f * (0.1f + 0.2f * b) + 0.003f else 0.016f
            bundle.addChild(model(MeshResource.createTorus(rr + 0.002f, rr - 0.0005f), rope).apply {
                setPos(Vector3(x, 0.012f, 0f))
                transform().eulerAngles = EulerAngles(0f, 0f, 90f)
                hit(this, "band:$b", Vector3(0.012f, 0.04f, 0.04f))
            })
        }
    }

    /** The dye vat's mouth (shop frame): the bundle below it and over it is in the dye. */
    fun dyeVat() = frontPos(Vector3(0.065f, 0.045f, 0f), false)

    /** Where band [b] (0..4) is tied along the bundle (station frame, x). */
    fun bandX(b: Int) = BUNDLE_REST.x - 0.05f + 0.1f * (0.1f + 0.2f * b)

    /** The ink stick: black, glossy with more glue, with a gold dragon when asked. */
    fun ink(glossy: Boolean, dragon: Boolean, quality: Double) {
        inkStick.getChildren().forEach { it.destroy() }
        inkStick.addChild(inkStickModel(glossy, dragon, quality))
    }

    private fun inkStickModel(glossy: Boolean, dragon: Boolean, quality: Double): Entity {
        val e = Entity()
        val m = pbr(color(0.04f, 0.04f, 0.05f), metallic = if (quality > 0.85) 0.2f else 0f, roughness = if (glossy) 0.12f else 0.55f)
        e.addChild(model(MeshResource.createBox(Vector3(0.07f, 0.012f, 0.02f), 0.003f), m))
        if (dragon) e.addChild(sign("龍", 0.016f, 0.016f, 0xFFE2B04A.toInt(), 0).apply { setPos(Vector3(-0.015f, 0.0062f, 0f)); transform().eulerAngles = EulerAngles(-90f, 0f, 0f) })
        e.addChild(sign(if (dragon) "天工" else "墨", if (dragon) 0.024f else 0.012f, 0.012f, 0xFFE2B04A.toInt(), 0).apply { setPos(Vector3(0.015f, 0.0062f, 0f)); transform().eulerAngles = EulerAngles(-90f, 0f, 0f) })
        return e
    }

    /** The New Year gift before the guest: the couplet on the player's paper, the bowl in the player's cloth. */
    fun gift(lines: Pair<String, String>?, wrapped: Boolean, made: Made, bowl: Boolean = true) {
        gift.getChildren().forEach { it.destroy() }
        gift.setPos(Vector3(-0.22f, BASE_TOP, 0.06f))
        if (lines != null) {
            val ink = if (made.ink?.glossy == true) 0xFF050505.toInt() else 0xFF2A2A2A.toInt()
            val paper = if ((made.paper?.quality ?: 0.0) > 0.8) 0xFFC7261C.toInt() else 0xFFB0372A.toInt()
            for ((k, text) in listOf(lines.first, lines.second).withIndex()) {
                val x = if (k == 0) 0.06f else -0.06f
                val h = 0.026f * text.length
                gift.addChild(sign(text, 0.026f, h, ink, paper, vertical = true).apply { setPos(Vector3(x, h / 2 + 0.04f, -0.02f)) })
                gift.addChild(model(MeshResource.createCylinder(0.035f, 0.002f), darkWood).apply { setPos(Vector3(x, h + 0.042f, -0.021f)); transform().eulerAngles = EulerAngles(0f, 0f, 90f) })
                gift.addChild(model(MeshResource.createCylinder(0.04f + h, 0.0015f), darkWood).apply { setPos(Vector3(x, (0.04f + h) / 2, -0.03f)) })
            }
        }
        val p = made.pot
        if (!bowl) {
            // The wrapped bowl is in the player's hand (the 送礼 step).
        } else if (wrapped) {
            val m = PhysicallyBasedMaterial.create().apply {
                val d = made.cloth?.dye
                val n = 64
                val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
                for (y in 0 until n) for (x in 0 until n) b.setPixel(x, y, indigoPixel(d?.at((x + 0.5) / n, (y + 0.5) / n) ?: 0.6))
                setBaseColorTexture(TextureResource.create(b)); setRoughness(0.9f)
            }
            gift.addChild(model(sphere, m).apply { setPos(Vector3(0f, 0.028f, 0f)); transform().setScaleVector(Vector3(0.042f, 0.03f, 0.042f)) })
            gift.addChild(model(sphere, m).apply { setPos(Vector3(0f, 0.062f, 0f)); setScale(0.012f) })
            gift.addChild(model(MeshResource.createTorus(0.012f, 0.008f), red).apply { setPos(Vector3(0f, 0.05f, 0f)) })
        } else if (p != null) {
            gift.addChild(model(lathe(p.rings, p.height.toFloat()), PhysicallyBasedMaterial.create().apply {
                setBaseColorTexture(TextureResource.create(potBitmap(p.pattern, p.celadon, p.redClay, p.fired, false))); setRoughness(0.15f)
            }))
        }
        gift.enabled = true
    }

    /** The guest bows (thanks for an order, or for the gift). */
    fun bow() {
        bowT = 0.0
    }

    private fun swapTexture(m: PhysicallyBasedMaterial, b: Bitmap, old: TextureResource?): TextureResource? {
        val t = runCatching { TextureResource.create(b) }.getOrNull() ?: return old
        m.setBaseColorTexture(t)
        m.setEmissiveTexture(t)
        m.setEmissiveColor(color(0.35f, 0.35f, 0.35f))
        old?.close()
        return t
    }

    // ---- The gauge and the beat ----

    /** Shows the gauge for a step: a bar from [low] to [high] colors, the good zone at [target] ± [tol]. */
    fun meter(target: Double, tol: Double, low: Int, high: Int) {
        val n = 128
        val b = Bitmap.createBitmap(n, 4, Bitmap.Config.ARGB_8888)
        for (x in 0 until n) {
            val t = x / (n - 1f)
            fun mix(a: Int, z: Int) = (a * (1 - t) + z * t).toInt()
            val c = android.graphics.Color.rgb(
                mix(android.graphics.Color.red(low), android.graphics.Color.red(high)),
                mix(android.graphics.Color.green(low), android.graphics.Color.green(high)),
                mix(android.graphics.Color.blue(low), android.graphics.Color.blue(high)),
            )
            for (y in 0 until 4) b.setPixel(x, y, c)
        }
        val t = TextureResource.create(b)
        meterBarMat.setBaseColorTexture(t)
        meterTex?.close()
        meterTex = t
        zone.transform().setScaleVector(Vector3((2 * tol * METER_W).toFloat(), 1f, 1f))
        zone.setPos(Vector3(((target - 0.5) * METER_W).toFloat(), 0f, 0.001f))
        gauge(0.0)
        meter.enabled = true
    }

    fun gauge(x: Double) {
        needle.setPos(Vector3(((x - 0.5) * METER_W).toFloat(), 0f, 0.002f))
    }

    fun meterOff() {
        meter.enabled = false
    }

    /** Shows the beat ([height] 0..1, 1 at the ring). */
    fun beat(height: Double) {
        beatRoot.enabled = true
        ball.setPos(Vector3(0f, (BEAT_TOP - 0.12 * (1 - height)).toFloat(), 0f))
    }

    fun beatOff() {
        beatRoot.enabled = false
    }

    /** A strike at the front station: the pestle (paper) or the hammer (ink) comes down. */
    fun strike(good: Boolean) {
        strikeAt = front
        strikeT = 0.25
        if (good) {
            flash = 0.3
            stars.emit(Vec3(0.15, BEAT_TOP.toDouble(), 0.1), 6, 0.15, 0.4, 0.003f)
        }
        val at = if (front == Craft.INK) Vec3(0.03, BENCH_TOP + 0.03, BENCH_Z + STATION_R + 0.02) else Vec3(0.065, BENCH_TOP + 0.04, BENCH_Z + STATION_R - 0.025)
        chips.emit(at, 4, 0.12, 0.35, 0.0025f, Vec3(0.0, 0.05, 0.0))
    }

    fun chop() {
        chips.emit(Vec3(0.0, BENCH_TOP + 0.02, BENCH_Z + STATION_R + 0.055), 14, 0.25, 0.5, 0.003f, Vec3(0.0, 0.1, 0.0))
    }

    fun trim() {
        chips.emit(Vec3(-0.045, BENCH_TOP + 0.08, BENCH_Z + STATION_R + 0.01), 10, 0.15, 0.4, 0.002f)
    }

    fun sparkle(at: Vec3 = Vec3(0.0, BENCH_TOP + 0.1, (BENCH_Z + STATION_R).toDouble())) {
        stars.emit(at, 24, 0.2, 0.9, 0.003f, Vec3(0.0, 0.05, 0.0))
    }

    fun spin(on: Boolean) {
        wheelSpin = on
    }

    /** The kiln's fire, 0 cold .. 1 white hot. */
    fun kiln(heat: Double) {
        val h = heat.coerceIn(0.0, 1.0).toFloat()
        kilnGlowMat.setBaseColor(color(h, h * (0.3f + 0.6f * h), h * h * h * 0.6f, 1f))
        if (h > 0.2f && (clock * 10).toInt() % 3 == 0) sparks.emit(Vec3(0.0, BENCH_TOP + 0.07, BENCH_Z + STATION_R - 0.03), 1, 0.08, 0.6, 0.0025f, Vec3(0.0, 0.08, 0.0))
    }

    /** The vat steaming as it boils, [heat] 0..1 (the pulp's color follows). */
    fun boil(heat: Double) {
        pulpColor(heat)
        if (heat > 0.03 && (clock * 20).toInt() % 2 == 0) sparks.emit(frontPos(Vector3(-0.065f, 0.02f, 0.035f), false), 1 + (heat * 3).toInt(), 0.02, 0.35 + heat * 0.3, (0.002 + heat * 0.004).toFloat(), Vec3(0.0, 0.05 + heat * 0.05, -0.01))
        if ((clock * 12).toInt() % 2 == 0) steam.emit(Vec3(-0.065, BENCH_TOP + 0.04, BENCH_Z + STATION_R - 0.01), 1, 0.02, 1.2, (0.004 + 0.006 * heat).toFloat(), Vec3(0.0, 0.04 + 0.04 * heat, 0.0))
    }

    /** The lamp burning, its flame [size] 0..1: more and thicker smoke with a bigger flame. */
    fun soot(size: Double) {
        if ((clock * 10).toInt() % 2 == 0) smoke.emit(Vec3(-0.065, BENCH_TOP + 0.07, BENCH_Z + STATION_R - 0.02), 1 + (size * 2).toInt(), 0.01 + size * 0.02, 1.2, (0.003 + size * 0.006).toFloat(), Vec3(0.0, 0.05, 0.0))
        if ((clock * 30).toInt() % 3 == 0) sparks.emit(Vec3(-0.065, BENCH_TOP + 0.045, BENCH_Z + STATION_R - 0.02), 1, 0.01, 0.2, (0.002 + size * 0.004).toFloat())
    }

    /** The finished piece of [c] rises and turns before the player; null puts it back. */
    fun present(c: Craft?) {
        showing?.let { s -> piece(s)?.let { p -> shownBase[s]?.let { p.setPos(it) }; p.transform().eulerAngles = EulerAngles(0f, 0f, 0f) } }
        showing = c
        showT = 0.0
        if (c != null) piece(c)?.let { shownBase[c] = it.transform().position }
    }

    private fun piece(c: Craft): Entity? = when (c) {
        Craft.PAPER -> paperSheet
        Craft.PORCELAIN -> potHolder
        Craft.DYE -> clothRack
        Craft.INK -> inkStick
        Craft.GIFT -> null
    }

    // ---- Trophies ----

    /** The trophy stand ([level] 0-3), the cabinet of the player's works (silver), the gold plaque and vase. */
    fun trophies(level: Int, made: Made, vaseMade: Boolean) {
        trophyRoot.getChildren().forEach { it.destroy() }
        cabinetRoot.getChildren().forEach { it.destroy() }
        plaqueRoot.getChildren().forEach { it.destroy() }
        val sx = 0.33f
        val sz = 0.08f
        trophyRoot.addChild(model(MeshResource.createBox(Vector3(0.12f, 0.03f, 0.07f), 0.004f), lacquer).apply { setPos(Vector3(sx, BASE_TOP + 0.015f, sz)) })
        if (level == 0) {
            // A cloth over the stand, a question mark on it.
            trophyRoot.addChild(model(MeshResource.createBox(Vector3(0.11f, 0.05f, 0.065f), 0.012f), pbr(color(0.55f, 0.12f, 0.1f), roughness = 0.95f)).apply { setPos(Vector3(sx, BASE_TOP + 0.055f, sz)) })
            trophyRoot.addChild(sign("？", 0.03f, 0.03f, 0xFFE6C06A.toInt(), 0).apply { setPos(Vector3(sx, BASE_TOP + 0.06f, sz + 0.0335f)) })
        }
        if (level >= 1) {
            // Bronze: a wooden plaque 「巧手」 on a little bronze stand.
            trophyRoot.addChild(model(MeshResource.createBox(Vector3(0.05f, 0.026f, 0.006f), 0.002f), wood).apply { setPos(Vector3(sx - 0.03f, BASE_TOP + 0.055f, sz)) })
            trophyRoot.addChild(sign("巧手", 0.044f, 0.022f, 0xFF2A1A0E.toInt(), 0).apply { setPos(Vector3(sx - 0.03f, BASE_TOP + 0.055f, sz + 0.0035f)) })
            trophyRoot.addChild(model(MeshResource.createCylinder(0.012f, 0.012f), bronze).apply { setPos(Vector3(sx - 0.03f, BASE_TOP + 0.036f, sz)) })
        }
        if (level >= 2) {
            // Silver: a silver cup, and the cabinet of the player's best works.
            trophyRoot.addChild(model(MeshResource.createCone(0.03f, 0.012f), pbr(color(0.86f, 0.88f, 0.92f), metallic = 0.9f, roughness = 0.25f)).apply { setPos(Vector3(sx + 0.03f, BASE_TOP + 0.05f, sz)); transform().eulerAngles = EulerAngles(180f, 0f, 0f) })
            cabinet(made, vaseMade)
        }
        if (level >= 3) {
            // Gold: the 「天工」 plaque on the beam, a gold ball on the stand.
            val y = BASE_TOP + WALL_H - 0.005f
            plaqueRoot.addChild(model(MeshResource.createBox(Vector3(0.17f, 0.065f, 0.006f), 0.003f), gold).apply { setPos(Vector3(0f, y, WALL_Z + 0.044f)) })
            plaqueRoot.addChild(model(MeshResource.createBox(Vector3(0.16f, 0.055f, 0.008f), 0.003f), lacquer).apply { setPos(Vector3(0f, y, WALL_Z + 0.048f)) })
            plaqueRoot.addChild(sign("天工", 0.12f, 0.05f, 0xFFF0C75A.toInt(), 0).apply { setPos(Vector3(0f, y, WALL_Z + 0.0525f)) })
            trophyRoot.addChild(model(sphere, gold).apply { setPos(Vector3(sx, BASE_TOP + 0.085f, sz)); setScale(0.012f) })
        }
    }

    private fun cabinet(made: Made, vase: Boolean) {
        val cx = 0.31f
        val cz = -0.15f
        val y0 = BASE_TOP
        cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.16f, 0.2f, 0.09f), 0.004f), darkWood).apply { setPos(Vector3(cx, y0 + 0.1f, cz - 0.004f)) })
        cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.15f, 0.19f, 0.08f), 0.004f), pbr(color(0.18f, 0.12f, 0.08f), roughness = 0.8f)).apply { setPos(Vector3(cx, y0 + 0.1f, cz + 0.004f)) })
        for (y in listOf(0.07f, 0.135f)) cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.148f, 0.004f, 0.078f), 0.001f), wood).apply { setPos(Vector3(cx, y0 + y, cz + 0.004f)) })
        // An open curio shelf: a front frame, no glass (a transparent pane reads as a grey board).
        for (x in listOf(-0.074f, 0.074f)) cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.008f, 0.2f, 0.008f), 0.002f), darkWood).apply { setPos(Vector3(cx + x, y0 + 0.1f, cz + 0.044f)) })
        cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.156f, 0.012f, 0.008f), 0.002f), darkWood).apply { setPos(Vector3(cx, y0 + 0.194f, cz + 0.044f)) })
        // Bottom shelf: the pot (and the vase); middle: cloth and paper; top: ink.
        made.pot?.let { p ->
            cabinetRoot.addChild(model(lathe(p.rings, (p.height * 0.45).toFloat(), 0.45f), PhysicallyBasedMaterial.create().apply {
                setBaseColorTexture(TextureResource.create(potBitmap(p.pattern, p.celadon, p.redClay, p.fired, false))); setRoughness(0.15f)
            }).apply { setPos(Vector3(cx - 0.04f, y0 + 0.008f, cz + 0.01f)) })
        }
        if (vase) made.vase?.let { p ->
            cabinetRoot.addChild(model(lathe(p.rings, (p.height * 0.32).toFloat(), 0.32f), PhysicallyBasedMaterial.create().apply {
                setBaseColorTexture(TextureResource.create(potBitmap(p.pattern, p.celadon, p.redClay, p.fired, true))); setRoughness(0.1f)
            }).apply { setPos(Vector3(cx + 0.025f, y0 + 0.008f, cz + 0.01f)) })
        }
        made.cloth?.let { c ->
            cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.05f, 0.012f, 0.04f), 0.003f), PhysicallyBasedMaterial.create().apply {
                setBaseColorTexture(TextureResource.create(clothBitmap(c.dye, 64))); setRoughness(0.9f)
            }).apply { setPos(Vector3(cx - 0.035f, y0 + 0.078f, cz + 0.004f)) })
        }
        made.paper?.let { p ->
            val m = PhysicallyBasedMaterial.create().apply { setBaseColorTexture(TextureResource.create(paperBitmap(p.quality, p.thick, p.young))); setRoughness(0.95f) }
            for (k in 0 until 3) cabinetRoot.addChild(model(MeshResource.createBox(Vector3(0.045f, 0.002f, 0.035f), 0.0005f), m).apply { setPos(Vector3(cx + 0.035f, y0 + 0.074f + k * 0.0025f, cz + 0.004f)) })
        }
        made.ink?.let { i -> cabinetRoot.addChild(inkStickModel(i.glossy, i.dragon, i.quality).apply { setPos(Vector3(cx, y0 + 0.144f, cz + 0.004f)); setScale(0.8f) }) }
    }

    // ---- Motion ----

    fun frame(dt: Double) {
        clock += dt
        yaw += (yawGoal - yaw) * (1 - exp(-dt * 2.5)).toFloat()
        bench.transform().eulerAngles = EulerAngles(0f, yaw, 0f)
        if (strikeT > 0) strikeT -= dt
        // The tools the hand holds sit where the hand put them; a pinched one glints now and then.
        for ((id, g) in grips) g.node.setPos(Vector3(g.rest.x + g.off.x, g.rest.y + g.off.y, g.rest.z + g.off.z))
        glintT -= dt
        if (glintT <= 0) {
            glintT = 1.6
            for (id in glinting) grips[id]?.takeIf { it.node.enabled }?.let { g ->
                val f = frontPos(Vector3(g.rest.x + g.off.x, g.rest.y + g.off.y + 0.02f, g.rest.z + g.off.z), g.shopFrame)
                stars.emit(f, 5, 0.04, 0.6, 0.0022f, Vec3(0.0, 0.02, 0.0))
            }
        }
        if (wheelSpin) pot.transform().eulerAngles = EulerAngles(0f, (clock * 220).toFloat(), 0f)
        if (flash > 0) flash -= dt
        topRing.setScale(1f + (flash.coerceAtLeast(0.0) * 1.5).toFloat())
        showing?.let { s ->
            showT += dt
            val p = piece(s) ?: return@let
            val base = shownBase[s] ?: return@let
            val rise = (1 - exp(-showT * 2.5)).toFloat()
            p.setPos(Vector3(base.x, base.y + 0.07f * rise, base.z + 0.04f * rise))
            p.transform().eulerAngles = EulerAngles(if (s == Craft.INK) 30f * rise else 0f, (sin(showT * 0.8) * 35).toFloat(), 0f)
        }
        if (bowT >= 0) {
            bowT += dt
            guest.transform().eulerAngles = EulerAngles((18 * sin((bowT / 1.4).coerceAtMost(1.0) * Math.PI)).toFloat(), 0f, 0f)
            if (bowT >= 1.4) bowT = -1.0
        } else guest.transform().eulerAngles = EulerAngles(0f, 0f, (sin(clock * 0.9) * 1.5).toFloat())
        smoke.update(dt, 0.3)
        steam.update(dt, 0.3)
        sparks.update(dt)
        chips.update(dt)
        drops.update(dt)
        stars.update(dt)
    }

    /** The front station's middle in the root frame (for sounds). */
    val work get() = Vec3(0.0, BENCH_TOP + 0.06, (BENCH_Z + STATION_R).toDouble())

    companion object {
        val ORDER = listOf(Craft.PAPER, Craft.PORCELAIN, Craft.DYE, Craft.INK)
        const val SCALE = 0.75f
        const val BASE_W = 0.84f
        const val BASE_D = 0.52f
        const val BASE_TOP = 0.02f
        const val WALL_H = 0.3f
        const val WALL_Z = -0.02f - BASE_D / 2 + 0.01f
        const val BENCH_TOP = 0.075f
        const val BENCH_Z = -0.03f
        const val BENCH_R = 0.24f
        const val STATION_R = 0.15f
        const val METER_W = 0.22f
        const val BEAT_TOP = 0.24f
        private val BUNDLE_REST = Vector3(0f, 0.012f, 0.02f)
    }
}
