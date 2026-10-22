package watchface

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The whole watch face for one Watch Face Format version. Version 4 cross-fades into ambient mode;
 * versions 1 to 3 reject Variant timing attributes and switch instantly.
 */
class WatchFaceDocument(formatVersion: Int) {

    private val animated = formatVersion >= 4

    // Elements only one mode shows get disjoint windows so texts never overlap mid-fade; exit replays them forward.
    private val fadeSharedOut = fade(0, "1.0")
    private val fadeLiveOut = fade(0, "0.45", startOffset = "0.55")
    private val fadeSharedIn = fade(255, "1.0")
    private val fadeAodIn = fade(255, "0.45")

    private fun fade(alpha: Int, duration: String, startOffset: String? = null): String {
        val timing = when {
            !animated -> ""
            startOffset != null -> """ startOffset="$startOffset" duration="$duration" interpolation="EASE_IN_OUT""""
            else -> """ duration="$duration" interpolation="EASE_IN_OUT""""
        }
        return """<Variant mode="AMBIENT" target="alpha" value="$alpha"$timing/>"""
    }

    fun render(): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            """<WatchFace width="480" height="480" clipShape="CIRCLE">""" +
            """<Metadata key="CLOCK_TYPE" value="DIGITAL"/>""" +
            """<Metadata key="PREVIEW_TIME" value="10:09:36"/>""" +
            userConfigurations() +
            """<Scene backgroundColor="$BLACK">""" + interactive() + ambient() + "</Scene></WatchFace>\n"

    private fun userConfigurations(): String {
        val options = ACCENT_OPTIONS.withIndex().joinToString("") { (i, option) ->
            val (name, hex) = option
            """<ColorOption id="$i" displayName="accent_$name" colors="#FF$hex #99$hex #66$hex #BF$hex"/>"""
        }
        return """<UserConfigurations><ColorConfiguration id="accent" displayName="accent_label" defaultValue="0">""" +
            options + "</ColorConfiguration></UserConfigurations>"
    }

    private fun interactive(): String {
        val date = text(
            0, 90, 480, 24,
            listOf(font("\$ date ", 18, GREY), font(template("%s %s %s", "[DAY_OF_WEEK_S]", "[DAY_Z]", "[MONTH_S]"), 18, IVORY)),
            transforms = fadeSharedOut,
        )
        val time = text(
            0, 126, 480, 78,
            listOf(
                font(template("%s", "[HOUR_0_23_Z]"), 78, IVORY, FONT_TIME),
                font(":", 78, GREY_DARK, FONT_TIME),
                font(template("%s", "[MINUTE_Z]"), 78, IVORY, FONT_TIME),
            ),
            transforms = fadeSharedOut,
        )
        val cursor = partDraw(listOf(rect(0, 0, 15, 58, ACCENT)), "[SECOND] % 2 == 0 ? 255 : 0", box = Box(365, 136, 15, 58))
        val bpm = text(
            227, 280, 120, 24,
            listOf(font(template("%s", "$HR > 0 ? $HR : \"--\""), 20, IVORY), font(" bpm", 15, GREY)),
            align = "START",
        )
        // Wear OS 4 and Samsung skip the ambient Variant of elements a Condition creates while ambient, so the
        // layer holding the Conditions fades as a whole too.
        val states = group(listOf(stateCondition(::stateView, "state"), minuteCurtain()), fadeSharedOut, "states")
        return group(
            listOf(ticks(), date, time, group(listOf(cursor, heart(), bpm), fadeLiveOut, "live_extras"), states),
            variant = "",
            name = "interactive",
        )
    }

    /** Minute ticks plus the accent seconds ring, one PartDraw per second so each lights up on its own. */
    private fun ticks(): String {
        val minor = mutableListOf<String>()
        val progress = mutableListOf<String>()
        for (i in 0 until 60) {
            val hour = i % 5 == 0
            val a = Math.toRadians((i * 6).toDouble())
            val inner = if (hour) 216 else 223
            val thickness = if (hour) "4" else "2"
            val x1 = 240 + 232 * sin(a)
            val y1 = 240 - 232 * cos(a)
            val x2 = 240 + inner * sin(a)
            val y2 = 240 - inner * cos(a)
            minor += line(x1, y1, x2, y2, if (hour) GREY_DARK else TICK_MINOR, thickness)
            val box = Box(min(x1, x2).toInt() - 3, min(y1, y2).toInt() - 3, abs(x1 - x2).toInt() + 7, abs(y1 - y2).toInt() + 7)
            val local = line(x1 - box.x, y1 - box.y, x2 - box.x, y2 - box.y, ACCENT, thickness)
            progress += partDraw(listOf(local), "[SECOND] >= $i ? 255 : 0", box = box)
        }
        return partDraw(minor, name = "ticks", variant = fadeSharedOut) + group(progress, fadeLiveOut, "progress")
    }

    /**
     * Black curtain over the status lines and the scene that closes over the last [MINUTE_FADE] seconds of a minute in
     * which the scene can switch and opens over the first ones, so the switch fades instead of cutting. It is one
     * long-lived PartDraw because runtimes draw a new branch's first frame before its own alpha applies, and Samsung's
     * runtime ignores per-frame Transforms on a Group.
     */
    private fun minuteCurtain(): String {
        val rotating = STATES.indices.filter { STATES[it].rotation.size > 1 }.map { it + 1 }
        check(rotating == (rotating.first()..rotating.last()).toList()) { "rotating states must be adjacent in STATES" }
        val hourly = STATES.indices.filter { STATES[it].rotation.size == 1 && STATES[it].endsOnTheHour }.map { it + 1 }
        val onTheHour = hourly.joinToString(" || ") { "$STATE_CODE == $it" }
        val switches = "(($STATE_CODE >= ${rotating.first()} && $STATE_CODE <= ${rotating.last()}) || " +
            "(($onTheHour) && ([MINUTE] == 59 || [MINUTE] == 0)))"
        val open = "([SECOND_MILLISECOND] < 30 ? clamp([SECOND_MILLISECOND] / $MINUTE_FADE, 0, 1) : " +
            "clamp((60 - [SECOND_MILLISECOND]) / $MINUTE_FADE, 0, 1))"
        // Status lines, then the scene below the heart row, both inside the minute ticks.
        val cover = listOf(rect(50, 216, 380, 54, BLACK), rect(136, 305, 228, 89, BLACK))
        return partDraw(cover, "$switches ? (1 - $open) * 255 : 0", name = "minute_curtain")
    }

    private fun stateCondition(view: (State, Scene, String) -> String, prefix: String): String {
        val bodies = STATES.map { stateBody(it, view, prefix) }
        val cases = bodies.dropLast(1).mapIndexed { i, body -> Case("state_${i + 1}", "$STATE_CODE == ${i + 1}", body) }
        return condition(cases, bodies.last())
    }

    private fun stateBody(state: State, view: (State, Scene, String) -> String, prefix: String): String {
        val name = "${prefix}_${state.name}"
        val rotation = state.rotation
        if (rotation.size == 1) return view(state, rotation.single(), name)
        val blocks = rotation.mapIndexed { k, scene -> view(state, scene, "${name}_${k}_${scene.id}") }
        val cases = blocks.dropLast(1).mapIndexed { k, block -> Case("rot_$k", "$SLOT % ${rotation.size} == $k", block) }
        return container(name, condition(cases, blocks.last()))
    }

    private fun stateView(state: State, scene: Scene, name: String): String {
        val idle = IDLE_SCENES[scene]
        val status = idle?.status ?: state.status
        val sub = idle?.sub ?: state.sub
        val (shared, live) = if (idle == null) laptopScene(state) else idle.shared to idle.live
        // Names must be unique per branch: the Wear OS 4 runtime drops a branch that repeats a Group name.
        return container(
            name,
            text(0, 220, 480, 26, listOf(status), transforms = fadeSharedOut) +
                text(0, 246, 480, 20, listOf(sub), transforms = fadeLiveOut) +
                group(shared, fadeSharedOut, "${name}_shared") + group(live, fadeLiveOut, "${name}_live"),
        )
    }

    private fun ambient(): String {
        val dimTicks = (0 until 12).map { i ->
            val a = Math.toRadians((i * 30).toDouble())
            line(240 + 232 * sin(a), 240 - 232 * cos(a), 240 + 222 * sin(a), 240 - 222 * cos(a), TICK_MINOR, "2")
        }
        // Same coordinates as the interactive layout, so the cross-fade dims elements in place instead of moving them.
        val shared = listOf(
            partDraw(dimTicks),
            text(
                0, 90, 480, 24,
                listOf(font("\$ date ", 18, GREY_DARK), font(template("%s %s %s", "[DAY_OF_WEEK_S]", "[DAY_Z]", "[MONTH_S]"), 18, GREY)),
            ),
            text(
                0, 126, 480, 78,
                listOf(
                    font(template("%s", "[HOUR_0_23_Z]"), 78, DOZE_TEXT, FONT_LIGHT),
                    font(":", 78, GREY_DARKER, FONT_LIGHT),
                    font(template("%s", "[MINUTE_Z]"), 78, DOZE_TEXT, FONT_LIGHT),
                ),
            ),
            stateCondition(::ambientView, "aod"),
        )
        val cursorOutline = partDraw(
            listOf("""<Rectangle x="1" y="1" width="13" height="56"><Stroke color="$ACCENT_SOFT" thickness="2"/></Rectangle>"""),
            box = Box(365, 136, 15, 58),
        )
        return group(shared, fadeSharedIn, "ambient", alpha = 0) + group(listOf(cursorOutline), fadeAodIn, "ambient_only", alpha = 0)
    }

    /** Static, dimmed twin of a scene, laid out on the same coordinates as its interactive version. */
    private fun ambientView(state: State, scene: Scene, name: String): String {
        val idle = IDLE_SCENES[scene]
        val status = idle?.aodStatus ?: state.aodStatus
        val parts = idle?.ambient ?: ambientLaptop(state)
        return container(name, text(0, 220, 480, 26, listOf(status)) + parts.joinToString(""))
    }

    private fun ambientLaptop(state: State): List<String> {
        val parts = listOf(
            partDraw(listOf(rect(204, 324, 72, 48, ACCENT_DIM))),
            laptopEyes(state.aodEyes),
            partDraw(listOf(rect(198, 352, 84, 32, LID_DOZE))),
            partDraw(listOf(rect(180, 384, 120, 4, LID))),
            spark(232, 360, 16, ACCENT_FAINT),
        )
        if (state.name != "doze") return parts
        return parts + text(296, 330, 30, 18, listOf(font("z", 14, GREY)), align = "START") +
            text(308, 310, 30, 22, listOf(font("Z", 18, GREY)), align = "START")
    }
}


private const val MINUTE_FADE = "0.4"
