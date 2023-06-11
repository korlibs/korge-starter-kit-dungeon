import korlibs.datastructure.*
import korlibs.datastructure.iterators.*
import korlibs.event.*
import korlibs.image.atlas.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.image.tiles.*
import korlibs.korge.*
import korlibs.korge.input.*
import korlibs.korge.ldtk.view.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import korlibs.korge.view.property.*
import korlibs.korge.view.tiles.*
import korlibs.korge.virtualcontroller.*
import korlibs.math.geom.*
import korlibs.math.interpolation.*
import korlibs.memory.*
import korlibs.time.*

suspend fun main() = Korge(windowSize = Size(512, 512), backgroundColor = Colors["#2b2b2b"]) {
    val sceneContainer = sceneContainer()

    sceneContainer.changeTo({ MyScene() })
}

class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        //val ldtk = KR.gfx.dungeonTilesmapCalciumtrice.__file.readLDTKWorld()
        val atlas = MutableAtlasUnit()
        val wizardFemale = KR.gfx.wizardF.__file.readImageDataContainer(ASE.toProps(), atlas)
        val clericFemale = KR.gfx.clericF.__file.readImageDataContainer(ASE.toProps(), atlas)
        val minotaur = KR.gfx.minotaur.__file.readImageDataContainer(ASE.toProps(), atlas)
        //val ldtk = localCurrentDirVfs["../korge-free-gfx/Calciumtrice/tiles/dungeon_tilesmap_calciumtrice.ldtk"].readLDTKWorld()
        val ldtk = KR.gfx.dungeonTilesmapCalciumtrice.__file.readLDTKWorld()
        val level = ldtk.levelsByName["Level_0"]!!
        //println()
        //LDTKWorldView(ldtk, showCollisions = true).addTo(this)
        lateinit var levelView: LDTKLevelView
        lateinit var annotations: Graphics
        val camera = camera {
            levelView = LDTKLevelView(level).addTo(this)
            annotations = graphics {  }
        }
        camera.setTo(Rectangle(0f, 0f, levelView.width, levelView.height))
        println(levelView.layerViewsByName.keys)
        val grid = levelView.layerViewsByName["Kind"]!!.intGrid
        val entities = levelView.layerViewsByName["Entities"]!!.entities
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
                )
            ),
        )

        var playerState = ""
        virtualController.apply {
            down(GameButton.BUTTON_SOUTH) {
                val playerView = (player.view as ImageDataView2)
                //playerView.animation = "attack"
                playerState = "attack"
            }
            down(GameButton.BUTTON_NORTH) {
                val playerView = (player.view as ImageDataView2)
                //playerView.animation = "attack"
                playerState = "gesture"
            }
            //changed(GameButton.LX) {
            //    if (it.new.absoluteValue < 0.01f) {
            //        updated(right = it.new > 0f, up = true, scale = 1f)
            //    }
        }
        var lastDX = 0f
        val gridSize = Size(16, 16)

        fun IntIArray2.check(it: PointInt): Boolean {
            if (!this.inside(it.x, it.y)) return true
            val v = this.getAt(it.x, it.y)
            return v != 1 && v != 3
        }

        fun hitTest(pos: Point): Boolean {
            return grid.check((pos / gridSize).toInt())
        }

        fun doRay(pos: Point, dir: Vector2): RayResult? {
            return grid.raycast(Ray(pos, dir), gridSize, collides = { check(it) })
        }

        fun updateRay(pos: Point): Float {

            val ANGLES_COUNT = 64
            val angles = (0 until ANGLES_COUNT).map { Angle.FULL * (it.toFloat() / ANGLES_COUNT.toFloat()) }
            //val angles = listOf(Angle.ZERO, Angle.HALF)
            val results: ArrayList<RayResult> = arrayListOf()

            val anglesDeque = Deque(angles)

            // @TODO: Try to find gaps and add points in the corners between gaps.
            // @TODO: Or alternatively, bisect several times rays with a big gap !! <-- IMPLEMENTED

            while (anglesDeque.isNotEmpty()) {
                val angle = anglesDeque.removeFirst()
                val last = results.lastOrNull()
                val current = doRay(pos, Vector2.polar(angle)) ?: continue
                if (last != null && last.point.distanceTo(current.point) >= 16) {
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


            annotations.updateShape {
                fill(Colors.WHITE.withAd(0.25)) {
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
                for (result in results) {
                    fill(Colors.RED) {
                        circle(result.point, 2f)
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
            return results.map { it.point.distanceTo(pos) }.minOrNull() ?: 0f
            //println("result=$result")
        }

        addUpdater(60.hz) {
            val dx = virtualController.lx
            val dy = virtualController.ly

            val playerView = (player.view as ImageDataView2)
            if (dx != 0f) lastDX = dx
            if (dx == 0f && dy == 0f) {
                playerView.animation = if (playerState != "") playerState else "idle"
            } else {
                playerState = ""
                playerView.animation = "walk"
                playerView.scaleX = if (lastDX < 0) -1f else +1f
            }
            val newDir = Vector2(dx.toFloat(), dy.toFloat())
            val oldPos = player.pos
            val moveRay = doRay(oldPos, newDir)
            val finalDir = if (moveRay != null && moveRay.point.distanceTo(oldPos) < 6f) {
                val res = newDir.reflected(moveRay.normal)
                // @TODO: Improve sliding
                if (moveRay.normal.y != 0f) {
                    Vector2(res.x, 0f)
                } else {
                    Vector2(0f, res.y)
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
                println("TODO!!. Checl why is this happening. Operation could have lead to stuck: oldPos=$oldPos -> newPos=$newPos, finalDir=$finalDir, moveRay=$moveRay")
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
                            image.stackedIntMap = StackedIntArray2(IntArray2(1, 1, 0))
                            image.tileset = TileSet.EMPTY
                        } else {
                            image.stackedIntMap = StackedIntArray2(tilemap.data)
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

inline operator fun Vector2.rem(that: Vector2): Vector2 = Point(x % that.x, y % that.y)
inline operator fun Vector2.rem(that: Size): Vector2 = Point(x % that.width, y % that.height)
inline operator fun Vector2.rem(that: Float): Vector2 = Point(x % that, y % that)
