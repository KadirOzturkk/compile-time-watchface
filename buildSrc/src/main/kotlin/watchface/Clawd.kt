package watchface

internal class Cell(val col: Int, val row: Int, val cols: Int = 1, val rows: Int = 1)

internal enum class Arms(vararg cells: Cell) {
    NORMAL(Cell(1, 2, 2), Cell(15, 2, 2)),
    UP(Cell(1, 0, rows = 2), Cell(2, 1), Cell(16, 0, rows = 2), Cell(15, 1)),
    WAVE(Cell(1, 2, 2), Cell(16, 0, rows = 2), Cell(15, 1)),
    SIP(Cell(1, 2, 2), Cell(15, 1, 2)),
    ;

    val cells = cells.toList()
}

/** Eye top and height in pixels on the sprite's 12 px rows. */
internal enum class SpriteEyes(val top: Double, val height: Double) {
    OPEN(12.0, 12.0),
    UP(12.0, 6.0),
    CLOSED(19.2, 4.8),
}

/** One Clawd pose as a PartDraw: 18x5 grid of 6x12 cells (terminal quadrant blocks). */
internal fun sprite(
    x: Int,
    y: Int,
    arms: Arms = Arms.NORMAL,
    legs: List<Int> = listOf(4, 6, 11, 13),
    eyes: SpriteEyes = SpriteEyes.OPEN,
    look: Int = 0,
    alpha: String? = null,
    xExpr: String? = null,
    yExpr: String? = null,
    color: String = ACCENT,
): String {
    val cells = listOf(Cell(3, 0, 12, 4)) + arms.cells + legs.map { Cell(it, 4) }
    val shapes = cells.map { rect(it.col * 6, it.row * 12, it.cols * 6, it.rows * 12, color) } +
        listOf(5, 12).map { rect((it + look) * 6, eyes.top, 6, eyes.height, BLACK) }
    val transforms = listOfNotNull(
        alpha?.let { transform("alpha", it) },
        xExpr?.let { transform("x", it) },
        yExpr?.let { transform("y", it) },
    )
    return """<PartDraw x="$x" y="$y" width="108" height="60">""" +
        transforms.joinToString("") + shapes.joinToString("") + "</PartDraw>"
}

/** Laptop-scene eyes, which sit on the larger seated body. */
internal enum class LaptopEyes(val top: Int, val height: Int) {
    OPEN(336, 12),
    HALF(342, 6),
    CLOSED(343, 5),
}

internal fun laptopEyes(eyes: LaptopEyes, alpha: String? = null): String =
    partDraw(listOf(rect(216, eyes.top, 6, eyes.height, BLACK), rect(258, eyes.top, 6, eyes.height, BLACK)), alpha)

private val SPARK_POINTS = listOf(
    12.0 to 2.0, 15.75 to 5.5, 19.79 to 7.5, 19.0 to 12.0, 20.66 to 17.0, 16.0 to 18.93,
    12.0 to 21.5, 8.5 to 18.06, 3.34 to 17.0, 4.5 to 12.0, 4.21 to 7.5, 8.0 to 5.07,
)

/** The spark logo on the laptop lid, drawn as rays on a 24 px grid scaled to [size]. */
internal fun spark(x: Int, y: Int, size: Int, color: String): String {
    val k = size / 24.0
    val rays = SPARK_POINTS.map { (px, py) -> line(12 * k, 12 * k, px * k, py * k, color, twoDecimals(2.4 * k), cap = "ROUND") }
    return partDraw(rays, box = Box(x, y, size, size))
}

/** Pixel heart beating at the measured heart rate. */
internal fun heart(): String {
    val cells = listOf(Box(3, 0, 6, 3), Box(12, 0, 6, 3), Box(0, 3, 21, 6), Box(3, 9, 15, 3), Box(6, 12, 9, 3), Box(9, 15, 3, 3))
    val period = "(60000 / clamp($HR, 40, 200))"
    val phase = "((([SECOND_MILLISECOND] * 1000) % $period) / $period)"
    val scale = "($phase < 0.12 ? 1.25 : ($phase < 0.24 ? 1 : ($phase < 0.36 ? 1.12 : 1)))"
    return """<PartDraw x="196" y="283" width="21" height="18" pivotX="0.5" pivotY="0.5">""" +
        transform("scaleX", scale) + transform("scaleY", scale) +
        cells.joinToString("") { rect(it.x, it.y, it.width, it.height, ACCENT) } + "</PartDraw>"
}

internal fun ground(): String = partDraw(listOf(rect(140, 386, 200, 2, GROUND)))

internal fun mug(body: String, stripe: String): List<String> =
    listOf(rect(0, 0, 12, 14, body), rect(12, 3, 3, 8, body), rect(2, 3, 8, 2, stripe))

internal fun duck(body: String, beak: String): List<String> =
    listOf(rect(14, 16, 30, 16, body), rect(44, 16, 4, 4, body), rect(8, 2, 14, 14, body), rect(2, 8, 6, 4, beak), rect(12, 6, 3, 3, BLACK))
