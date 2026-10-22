package watchface

import java.math.BigDecimal
import java.math.RoundingMode

internal data class Box(val x: Int, val y: Int, val width: Int, val height: Int)

internal val FULL_FACE = Box(0, 0, 480, 480)

internal fun attr(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// Rounds the exact binary value with ties to even, so the digits never depend on the JDK's formatter.
internal fun twoDecimals(value: Double): String = BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toPlainString()

private fun number(value: Number): String =
    if (value is Double && value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Nested ternary selecting `items[index]`; items are string literals that need no further quoting. */
internal fun pick(index: String, items: List<String>): String =
    items.dropLast(1).foldRightIndexed("\"${items.last()}\"") { i, item, rest -> "(($index) == $i ? \"$item\" : $rest)" }

internal fun rect(x: Number, y: Number, width: Number, height: Number, color: String): String =
    """<Rectangle x="${number(x)}" y="${number(y)}" width="${number(width)}" height="${number(height)}">""" +
        """<Fill color="$color"/></Rectangle>"""

internal fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: String, thickness: String, cap: String? = null): String {
    val capAttr = if (cap != null) """ cap="$cap"""" else ""
    return """<Line startX="${twoDecimals(x1)}" startY="${twoDecimals(y1)}" endX="${twoDecimals(x2)}" endY="${twoDecimals(y2)}">""" +
        """<Stroke color="$color" thickness="$thickness"$capAttr/></Line>"""
}

internal fun transform(target: String, value: String): String = """<Transform target="$target" value="${attr(value)}"/>"""

internal fun partDraw(
    shapes: List<String>,
    alpha: String? = null,
    name: String? = null,
    box: Box = FULL_FACE,
    variant: String = "",
): String {
    val nameAttr = if (name != null) """ name="$name"""" else ""
    val alphaTransform = if (alpha != null) transform("alpha", alpha) else ""
    return """<PartDraw x="${box.x}" y="${box.y}" width="${box.width}" height="${box.height}"$nameAttr>""" +
        variant + alphaTransform + shapes.joinToString("") + "</PartDraw>"
}

internal fun group(children: List<String>, variant: String, name: String, alpha: Int = 255): String =
    """<Group x="0" y="0" width="480" height="480" name="$name" alpha="$alpha">""" +
        variant + children.joinToString("") + "</Group>"

internal fun container(name: String, content: String): String =
    """<Group x="0" y="0" width="480" height="480" name="$name">$content</Group>"""

internal fun text(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    spans: List<String>,
    align: String = "CENTER",
    transforms: String = "",
): String =
    """<PartText x="$x" y="$y" width="$width" height="$height">$transforms""" +
        """<Text align="$align">${spans.joinToString("")}</Text></PartText>"""

internal fun font(content: String, size: Int, color: String, family: String = FONT): String =
    """<Font family="$family" size="$size" color="$color">$content</Font>"""

internal fun template(format: String, vararg expressions: String): String =
    "<Template>$format" + expressions.joinToString("") { """<Parameter expression="${attr(it)}"/>""" } + "</Template>"

internal class Case(val name: String, val expression: String, val content: String)

/** Shows the content of the first case whose expression holds, otherwise [default]. */
internal fun condition(cases: List<Case>, default: String): String =
    "<Condition><Expressions>" +
        cases.joinToString("") { """<Expression name="${it.name}"><![CDATA[${it.expression}]]></Expression>""" } +
        "</Expressions>" +
        cases.joinToString("") { """<Compare expression="${it.name}">${it.content}</Compare>""" } +
        "<Default>$default</Default></Condition>"
