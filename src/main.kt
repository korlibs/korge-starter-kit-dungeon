import korlibs.datastructure.*
import korlibs.datastructure.ds.*
import korlibs.datastructure.iterators.*
import korlibs.event.*
import korlibs.image.atlas.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.image.text.*
import korlibs.image.tiles.*
import korlibs.io.async.*
import korlibs.io.async.launch
import korlibs.korge.*
import korlibs.korge.animate.*
import korlibs.korge.input.*
import korlibs.korge.ldtk.*
import korlibs.korge.ldtk.view.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.ui.*
import korlibs.korge.view.*
import korlibs.korge.view.align.*
import korlibs.korge.view.animation.*
import korlibs.korge.view.filter.*
import korlibs.korge.view.mask.*
import korlibs.korge.view.property.*
import korlibs.korge.view.tiles.*
import korlibs.korge.virtualcontroller.*
import korlibs.math.*
import korlibs.math.geom.*
import korlibs.math.geom.ds.*
import korlibs.math.interpolation.*
import korlibs.math.raycasting.*
import korlibs.memory.*
import korlibs.time.*
import kotlin.math.*
import korlibs.render.*
import kotlinx.coroutines.*

suspend fun main() = Korge(windowSize = Size(1280, 720), backgroundColor = Colors["#2b2b2b"], displayMode = KorgeDisplayMode.TOP_LEFT_NO_CLIP) {
    val sceneContainer = sceneContainer()

    sceneContainer.changeTo { MyScene() }
}

class BvhWorld(val baseView: View) {
    val bvh = BVH2D<BvhEntity>()
    fun getAll(): List<BVH.Node<BvhEntity>> = bvh.search(bvh.envelope())
    fun add(view: View): BvhEntity {
        return BvhEntity(this, view).also { it.update() }
    }

    operator fun plusAssign(view: View) {
        add(view)
    }
    //val entities = arrayListOf<BvhEntity>()
}

class BvhEntity(val world: BvhWorld, val view: View) {
    //var lastRectangle: Rectangle? = null

    fun update() {
        //if (lastRectangle != null) {
        //    world.bvh.remove(lastRectangle, this)
        //}
        //val pos = world.baseView.getPositionRelativeTo(world.baseView)
        val rect = view.getBounds(world.baseView)
        val pos = rect.getAnchoredPoint(Anchor.BOTTOM_CENTER)
        //world.bvh.insertOrUpdate(rect, this)
        world.bvh.insertOrUpdate(Rectangle(pos - Point(8, 16), Size(16, 16)), this)
        //view.getLocalBounds()
    }
}

class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        //val ldtk = KR.gfx.dungeonTilesmapCalciumtrice.__file.readLDTKWorld()

        val loadingText = text("Loading level...", alignment = TextAlignment.MIDDLE_CENTER).centerOn(this)

        val atlas = MutableAtlasUnit()

        val font = KR.fonts.publicpixel.__file.readTtfFont().lazyBitmapSDF

        val wizardFemale = KR.gfx.wizardF.__file.readImageDataContainer(ASE.toProps(), atlas)

        val clericFemale = KR.gfx.clericF.__file.readImageDataContainer(ASE.toProps(), atlas)
        val minotaur = KR.gfx.minotaur.__file.readImageDataContainer(ASE.toProps(), atlas)
        //val ldtk = localCurrentDirVfs["../korge-free-gfx/Calciumtrice/tiles/dungeon_tilesmap_calciumtrice.ldtk"].readLDTKWorld()
        val ldtk = KR.gfx.dungeonTilesmapCalciumtrice.__file.readLDTKWorld()
        val level = ldtk.levelsByName["Level_0"]!!

        val tileEntities = ldtk.levelsByName["TILES"]!!.layersByName["Entities"]
        val tileEntitiesByName = tileEntities?.layer?.entityInstances?.associateBy { it.fieldInstancesByName["Name"].valueDyn.str } ?: emptyMap()
        val ClosedChest = tileEntitiesByName["ClosedChest"]
        val OpenedChest = tileEntitiesByName["OpenedChest"]
        println("tileEntitiesByName=$tileEntitiesByName")
        //println()
        //LDTKWorldView(ldtk, showCollisions = true).addTo(this)
        //var showAnnotations = true
        var showAnnotations = false
        lateinit var levelView: LDTKLevelView
        lateinit var annotations: Graphics
        lateinit var annotations2: Container
        lateinit var highlight: Graphics
        val camera = camera {
        //val camera = container {
            levelView = LDTKLevelView(level).addTo(this)//.xy(0, 8)
            highlight = graphics { }
                .filters(BlurFilter(2.0).also { it.filtering = false })
            annotations = graphics {  }
            //annotations2 = container {  }
            //setTo(Rectangle(0f, 0f, levelView.width, levelView.height))
            setTo(Rectangle(0, 0, 1280, 720) * 0.5)
        }

        //camera.mask(highlight.also { it.visible = false })
        //levelView.mask2(highlight, filtering = false)
        levelView.mask(highlight, filtering = false)
        highlight.visible = false

        uiButton("Reload") { onClick { launch { sceneContainer.changeTo { MyScene() } } } }

        val entitiesBvh = BvhWorld(camera)
        addUpdater {
            for (entity in entitiesBvh.getAll()) {
                entity.value?.update()
            }
        }

        val textInfo = text("", font = font).xy(120, 8)
        println(levelView.layerViewsByName.keys)
        val grid = levelView.layerViewsByName["Kind"]!!.intGridInt
        val entities = levelView.layerViewsByName["Entities"]!!.entities

        for (entity in entities) {
            entitiesBvh += entity
        }

        val player = entities.first {
            it.fieldsByName["Name"]?.valueString == "Cleric"
        }.apply {
            replaceView(ImageDataView2(clericFemale.default).also {
                it.smoothing = false
                it.animation = "idle"
                it.anchorPixel(Point(it.width * 0.5f, it.height))
                it.play()
            })
        }

        val mage = entities.first {
            it.fieldsByName["Name"]?.valueString == "Mage"
        }.apply {
            replaceView(ImageDataView2(wizardFemale.default).also {
                it.smoothing = false
                it.animation = "idle"
                it.anchor(Anchor.BOTTOM_CENTER)
                it.play()
            })
        }

        entities.first {
            it.fieldsByName["Name"]?.valueString == "Minotaur"
        }.replaceView(ImageDataView2(minotaur.default).also {
            it.smoothing = false
            it.animation = "idle"
            it.anchor(Anchor.BOTTOM_CENTER)
            it.play()
        })

        val virtualController = virtualController(
            sticks = listOf(
                VirtualStickConfig(
                    left = Key.LEFT,
                    right = Key.RIGHT,
                    up = Key.UP,
                    down = Key.DOWN,
                    lx = GameButton.LX,
                    ly = GameButton.LY,
                    anchor = Anchor.BOTTOM_LEFT,
                )
            ),
            buttons = listOf(
                VirtualButtonConfig(
                    key = Key.SPACE,
                    button = GameButton.BUTTON_SOUTH,
                    anchor = Anchor.BOTTOM_RIGHT,
                ),
                VirtualButtonConfig(
                    key = Key.RETURN,
                    button = GameButton.BUTTON_NORTH,
                    anchor = Anchor.BOTTOM_RIGHT,
                    offset = Point(0f, -100f)
                ),
                VirtualButtonConfig(
                    key = Key.Z,
                    button = GameButton.BUTTON_WEST,
                    anchor = Anchor.BOTTOM_RIGHT,
                    offset = Point(0f, -200f)
                )
            ),
        )

        var lastInteractiveView: View? = null
        var playerDirection = Vector2D(1.0, 0.0)
        val gridSize = Size(16, 16)

        var playerState = ""


        fun IntIArray2.check(it: PointInt): Boolean {
            if (!this.inside(it.x, it.y)) return true
            val v = this.getAt(it.x, it.y)
            return v != 1 && v != 3
        }

        fun TileMapData.check(it: PointInt): Boolean {
            if (!this.inside(it.x, it.y)) return true
            val v = this.get(it.x, it.y).tile
            return v != 1 && v != 3 // 1=Ground, 3=Stone, ... 2=Wall, 4=Outside
        }

        fun hitTest(pos: Point): Boolean {
            for (result in entitiesBvh.bvh.search(Rectangle.fromBounds(pos - Point(1, 1), pos + Point(1, 1)))) {
                val view = result.value?.view ?: continue
                if (view == player) continue
                val entityView = view as? LDTKEntityView
                val doBlock = entityView?.fieldsByName?.get("Collides")
                if (doBlock?.valueString == "false") continue

                return true
            }
            return grid.check((pos / gridSize).toInt())
        }

        fun doRay(pos: Point, dir: Vector2D, property: String): RayResult? {
            // @TODO: FIXME! This is required because BVH produces wrong intersect distance for completely vertical rays. We should fix that.
            val dir = Vector2D(
                if (dir.x.isAlmostEquals(0.0)) .00001 else dir.x,
                if (dir.y.isAlmostEquals(0.0)) .00001 else dir.y,
            )
            val ray = Ray(pos, dir)
            val outResults = arrayListOf<RayResult?>()
            val blockedResults = arrayListOf<RayResult>()
            //println("grid=$grid")
            outResults += grid.raycast(ray, gridSize, collides = { check(it) })?.also { it.view = null }
            for (result in entitiesBvh.bvh.intersect(ray)) {
                val view = result.obj.value?.view
                if (view == player) continue

                // NARROW result. And try for example to use circles, capsules or smaller rectangles covering only the base of the object
                val rect = result.obj.d.toRectangle()
                val intersectionPos = ray.point + ray.direction.normalized * result.intersect
                //if (intersectionPos.distanceTo(pos) < 4f) {
                //    println("pos=$pos, dir=$dir, intersectionPos=$intersectionPos, ray.direction.normalized=${ray.direction.normalized}, result.intersect=${result.intersect}, result.obj.value?.view=${result.obj.value?.view} : result=$result")
                //}
                val normalX = if (intersectionPos.x <= rect.left + 0.5f) -1f else if (intersectionPos.x >= rect.right - .5f) +1f else 0f
                val normalY = if (intersectionPos.y <= rect.top + 0.5f) -1f else if (intersectionPos.y >= rect.bottom - .5f) +1f else 0f
                val rayResult = RayResult(ray, intersectionPos, Vector2D(normalX, normalY))?.also { it.view = view }

                val entityView = view as? LDTKEntityView
                val doBlock = entityView?.fieldsByName?.get(property)
                if (rayResult != null && doBlock?.valueString == "false") {
                    blockedResults += rayResult
                    continue
                }

                outResults += rayResult
            }
            //println("results=$results")
            return outResults.filterNotNull().minByOrNull { it.point.distanceTo(pos) }?.also { res ->
                val dist = res.point.distanceTo(pos)
                res.blockedResults = blockedResults.filter { it!!.point.distanceTo(pos) < dist }
            }
        }

        fun getInteractiveView(): View? {
            val results = doRay(player.pos, playerDirection, "Collides") ?: return null
            if (results.point.distanceTo(player.pos) >= 16f) return null
            return results.view
        }

        fun updateRay(pos: Point): Double {
            val ANGLES_COUNT = 64
            val angles = (0 until ANGLES_COUNT).map { Angle.FULL * (it.toDouble() / ANGLES_COUNT.toDouble()) }
            //val angles = listOf(Angle.ZERO, Angle.HALF)
            val results: ArrayList<RayResult> = arrayListOf()
            val results2: ArrayList<RayResult> = arrayListOf()

            val anglesDeque = Deque(angles)

            // @TODO: Try to find gaps and add points in the corners between gaps.
            // @TODO: Or alternatively, bisect several times rays with a big gap !! <-- IMPLEMENTED

            while (anglesDeque.isNotEmpty()) {
                val angle = anglesDeque.removeFirst()
                val last = results.lastOrNull()
                val current = doRay(pos, Vector2D.polar(angle), "Occludes") ?: continue
                current?.blockedResults?.let {
                    results2 += it.filterNotNull()
                }
                //println(doBlock?.valueString)
                if (last != null && (last.point.distanceTo(current.point) >= 16 || last.normal != current.normal)) {
                    val lastAngle = last.ray.direction.angle
                    val currentAngle = current.ray.direction.angle
                    if ((lastAngle - currentAngle).absoluteValue >= 0.25.degrees) {
                        anglesDeque.addFirst(angle)
                        anglesDeque.addFirst(
                            Angle.fromRatio(0.5.toRatio().interpolate(lastAngle.ratio, currentAngle.ratio))
                        )
                        continue
                    }
                }
                results += current
            }

            entities.fastForEach { entity ->
                if ("hide_on_fog" in entity.entity.tags) {
                    entity.simpleAnimator.cancel().sequence {
                        tween(entity::alpha[if (entity != player) .1f else 1f], time = 0.25.seconds)
                    }
                }
            }

            for (result in (results + results2)) {
                val view = result.view ?: continue
                if (view.alpha != 1.0) {
                    view.simpleAnimator.cancel().sequence {
                        tween(view::alpha[1f], time = 0.25.seconds)
                    }
                }
            }

            textInfo.text = "Rays: ${results.size}"
            highlight.updateShape {
                fill(Colors["#FFFFFF55"]) {
                    rect(0, 0, 600, 500)
                }
                fill(Colors.WHITE) {
                    var first = true
                    for (result in results) {
                        if (first) {
                            first = false
                            moveTo(result.point)
                        } else {
                            lineTo(result.point)
                        }
                    }
                    close()
                }
                fill(Colors.WHITE) {
                    for (result in results) {
                        val view = result.view ?: continue
                        rect(view.getBounds(highlight).expanded(MarginInt(-2)))
                    }
                }
            }
            annotations.updateShape {
                if (showAnnotations) {
                    for (result in results) {
                        fill(Colors.RED) {
                            circle(result.point, 2.0)
                        }
                        //stroke(Colors.BLUE.withAd(0.1)) {
                        //    line(pos, result.point)
                        //}
                    }
                    for (result in results) {
                        stroke(Colors.GREEN) {
                            line(result.point, result.point + result.normal * 4f)
                        }

                        val newVec = (result.point - pos).reflected(result.normal).normalized
                        stroke(Colors.YELLOW) {
                            line(result.point, result.point + newVec * 4f)
                        }
                    }
                }

                if (showAnnotations) {
                    //annotations2.removeChildren()
                    //for (n in 0 until 16) {
                    //    annotations2.solidRect(Size(256, 1), Colors.SADDLEBROWN).xy(0, n * 16)
                    //}
                    //stroke(Colors.SADDLEBROWN) {
                    //    for (n in 0 until 16) {
                    //        line(Point(0, n * 16), Point(256, n * 16))
                    //    }
                    //}

                    for (entity in entitiesBvh.getAll()) {
                        //println("entity: ${entity.d.toRectangle()}")
                        stroke(Colors.PURPLE.withAd(0.1)) {
                            rect(entity.d.toRectangle())
                        }
                    }
                }
            }
            return results.map { it.point.distanceTo(pos) }.minOrNull() ?: 0.0
            //println("result=$result")
        }

        addUpdater(60.hz) {
            val dx = virtualController.lx.toDouble()
            val dy = virtualController.ly.toDouble()

            val playerView = (player.view as ImageDataView2)
            if (!dx.isAlmostZero() || !dy.isAlmostZero()) {
                playerDirection = Vector2D(dx.sign, dy.sign)
                //println("playerDirection=$playerDirection")
            }
            if (dx == 0.0 && dy == 0.0) {
                playerView.animation = if (playerState != "") playerState else "idle"
            } else {
                playerState = ""
                playerView.animation = "walk"
                playerView.scaleX = if (playerDirection.x < 0) -1.0 else +1.0
            }
            val speed = 1.5
            val newDir = Vector2D(dx * speed, dy * speed)
            val oldPos = player.pos
            val moveRay = doRay(oldPos, newDir, "Collides")
            val finalDir = if (moveRay != null && moveRay.point.distanceTo(oldPos) < 6f) {
                val res = newDir.reflected(moveRay.normal)
                // @TODO: Improve sliding
                if (moveRay.normal.y != 0.0) {
                    Vector2D(res.x, 0f)
                } else {
                    Vector2D(0f, res.y)
                }
            } else {
                newDir
            }
            val newPos = oldPos + finalDir
            if (!hitTest(newPos) || hitTest(oldPos)) {
                player.pos = newPos
                player.zIndex = player.y
                updateRay(oldPos)
            } else {
                println("TODO!!. Check why is this happening. Operation could have lead to stuck: oldPos=$oldPos -> newPos=$newPos, finalDir=$finalDir, moveRay=$moveRay")
            }

            lastInteractiveView?.colorMul = Colors.WHITE
            //lastInteractiveView?.filters()
            val interactiveView = getInteractiveView()
            if (interactiveView != null) {
                interactiveView.colorMul = Colors["#ffbec3"]
                //interactiveView.filters(DropshadowFilter(0f, 0f, Colors.RED, blurRadius = 1f))
                lastInteractiveView = interactiveView
            } else {
            }
            //val lx = virtualController.lx
            //when {
            //    lx < 0f -> {
            //        updated(right = false, up = false, scale = lx.absoluteValue)
            //    }
            //    lx > 0f -> {
            //        updated(right = true, up = false, scale = lx.absoluteValue)
            //    }
            //}

        }

        keys {
            down(Key.R) {
                println(player.pos)
            }
        }

        virtualController.apply {
            fun onAnyButton() {
                val view = getInteractiveView() ?: return
                val entityView = view as? LDTKEntityView ?: return
                val doBlock = entityView?.fieldsByName?.get("Items") ?: return
                val items = doBlock.valueDyn.list.map { it.str }

                val tile = OpenedChest!!.tile!!
                entityView.replaceView(
                    Image(entityView!!.tileset!!.unextrudedTileSet!!.base.sliceWithSize(tile.x, tile.y, tile.w, tile.h)).also {
                        it.smoothing = false
                        it.anchor(entityView.anchor)
                    }
                )

                launchImmediately {
                    gameWindow.alert("Found $items")
                }

                //if (rayResult != null && doBlock?.valueString == "false") {
                println("INTERACTED WITH: " + view + " :: ${doBlock.value!!::class}, ${doBlock.value}")
            }

            down(GameButton.BUTTON_WEST) {
                showAnnotations = !showAnnotations
            }
            down(GameButton.BUTTON_SOUTH) {
                val playerView = (player.view as ImageDataView2)
                //playerView.animation = "attack"
                playerState = "attack"
                onAnyButton()
            }
            down(GameButton.BUTTON_NORTH) {
                val playerView = (player.view as ImageDataView2)
                //playerView.animation = "attack"
                playerState = "gesture"
                onAnyButton()
            }
            //changed(GameButton.LX) {
            //    if (it.new.absoluteValue < 0.01f) {
            //        updated(right = it.new > 0f, up = true, scale = 1f)
            //    }
        }

        loadingText.removeFromParent()
    }

    /*
        addArrowKeysController() { dx, dy, lastDX, lastDY ->
            val playerView = (player.view as ImageDataView2)
            if (dx == 0 && dy == 0) {
                playerView.animation = "idle"
            } else {
                playerView.animation = "walk"
                playerView.scaleX = if (lastDX < 0) -1f else +1f
            }
            player.x += dx.toFloat()
            player.y += dy.toFloat()
            player.zIndex = player.y
        }
        addArrowKeysController(
            left = Key.A,
            right = Key.D,
            up = Key.W,
            down = Key.S,
        ) { dx, dy, lastDX, lastDY ->
            val playerView = (mage.view as ImageDataView2)
            if (dx == 0 && dy == 0) {
                playerView.animation = "idle"
            } else {
                playerView.animation = "walk"
                playerView.scaleX = if (lastDX < 0) -1f else +1f
            }
            mage.x += dx.toFloat()
            mage.y += dy.toFloat()
            mage.zIndex = mage.y
        }

     */
}

fun View.addArrowKeysController(
    left: Key = Key.LEFT,
    right: Key = Key.RIGHT,
    up: Key = Key.UP,
    down: Key = Key.DOWN,
    block: (dx: Int, dy: Int, lastDX: Int, lastDY: Int) -> Unit
) {
    keys {
        var lastDX = 0
        var lastDY = 0
        addUpdaterWithViews { views, dt ->
            val dx = if (views.input.keys[left]) -1 else if (views.input.keys[right]) +1 else 0
            val dy = if (views.input.keys[up]) -1 else if (views.input.keys[down]) +1 else 0
            if (dx != 0) lastDX = dx
            if (dy != 0) lastDY = dy
            block(dx, dy, lastDX, lastDY)
        }
    }
}

inline fun Container.imageAnimationView2(
    animation: ImageAnimation? = null,
    direction: ImageAnimation.Direction? = null,
    block: @ViewDslMarker ImageAnimationView2<Image>.() -> Unit = {}
): ImageAnimationView2<Image> =
    ImageAnimationView2(animation, direction) { Image(Bitmaps.transparent) }.addTo(this, block)

fun ImageAnimationView2(
    animation: ImageAnimation? = null,
    direction: ImageAnimation.Direction? = null,
): ImageAnimationView2<Image> = ImageAnimationView2(animation, direction) { Image(Bitmaps.transparent) }

open class ImageDataView2(
    data: ImageData? = null,
    animation: String? = null,
    playing: Boolean = false,
    smoothing: Boolean = true,
) : Container(), PixelAnchorable, Anchorable {
    // Here we can create repeated in korge-parallax if required
    protected open fun createAnimationView(): ImageAnimationView2<out SmoothedBmpSlice> {
        return imageAnimationView2()
    }

    open val animationView: ImageAnimationView2<out SmoothedBmpSlice> = createAnimationView()

    override var anchorPixel: Point by animationView::anchorPixel
    override var anchor: Anchor by animationView::anchor

    fun getLayer(name: String): View? {
        return animationView.getLayer(name)
    }

    var smoothing: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                animationView.smoothing = value
            }
        }

    var data: ImageData? = data
        set(value) {
            if (field !== value) {
                field = value
                updatedDataAnimation()
            }
        }

    var animation: String? = animation
        set(value) {
            if (field !== value) {
                field = value
                updatedDataAnimation()
            }
        }

    val animationNames: Set<String> get() = data?.animationsByName?.keys ?: emptySet()

    init {
        updatedDataAnimation()
        if (playing) play() else stop()
        this.smoothing = smoothing
    }

    fun play() {
        animationView.play()
    }

    fun stop() {
        animationView.stop()
    }

    fun rewind() {
        animationView.rewind()
    }

    private fun updatedDataAnimation() {
        animationView.animation =
            if (animation != null) data?.animationsByName?.get(animation) else data?.defaultAnimation
    }
}

interface PixelAnchorable {
    @ViewProperty(name = "anchorPixel")
    var anchorPixel: Point
}

fun <T : PixelAnchorable> T.anchorPixel(point: Point): T {
    this.anchorPixel = point
    return this
}

open class ImageAnimationView2<T : SmoothedBmpSlice>(
    animation: ImageAnimation? = null,
    direction: ImageAnimation.Direction? = null,
    val createImage: () -> T
) : Container(), Playable, PixelAnchorable, Anchorable {
    private var nframes: Int = 1

    fun createTilemap(): TileMap = TileMap()

    var onPlayFinished: (() -> Unit)? = null
    var onDestroyLayer: ((T) -> Unit)? = null
    var onDestroyTilemapLayer: ((TileMap) -> Unit)? = null

    var animation: ImageAnimation? = animation
        set(value) {
            if (field !== value) {
                field = value
                didSetAnimation()
            }
        }
    var direction: ImageAnimation.Direction? = direction
        set(value) {
            if (field !== value) {
                field = value
                setFirstFrame()
            }
        }

    private val computedDirection: ImageAnimation.Direction
        get() = direction ?: animation?.direction ?: ImageAnimation.Direction.FORWARD
    private val anchorContainer = container()
    private val layers = fastArrayListOf<View>()
    private val layersByName = FastStringMap<View>()
    private var nextFrameIn = 0.milliseconds
    private var currentFrameIndex = 0
    private var nextFrameIndex = 0
    private var dir = +1

    override var anchorPixel: Point = Point.ZERO
        set(value) {
            field = value
            anchorContainer.pos = -value
        }
    override var anchor: Anchor
        get() = Anchor(anchorPixel.x / width, anchorPixel.y / height)
        set(value) {
            anchorPixel = Point(value.sx * width, value.sy * height)
        }

    fun getLayer(name: String): View? = layersByName[name]

    var smoothing: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                layers.fastForEach {
                    if (it is SmoothedBmpSlice) it.smoothing = value
                }
            }
        }

    private fun setFrame(frameIndex: Int) {
        currentFrameIndex = frameIndex
        val frame =
            if (animation?.frames?.isNotEmpty() == true) animation?.frames?.getCyclicOrNull(frameIndex) else null
        if (frame != null) {
            frame.layerData.fastForEach {
                val image = layers[it.layer.index]
                when (it.layer.type) {
                    ImageLayer.Type.NORMAL -> {
                        (image as SmoothedBmpSlice).bitmap = it.slice
                    }

                    else -> {
                        image as TileMap
                        val tilemap = it.tilemap
                        if (tilemap == null) {

                            image.map = TileMapData(1, 1)
                            image.tileset = TileSet.EMPTY
                        } else {
                            image.map = TileMapData(tilemap.data)
                            image.tileset = tilemap.tileSet ?: TileSet.EMPTY
                        }
                    }
                }
                image.xy(it.targetX, it.targetY)
            }
            nextFrameIn = frame.duration
            dir = when (computedDirection) {
                ImageAnimation.Direction.FORWARD -> +1
                ImageAnimation.Direction.REVERSE -> -1
                ImageAnimation.Direction.PING_PONG -> if (frameIndex + dir !in 0 until nframes) -dir else dir
                ImageAnimation.Direction.ONCE_FORWARD -> if (frameIndex < nframes - 1) +1 else 0
                ImageAnimation.Direction.ONCE_REVERSE -> if (frameIndex == 0) 0 else -1
            }
            nextFrameIndex = (frameIndex + dir) umod nframes
        } else {
            layers.fastForEach {
                if (it is SmoothedBmpSlice) {
                    it.bitmap = Bitmaps.transparent
                }
            }
        }
    }

    private fun setFirstFrame() {
        if (computedDirection == ImageAnimation.Direction.REVERSE || computedDirection == ImageAnimation.Direction.ONCE_REVERSE) {
            setFrame(nframes - 1)
        } else {
            setFrame(0)
        }
    }

    private fun didSetAnimation() {
        nframes = animation?.frames?.size ?: 1
        // Before clearing layers let parent possibly recycle layer objects (e.g. return to pool, etc.)
        for (layer in layers) {
            if (layer is TileMap) {
                onDestroyTilemapLayer?.invoke(layer)
            } else {
                onDestroyLayer?.invoke(layer as T)
            }
        }
        layers.clear()
        anchorContainer.removeChildren()
        dir = +1
        val animation = this.animation
        if (animation != null) {
            for (layer in animation.layers) {
                val image: View = when (layer.type) {
                    ImageLayer.Type.NORMAL -> {
                        createImage().also { it.smoothing = smoothing } as View
                    }

                    ImageLayer.Type.TILEMAP -> createTilemap()
                    ImageLayer.Type.GROUP -> TODO()
                }
                layers.add(image)
                layersByName[layer.name ?: "default"] = image
                anchorContainer.addChild(image as View)
            }
        }
        setFirstFrame()
    }

    private var running = true
    override fun play() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun rewind() {
        setFirstFrame()
    }

    init {
        didSetAnimation()
        addUpdater {
            //println("running=$running, nextFrameIn=$nextFrameIn, nextFrameIndex=$nextFrameIndex")
            if (running) {
                nextFrameIn -= it
                if (nextFrameIn <= 0.0.milliseconds) {
                    setFrame(nextFrameIndex)
                    // Check if animation should be played only once
                    if (dir == 0) {
                        running = false
                        onPlayFinished?.invoke()
                    }
                }
            }
        }
    }
}

private var RayResult.view: View? by Extra.Property { null }
private var RayResult.blockedResults: List<RayResult>? by Extra.Property { null }

fun TileMapData.raycast(
    ray: Ray,
    cellSize: Size = Size(1, 1),
    maxTiles: Int = 10,
    collides: TileMapData.(tilePos: PointInt) -> Boolean
): RayResult? {
    return ray.firstCollisionInTileMap(cellSize, maxTiles) { pos -> collides(this, pos) }
}
