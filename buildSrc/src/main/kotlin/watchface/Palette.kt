package watchface

// Accent comes from the user's Customize choice; each option carries full, 60%, 40% and 75% alpha variants.
internal const val ACCENT = "[CONFIGURATION.accent.0]"
internal const val ACCENT_DIM = "[CONFIGURATION.accent.1]"
internal const val ACCENT_FAINT = "[CONFIGURATION.accent.2]"
internal const val ACCENT_SOFT = "[CONFIGURATION.accent.3]"

// Each name needs a matching accent_<name> string resource.
internal val ACCENT_OPTIONS = listOf("clay" to "D97757", "sky" to "6A9BCC", "mint" to "9FDCAE", "oat" to "E3DACC")

internal const val IVORY = "#FFFAF9F5"
internal const val IVORY_DIM = "#99FAF9F5"
internal const val GREY = "#FF87867F"
internal const val GREY_DARK = "#FF5E5D59"
internal const val GREY_DARKER = "#FF4D4C48"
internal const val TICK_MINOR = "#FF30302E"
internal const val LID = "#FF3D3D3A"
internal const val LID_DOZE = "#FF262624"
internal const val GROUND = "#FF262624"
internal const val YELLOW = "#FFE3B341"
internal const val DOZE_TEXT = "#FF9C9A92"
internal const val BLACK = "#FF000000"
internal const val DUCK = "#FFE3B341"
internal const val DUCK_DIM = "#99E3B341"
internal const val BEAK = "#FFE8833A"
internal const val BEAK_DIM = "#99E8833A"

internal const val FONT = "jetbrains_mono_regular"
internal const val FONT_TIME = "jetbrains_mono_medium"
internal const val FONT_LIGHT = "jetbrains_mono_light"

// Four animation frames per second.
internal const val FRAME = "floor([SECOND_MILLISECOND] * 4)"
internal const val HR = "round([HEART_RATE])"
