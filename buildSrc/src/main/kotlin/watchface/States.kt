package watchface

private val WORK_VERBS = listOf(
    "Compiling", "Recomposing", "Syncing Gradle", "Indexing", "Dexing", "Inflating layouts",
    "Running lint", "Shrinking with R8", "Building APK", "Installing APK", "Launching Activity", "Running KSP",
)
private val CODE_GLYPHS = listOf("{ }", ";", "</>", "?.", "!!", "::", "=>", "@")

/** Text that drifts up beside the laptop. */
internal class Floater(val content: String, val color: String, val x: String, val alpha: String? = null)

/** What Clawd is up to: picked from heart rate and time of day, drawn at the laptop or in an idle scene. */
internal class State(
    val name: String,
    val status: String,
    val sub: String,
    val aodStatus: String,
    val eyes: LaptopEyes,
    val aodEyes: LaptopEyes = LaptopEyes.HALF,
    val blinkEvery: Int? = null,
    val tapEvery: Int? = null,
    val handsOnKeys: Boolean = false,
    val cheer: String? = null,
    val floater: Floater? = null,
    val rotation: List<Scene> = listOf(Scene.LAPTOP),
    val endsOnTheHour: Boolean = true,
)

// Matches the 1-based codes of STATE_CODE.
internal val STATES: List<State> = run {
    // Work shows the laptop only in the first two minutes of each six, so the verb steps through those two.
    val verb = pick("(floor($SLOT / 6) * 2 + $SLOT % 6) % 12", WORK_VERBS)
    val code = template("%s", pick("[SECOND] % 8", CODE_GLYPHS))
    val workCheer = "[SECOND] == 0"
    val codeFloater = Floater(code, ACCENT, "[SECOND] % 2 == 1 ? 300 : 144", "!($workCheer) ? 255 : 0")
    fun spinning(label: String) = font(template("%s $label", SPIN), 20, ACCENT)
    fun aod(label: String) = font("✻ $label", 20, GREY)
    listOf(
        State(
            name = "anr",
            status = font("✻ Clawd isn’t responding", 20, YELLOW),
            sub = font("(Wait · Close app)", 15, GREY),
            aodStatus = aod("Clawd isn’t responding"),
            eyes = LaptopEyes.OPEN,
            aodEyes = LaptopEyes.OPEN,
            handsOnKeys = true,
            floater = Floater("!", YELLOW, "300", "[SECOND] % 2 == 0 ? 255 : 0"),
            endsOnTheHour = false,
        ),
        State(
            name = "busy",
            status = spinning("Skipping frames…"),
            sub = font(template("(%ss · main thread busy)", "[SECOND_Z]"), 15, GREY),
            aodStatus = aod("Skipping frames…"),
            eyes = LaptopEyes.OPEN,
            aodEyes = LaptopEyes.OPEN,
            tapEvery = 1,
            cheer = workCheer,
            floater = codeFloater,
            endsOnTheHour = false,
        ),
        State(
            name = "doze",
            status = font("✻ Doze mode", 20, DOZE_TEXT),
            sub = font("(wake lock released)", 15, GREY),
            aodStatus = aod("Doze mode"),
            eyes = LaptopEyes.CLOSED,
            aodEyes = LaptopEyes.CLOSED,
            floater = Floater(template("%s", "[SECOND] % 2 == 1 ? \"Z\" : \"z\""), DOZE_TEXT, "296"),
        ),
        State(
            name = "standby",
            status = font("✻ App Standby", 20, ACCENT),
            sub = font("(resting · battery optimized)", 15, GREY),
            aodStatus = aod("App Standby"),
            eyes = LaptopEyes.HALF,
            blinkEvery = 16,
            tapEvery = 4,
            rotation = listOf(Scene.THINK, Scene.COFFEE, Scene.LAPTOP),
        ),
        State(
            name = "coldStart",
            status = spinning("Cold start…"),
            sub = font("(warming up caches)", 15, GREY),
            aodStatus = aod("Cold start…"),
            eyes = LaptopEyes.HALF,
            blinkEvery = 16,
            tapEvery = 4,
            cheer = "[SECOND] % 6 == 0",
            rotation = listOf(Scene.COFFEE, Scene.LAPTOP, Scene.THINK),
        ),
        State(
            name = "gc",
            status = spinning("Garbage collecting…"),
            sub = font("(lunch · GC pause)", 15, GREY),
            aodStatus = aod("Garbage collecting…"),
            eyes = LaptopEyes.OPEN,
            floater = Floater("gc()", ACCENT, "300"),
            rotation = listOf(Scene.STROLL, Scene.COFFEE, Scene.LAPTOP),
        ),
        State(
            name = "work",
            status = font(template("%s %s…", SPIN, verb), 20, ACCENT),
            sub = font(template("(%ss · esc to interrupt)", "[SECOND_Z]"), 15, GREY),
            aodStatus = font(template("✻ %s…", verb), 20, GREY),
            eyes = LaptopEyes.HALF,
            blinkEvery = 24,
            tapEvery = 2,
            cheer = workCheer,
            floater = codeFloater,
            rotation = listOf(Scene.LAPTOP, Scene.LAPTOP, Scene.THINK, Scene.DUCK, Scene.STROLL, Scene.COFFEE),
        ),
        State(
            name = "saveState",
            status = spinning("Saving instance state…"),
            sub = font(template("(%ss · onPause)", "[SECOND_Z]"), 15, GREY),
            aodStatus = aod("Saving instance state…"),
            eyes = LaptopEyes.HALF,
            blinkEvery = 24,
            tapEvery = 2,
            cheer = workCheer,
            floater = codeFloater,
            rotation = listOf(Scene.LAPTOP, Scene.STROLL, Scene.COFFEE),
        ),
        State(
            name = "background",
            status = spinning("Going to background…"),
            sub = font("(onStop · lights off)", 15, GREY),
            aodStatus = aod("Going to background…"),
            eyes = LaptopEyes.HALF,
            blinkEvery = 16,
            tapEvery = 4,
            rotation = listOf(Scene.LAPTOP, Scene.THINK),
        ),
    )
}

/** 1-based index into [STATES]: high heart rate first, then low heart rate, then the hour. */
internal const val STATE_CODE = "($HR >= 130 ? 1 : ($HR >= 100 ? 2 : ([HOUR_0_23] < 6 ? 3 : (($HR > 0 && $HR < 55) ? 4 : " +
    "([HOUR_0_23] < 9 ? 5 : ([HOUR_0_23] == 12 ? 6 : ([HOUR_0_23] < 18 ? 7 : ([HOUR_0_23] < 22 ? 8 : 9))))))))"

// Scenes advance with the clock, so waking the screen never restarts one.
internal const val SLOT = "([HOUR_0_23] * 60 + [MINUTE])"
