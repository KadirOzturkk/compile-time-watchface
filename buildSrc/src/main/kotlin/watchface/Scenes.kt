package watchface

private val GLYPHS = listOf("·", "✢", "✳", "✶", "✻", "✽", "✻", "✶", "✳", "✢")

/** The spinner that leads every animated status line. */
internal val SPIN = pick("$FRAME % 10", GLYPHS)

internal enum class Scene {
    LAPTOP, COFFEE, DUCK, THINK, STROLL;

    val id = name.lowercase()
}

/**
 * A scene Clawd plays away from the laptop. [shared] and [ambient] are laid out on the same coordinates,
 * so the ambient transition dims the scene in place.
 */
internal class IdleScene(
    label: String,
    val sub: String,
    val shared: List<String>,
    val live: List<String>,
    val ambient: List<String>,
) {
    val status = font(template("%s $label", SPIN), 20, ACCENT)
    val aodStatus = font("✻ $label", 20, GREY)
}

/** Clawd at the laptop: eyes, hands, cheer pose and floating glyph follow the state. */
internal fun laptopScene(state: State): Pair<List<String>, List<String>> {
    val doze = state.name == "doze"
    val cheer = state.cheer
    val shared = mutableListOf(partDraw(listOf(rect(204, 324, 72, 48, ACCENT))))
    val live = mutableListOf<String>()

    if (state.eyes == LaptopEyes.HALF) {
        val blink = "$FRAME % ${state.blinkEvery} == 13"
        if (cheer != null) {
            shared += laptopEyes(LaptopEyes.OPEN, "$cheer ? 255 : 0")
            shared += laptopEyes(LaptopEyes.HALF, "!($cheer) && !($blink) ? 255 : 0")
            shared += laptopEyes(LaptopEyes.CLOSED, "!($cheer) && ($blink) ? 255 : 0")
        } else {
            shared += laptopEyes(LaptopEyes.HALF, "!($blink) ? 255 : 0")
            shared += laptopEyes(LaptopEyes.CLOSED, "$blink ? 255 : 0")
        }
    } else {
        shared += laptopEyes(state.eyes)
    }

    shared += partDraw(listOf(rect(198, 352, 84, 32, if (doze) LID_DOZE else LID)))
    shared += partDraw(listOf(rect(180, 384, 120, 4, GREY_DARK)))
    shared += spark(232, 360, 16, if (doze) ACCENT_FAINT else ACCENT)

    // A raised hand hides against the body; a pressed one lands as a nub on the lid's top edge.
    fun hands(leftTop: Int, rightTop: Int, alpha: String? = null) =
        partDraw(listOf(rect(204, leftTop, 9, 6, ACCENT), rect(267, rightTop, 9, 6, ACCENT)), alpha)

    if (state.handsOnKeys) {
        live += hands(346, 353)
    } else if (state.tapEvery != null) {
        val tap = "floor($FRAME / ${state.tapEvery}) % 2 == 0"
        val guard = if (cheer != null) "!($cheer) && " else ""
        live += hands(346, 353, "$guard($tap) ? 255 : 0")
        live += hands(353, 346, "$guard!($tap) ? 255 : 0")
    }

    if (cheer != null) {
        val arms = listOf(
            rect(192, 324, 6, 24, ACCENT), rect(198, 336, 6, 12, ACCENT),
            rect(282, 324, 6, 24, ACCENT), rect(276, 336, 6, 12, ACCENT),
        )
        live += partDraw(arms, "$cheer ? 255 : 0")
    }

    state.floater?.let { floater ->
        val transforms = transform("x", floater.x) + transform("y", "340 - ($FRAME % 4) * 6") +
            (floater.alpha?.let { transform("alpha", it) } ?: "")
        live += text(144, 340, 60, 18, listOf(font(floater.content, 14, floater.color)), align = "START", transforms = transforms)
    }
    return shared to live
}

internal val IDLE_SCENES: Map<Scene, IdleScene> = mapOf(
    Scene.COFFEE to coffee(),
    Scene.DUCK to rubberDuck(),
    Scene.THINK to think(),
    Scene.STROLL to stroll(),
)

private fun coffee(): IdleScene {
    val sip = "[SECOND] % 4 == 0"
    val steam = listOf(rect(2, 8, 3, 3, GREY), rect(7, 3, 3, 3, GREY))
    return IdleScene(
        label = "Warming up the JIT…",
        sub = font(template("(coffee break · %ss)", "[SECOND_Z]"), 15, GREY),
        shared = listOf(
            ground(),
            sprite(186, 326, alpha = "!($sip) ? 255 : 0"),
            sprite(186, 326, arms = Arms.SIP, eyes = SpriteEyes.CLOSED, alpha = "$sip ? 255 : 0"),
            partDraw(mug(IVORY, ACCENT), "!($sip) ? 255 : 0", box = Box(288, 346, 15, 14)),
            partDraw(mug(IVORY, ACCENT), "$sip ? 255 : 0", box = Box(284, 330, 15, 14)),
        ),
        live = listOf(
            """<PartDraw x="288" y="330" width="12" height="14">""" +
                transform("y", "330 - ($FRAME % 4) * 2") + transform("alpha", "!($sip) ? 255 : 0") +
                steam.joinToString("") + "</PartDraw>",
        ),
        ambient = listOf(
            ground(),
            sprite(186, 326, color = ACCENT_DIM),
            partDraw(mug(IVORY_DIM, ACCENT_FAINT), box = Box(288, 346, 15, 14)),
        ),
    )
}

private fun rubberDuck(): IdleScene {
    val talk = "[SECOND] % 2 == 0"
    val bubble = template("%s", pick("[SECOND] % 3", listOf("…", "?", "!")))
    return IdleScene(
        label = "Rubber duck debugging…",
        sub = font("(explaining the stack trace)", 15, GREY),
        shared = listOf(
            ground(),
            sprite(186, 326, look = 1, alpha = "$talk ? 255 : 0"),
            sprite(186, 326, arms = Arms.WAVE, look = 1, alpha = "!($talk) ? 255 : 0"),
            """<PartDraw x="294" y="350" width="50" height="34">""" +
                transform("y", "350 - ([SECOND] % 2) * 2") + duck(DUCK, BEAK).joinToString("") + "</PartDraw>",
        ),
        live = listOf(
            text(282, 330, 30, 18, listOf(font(bubble, 14, GREY)), align = "START", transforms = transform("y", "334 - ($FRAME % 4) * 3")),
        ),
        ambient = listOf(
            ground(),
            sprite(186, 326, look = 1, color = ACCENT_DIM),
            partDraw(duck(DUCK_DIM, BEAK_DIM), box = Box(294, 350, 50, 34)),
        ),
    )
}

private fun think(): IdleScene {
    val idea = "[SECOND] % 10 == 0"
    return IdleScene(
        label = "Pondering…",
        sub = font("(reading the docs)", 15, GREY),
        shared = listOf(
            ground(),
            sprite(186, 326, eyes = SpriteEyes.UP, alpha = "!($idea) ? 255 : 0"),
            sprite(186, 326, arms = Arms.UP, alpha = "$idea ? 255 : 0"),
            partDraw(listOf(rect(282, 340, 4, 4, GREY), rect(290, 330, 6, 6, GREY))),
        ),
        live = listOf(
            text(298, 300, 30, 26, listOf(font(template("%s", "$idea ? \"!\" : \"?\""), 22, ACCENT)), align = "START"),
        ),
        ambient = listOf(
            ground(),
            sprite(186, 326, eyes = SpriteEyes.UP, color = ACCENT_DIM),
            partDraw(listOf(rect(282, 340, 4, 4, GREY_DARK), rect(290, 330, 6, 6, GREY_DARK))),
            text(298, 300, 30, 26, listOf(font("?", 22, GREY)), align = "START"),
        ),
    )
}

private fun stroll(): IdleScene {
    // A 10 s loop that starts and ends at the centre, so every minute begins and ends where the other scenes stand.
    val f = "($FRAME % 40)"
    val walking = "(($f >= 1 && $f < 8) || ($f >= 12 && $f < 26) || ($f >= 30 && $f < 37))"
    val offset = "($f < 8 ? $f * 6 : ($f < 12 ? 42 : ($f < 26 ? 42 - ($f - 11) * 6 : ($f < 30 ? -42 : ($f < 37 ? -42 + ($f - 29) * 6 : 0)))))"
    val x = "186 + $offset"
    val y = "326 - (($walking) && $FRAME % 2 == 1 ? 2 : 0)"
    return IdleScene(
        label = "Walking the view tree…",
        sub = font("(measure · layout · draw)", 15, GREY),
        shared = listOf(
            ground(),
            sprite(186, 326, legs = listOf(4, 11), alpha = "($walking) && $FRAME % 2 == 0 ? 255 : 0", xExpr = x, yExpr = y),
            sprite(186, 326, legs = listOf(6, 13), alpha = "($walking) && $FRAME % 2 == 1 ? 255 : 0", xExpr = x, yExpr = y),
            sprite(186, 326, alpha = "!($walking) ? 255 : 0", xExpr = x, yExpr = y),
        ),
        live = emptyList(),
        ambient = listOf(ground(), sprite(186, 326, color = ACCENT_DIM)),
    )
}
