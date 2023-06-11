import korlibs.datastructure.*
import korlibs.math.geom.*
import kotlin.math.*

// @TODO: Move to KorGE and add companion to Ray, so this can be an static method
fun RayFromTwoPoints(start: Point, end: Point): Ray = Ray(start, Angle.between(start, end))

private fun sq(v: Float): Float = v * v
private fun signumNonZero(v: Float): Float = if (v < 0) -1f else +1f

data class RayResult(val ray: Ray, val point: Point, val normal: Vector2) : Extra by Extra.Mixin()

// https://www.youtube.com/watch?v=NbSee-XM7WA
fun Ray.firstCollisionInTileMap(
    cellSize: Size = Size(1f, 1f),
    maxTiles: Int = 10,
    collides: (tilePos: PointInt) -> Boolean
): RayResult? {
    val ray = this
    val rayStart = this.point / cellSize
    val rayDir = ray.direction.normalized
    val rayUnitStepSize = Vector2(
        sqrt(1f + sq(rayDir.y / rayDir.x)),
        sqrt(1f + sq(rayDir.x / rayDir.y)),
    )
    //println("vRayUnitStepSize=$vRayUnitStepSize")
    var mapCheckX = rayStart.x.toInt()
    var mapCheckY = rayStart.y.toInt()
    val stepX = signumNonZero(rayDir.x).toInt()
    val stepY = signumNonZero(rayDir.y).toInt()
    var rayLength1Dx = when {
        rayDir.x < 0 -> (rayStart.x - (mapCheckX)) * rayUnitStepSize.x
        else -> ((mapCheckX + 1) - rayStart.x) * rayUnitStepSize.x
    }
    var rayLength1Dy = when {
        rayDir.y < 0 -> (rayStart.y - (mapCheckY)) * rayUnitStepSize.y
        else -> ((mapCheckY + 1) - rayStart.y) * rayUnitStepSize.y
    }

    // Perform "Walk" until collision or range check
    var bTileFound = false
    val fMaxDistance = hypot(cellSize.width.toFloat(), cellSize.height.toFloat()) * maxTiles
    var fDistance = 0.0f
    var dx = 0
    while (fDistance < fMaxDistance) {
        // Walk along shortest path
        if (rayLength1Dx < rayLength1Dy) {
            mapCheckX += stepX
            fDistance = rayLength1Dx
            rayLength1Dx += rayUnitStepSize.x
            dx = 0
        } else {
            mapCheckY += stepY
            fDistance = rayLength1Dy
            rayLength1Dy += rayUnitStepSize.y
            dx = 1
        }

        // Test tile at new test point
        if (collides(PointInt(mapCheckX, mapCheckY))) {
            bTileFound = true
            break
        }
    }

    // Calculate intersection location
    if (bTileFound) {
        //println("vRayStart=$vRayStart: vRayDir=$vRayDir, fDistance=$fDistance")
        return RayResult(
            this,
            (rayStart + rayDir * fDistance) * cellSize,
            if (dx == 0) Vector2(-1f * rayDir.x.sign, 0f) else Vector2(0f, -1f * rayDir.y.sign)
        )
    }
    return null
}

fun IStackedIntArray2.raycast(
    ray: Ray,
    cellSize: Size = Size(1f, 1f),
    maxTiles: Int = 10,
    collides: IStackedIntArray2.(tilePos: PointInt) -> Boolean
): RayResult? {
    return ray.firstCollisionInTileMap(cellSize, maxTiles) { pos -> collides(this, pos) }
}

fun IntIArray2.raycast(
    ray: Ray,
    cellSize: Size = Size(1f, 1f),
    maxTiles: Int = 10,
    collides: IntIArray2.(tilePos: PointInt) -> Boolean
): RayResult? {
    return ray.firstCollisionInTileMap(cellSize, maxTiles) { pos -> collides(this, pos) }
}

// https://math.stackexchange.com/questions/13261/how-to-get-a-reflection-vector
//𝑟=𝑑−2(𝑑⋅𝑛)𝑛
operator fun Float.times(v: Vector3): Vector3 = v * this
operator fun Float.div(v: Vector3): Vector3 = v / this
operator fun Float.times(v: Vector2): Vector2 = v * this
operator fun Float.div(v: Vector2): Vector2 = v / this
fun Vector3.reflected(surfaceNormal: Vector3): Vector3 {
    val d = this
    val n = surfaceNormal
    return d - 2f * (d dot n) * n
}

//fun Vector2.reflected(surfaceNormal: Vector2): Vector2 {
//    val res = Vector3(x, y, 0f).reflected(Vector3(surfaceNormal.x, surfaceNormal.y, 0f))
//    return Vector2(res.x, res.y)
//}

fun Vector2.reflected(surfaceNormal: Vector2): Vector2 {
    val d = this
    val n = surfaceNormal
    return d - 2f * (d dot n) * n
}
