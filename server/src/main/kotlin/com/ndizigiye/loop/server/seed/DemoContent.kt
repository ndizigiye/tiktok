package com.ndizigiye.loop.server.seed

/** A fictional demo creator. Every demo account uses [DemoContent.PASSWORD]. */
data class DemoUser(
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarColor: String,
    val verified: Boolean = false,
    /** Two gradient colours for a generated avatar, or null to keep the colour fallback. */
    val avatarGradient: Pair<String, String>? = null,
)

/** A shared sound: a generated tone reused by several clips. */
data class DemoSound(val key: String, val title: String, val author: String, val tone: String)

/** One generated demo clip. Without [soundKey] the clip gets an "original sound" from [tone]. */
data class DemoClip(
    val number: Int,
    val author: String,
    val caption: String,
    val visual: Visual,
    val seconds: Int,
    val ageHours: Int,
    val soundKey: String? = null,
    val tone: String = Tones.hum(220.0),
) {
    val fileName: String get() = "demo-%02d.mp4".format(number)
}

/** Picture of a clip, built only from ffmpeg's built-in lavfi sources. */
sealed interface Visual {
    /** A lavfi video source plus optional filters applied before the final 540x960 scale. */
    data class Pattern(val source: String, val filters: String = "") : Visual

    /** The clip's own audio drawn as a waveform over a lavfi background. */
    data class Waveform(val background: String, val color: String) : Visual
}

/** Simple tones for `aevalsrc`; sharp edges are avoided with smooth envelopes. */
object Tones {
    fun hum(frequency: Double) =
        "aevalsrc='0.2*sin(2*PI*$frequency*t)*(0.7+0.3*sin(2*PI*1.5*t))+0.06*sin(2*PI*${frequency * 1.5}*t)':s=44100"

    /** A minor arpeggio, four plucked notes per second. */
    const val ARPEGGIO = "aevalsrc='0.25*sin(PI*mod(t*4,1))*sin(2*PI*t*220*pow(2,(3*eq(floor(mod(t*4,4)),1)" +
        "+7*eq(floor(mod(t*4,4)),2)+12*eq(floor(mod(t*4,4)),3))/12))':s=44100"
    const val KILN_HUM = "aevalsrc='0.12*sin(2*PI*130.81*t)+0.1*sin(2*PI*196*t)" +
        "+0.08*sin(2*PI*261.63*t)*(0.6+0.4*sin(2*PI*0.25*t))':s=44100"
    const val TRAIL_SHUFFLE = "aevalsrc='0.35*exp(-8*mod(t*2,1))*sin(2*PI*55*t)" +
        "+0.15*sin(PI*mod(t*4,1))*sin(2*PI*t*(330+110*mod(floor(t*4),2)))':s=44100"
    const val KITCHEN_TICK = "aevalsrc='0.3*exp(-40*mod(t*3,1))*sin(2*PI*1200*t)" +
        "+0.08*sin(2*PI*440*t)*sin(PI*mod(t*0.5,1))':s=44100"
    const val GARDEN_CHIRPS = "aevalsrc='0.18*sin(PI*mod(t*1.5,1))*sin(2*PI*t*(1800+300*sin(2*PI*5*t)))" +
        "+0.06*sin(2*PI*196*t)':s=44100"
    const val FOCUS_LOOP = "aevalsrc='0.15*sin(2*PI*174.6*t)*(0.5+0.5*sin(2*PI*0.5*t))" +
        "+0.1*sin(2*PI*220*t)*(0.5+0.5*cos(2*PI*0.5*t))':s=44100"
}

object DemoContent {
    const val PASSWORD = "loop1234"

    val users = listOf(
        DemoUser(
            "mira.makes", "Mira Okafor",
            "Clay, glaze and the occasional kiln surprise. New pots every Sunday.",
            "#E17055", avatarGradient = "0xE17055" to "0xFDCB6E",
        ),
        DemoUser(
            "tobi_trails", "Tobi Lindqvist",
            "Weekend hikes, trail snacks and slightly wrong maps.",
            "#00B894", avatarGradient = "0x00B894" to "0x0B3D2E",
        ),
        DemoUser(
            "nightowl.beats", "Sol Navarro",
            "Bedroom producer. Loops made after midnight.",
            "#0984E3", verified = true, avatarGradient = "0x1B1F3B" to "0x0984E3",
        ),
        DemoUser(
            "pixel.pantry", "Hana Sato",
            "Tiny kitchen, big flavours. Recipes in under a minute.",
            "#FDCB6E", verified = true, avatarGradient = "0xFDCB6E" to "0xD35400",
        ),
        DemoUser(
            "lena.lifts", "Lena Brandt",
            "Form tips and gentle gym motivation. Rest days count.",
            "#D35400",
        ),
        DemoUser(
            "quietcoder", "Ravi Menon",
            "Shipping small side projects, one commit at a time.",
            "#2D3436", avatarGradient = "0x2D3436" to "0x74B9FF",
        ),
        DemoUser(
            "dot.and.line", "Ines Moreau",
            "Sketchbook spreads and five-minute doodles.",
            "#A29BFE",
        ),
        DemoUser(
            "fieldnotes.ari", "Ari Kowalski",
            "Birds, bugs and backyard science.",
            "#27AE60", avatarGradient = "0x27AE60" to "0xF6E58D",
        ),
    )

    val sounds = listOf(
        DemoSound("arpeggio", "Midnight Arpeggio", "nightowl.beats", Tones.ARPEGGIO),
        DemoSound("kiln", "Kiln Hum", "mira.makes", Tones.KILN_HUM),
        DemoSound("trail", "Trail Mix Shuffle", "tobi_trails", Tones.TRAIL_SHUFFLE),
        DemoSound("tick", "Kitchen Timer Tick", "pixel.pantry", Tones.KITCHEN_TICK),
        DemoSound("chirps", "Garden Chirps", "fieldnotes.ari", Tones.GARDEN_CHIRPS),
        DemoSound("focus", "Focus Loop", "quietcoder", Tones.FOCUS_LOOP),
    )

    private const val W = "s=540x960:r=30"

    val clips = listOf(
        DemoClip(
            1, "mira.makes", "First pull of the morning, still a bit wobbly but it's mine #pottery #wheelthrowing #slowmade",
            Visual.Pattern("gradients=$W:c0=0xE17055:c1=0xFDCB6E:c2=0x6F4E37:nb_colors=3:speed=0.03:type=spiral:seed=11"),
            seconds = 9, ageHours = 5, soundKey = "kiln",
        ),
        DemoClip(
            2, "mira.makes", "Glaze test tiles are out. @dot.and.line called the blue one #pottery #glaze",
            Visual.Pattern(
                "color=c=black:s=270x480:r=30",
                "format=gbrp,geq=r='40+40*sin(2*PI*(X/W+T/5))':g='90+60*sin(2*PI*(Y/H+T/6))':" +
                    "b='170+80*sin(2*PI*((X+Y)/(W+H)-T/4))',",
            ),
            seconds = 8, ageHours = 30, tone = Tones.hum(196.0),
        ),
        DemoClip(
            3, "mira.makes", "Kiln opening day is the best day #pottery #studiovlog",
            Visual.Pattern("mandelbrot=s=270x480:r=30:maxiter=400:start_x=-0.7453:start_y=0.1127", "hue=h=200:s=1.3,"),
            seconds = 10, ageHours = 96, soundKey = "kiln",
        ),
        DemoClip(
            4, "tobi_trails", "Sunrise at the ridge. 6 km in and zero regrets #hiking #outdoors #sunrise",
            Visual.Pattern("gradients=$W:c0=0xFF9F43:c1=0x5F27CD:c2=0xFECA57:nb_colors=3:speed=0.02:type=linear:seed=4"),
            seconds = 11, ageHours = 3, soundKey = "trail",
        ),
        DemoClip(
            5, "tobi_trails", "Packed trail mix for @fieldnotes.ari and ate half before the trailhead #hiking #snacks",
            Visual.Pattern("testsrc2=$W"),
            seconds = 7, ageHours = 52, soundKey = "trail",
        ),
        DemoClip(
            6, "tobi_trails", "Map says left. Gut says right. #hiking #adventure",
            Visual.Pattern(
                "cellauto=s=135x240:r=15:rule=30:random_fill_ratio=0.3:scroll=1:seed=8",
                "format=rgb24,colorchannelmixer=rr=0.2:gg=0.75:bb=0.45,scale=540:960:flags=neighbor,",
            ),
            seconds = 8, ageHours = 150, tone = Tones.hum(246.94),
        ),
        DemoClip(
            7, "nightowl.beats", "Late night loop, headphones on #music #beats #producer",
            Visual.Waveform("gradients=$W:c0=0x1B1F3B:c1=0x3D2C5E:nb_colors=2:speed=0.02:type=radial:seed=2", "0xF2C14E"),
            seconds = 12, ageHours = 2, soundKey = "arpeggio",
        ),
        DemoClip(
            8, "nightowl.beats", "Turned a two-note idea into this #music #synth",
            Visual.Waveform("gradients=$W:c0=0x0B132B:c1=0x1C2541:c2=0x3A506B:nb_colors=3:speed=0.01:type=circular:seed=5", "0x5BC0BE"),
            seconds = 9, ageHours = 40, soundKey = "arpeggio",
        ),
        DemoClip(
            9, "nightowl.beats", "When the arpeggio finally sits right in the mix #music #producer #beats",
            Visual.Pattern("smptehdbars=$W", "hue=h=t*40,"),
            seconds = 8, ageHours = 120, soundKey = "arpeggio",
        ),
        DemoClip(
            10, "pixel.pantry", "60-second tomato soup, no blender needed #recipe #cooking #quickmeals",
            Visual.Pattern("colorspectrum=$W:type=all", "hue=h=t*30,"),
            seconds = 10, ageHours = 8, soundKey = "tick",
        ),
        DemoClip(
            11, "pixel.pantry", "Rating my roommate's toast: 7/10 @lena.lifts #cooking #breakfast",
            Visual.Pattern("rgbtestsrc=$W", "hue=h=t*25,"),
            seconds = 6, ageHours = 60, soundKey = "tick",
        ),
        DemoClip(
            12, "pixel.pantry", "Knife skills practice: slow is smooth #cooking #kitchentips",
            Visual.Pattern("yuvtestsrc=$W", "hue=h=t*20,"),
            seconds = 7, ageHours = 200, tone = Tones.hum(293.66),
        ),
        DemoClip(
            13, "lena.lifts", "Three cues for a better squat. Save this for leg day #fitness #gym #formcheck",
            Visual.Pattern(
                "life=s=135x240:r=15:mold=10:ratio=0.12:life_color=#55EFC4:death_color=#1E3A2F:seed=21",
                "scale=540:960:flags=neighbor,",
            ),
            seconds = 10, ageHours = 12, soundKey = "trail",
        ),
        DemoClip(
            14, "lena.lifts", "Rest days count too #fitness #recovery",
            Visual.Pattern("gradients=$W:c0=0xA8E6CF:c1=0xDCEDC1:c2=0xFFD3B6:nb_colors=3:speed=0.01:type=radial:seed=9"),
            seconds = 8, ageHours = 75, tone = Tones.hum(174.61),
        ),
        DemoClip(
            15, "lena.lifts", "Leg day playlist pick from @nightowl.beats #fitness #gym #music",
            Visual.Waveform("gradients=$W:c0=0x2D3436:c1=0xD35400:nb_colors=2:speed=0.02:type=linear:seed=3", "0xFFFFFF"),
            seconds = 9, ageHours = 170, soundKey = "arpeggio",
        ),
        DemoClip(
            16, "quietcoder", "Rendering a fractal in 40 lines of code #coding #fractal #creativecoding",
            Visual.Pattern("mandelbrot=s=270x480:r=30:maxiter=300"),
            seconds = 11, ageHours = 18, soundKey = "focus",
        ),
        DemoClip(
            17, "quietcoder", "Cellular automaton, rule 110, because why not #coding #creativecoding",
            Visual.Pattern(
                "cellauto=s=135x240:r=15:rule=110:random_fill_ratio=0.5:scroll=1:seed=3",
                "format=rgb24,colorchannelmixer=rr=0.95:gg=0.55:bb=0.25,scale=540:960:flags=neighbor,",
            ),
            seconds = 9, ageHours = 66, soundKey = "focus",
        ),
        DemoClip(
            18, "quietcoder", "Day 12 of shipping one tiny thing a day #coding #buildinpublic",
            Visual.Pattern("testsrc2=$W", "hue=h=180,"),
            seconds = 6, ageHours = 230, tone = Tones.hum(261.63),
        ),
        DemoClip(
            19, "dot.and.line", "Five-minute doodle: only straight lines allowed #art #drawing #doodle",
            Visual.Pattern("sierpinski=$W:type=carpet", "hue=h=t*15,"),
            seconds = 8, ageHours = 22, tone = Tones.hum(329.63),
        ),
        DemoClip(
            20, "dot.and.line", "Colour study inspired by @mira.makes and her glazes #art #colorstudy",
            Visual.Pattern(
                "color=c=black:s=270x480:r=30",
                "format=gbrp,geq=r='128+127*sin(2*PI*(X/W+T/4))':g='128+127*sin(2*PI*(Y/H+T/5))':" +
                    "b='128+127*sin(2*PI*((X+Y)/(W+H)-T/3))',",
            ),
            seconds = 9, ageHours = 85, soundKey = "kiln",
        ),
        DemoClip(
            21, "dot.and.line", "Sketchbook flip, spring edition #art #sketchbook",
            Visual.Pattern("smptebars=$W", "hue=h=t*60:s=0.8,"),
            seconds = 7, ageHours = 190, tone = Tones.hum(349.23),
        ),
        DemoClip(
            22, "fieldnotes.ari", "Backyard bird count: 14 species before breakfast #nature #birds #science",
            Visual.Pattern("gradients=$W:c0=0x27AE60:c1=0xF6E58D:c2=0x0B3D2E:nb_colors=3:speed=0.03:type=spiral:seed=14"),
            seconds = 12, ageHours = 6, soundKey = "chirps",
        ),
        DemoClip(
            23, "fieldnotes.ari", "Game of life, but make it a meadow #nature #science #creativecoding",
            Visual.Pattern(
                "life=s=135x240:r=15:mold=20:ratio=0.2:life_color=#F6E58D:death_color=#14532D:seed=5",
                "scale=540:960:flags=neighbor,",
            ),
            seconds = 10, ageHours = 48, soundKey = "chirps",
        ),
        DemoClip(
            24, "fieldnotes.ari", "Pond water under the microscope. Look at them go #nature #science #microscope",
            Visual.Pattern("mandelbrot=s=270x480:r=30:maxiter=250:start_x=-0.1011:start_y=0.9563", "hue=h=120,"),
            seconds = 9, ageHours = 140, soundKey = "chirps",
        ),
    )

    val commentTexts = listOf(
        "This is so satisfying to watch",
        "Okay the colours here are unreal",
        "How long did this take you?",
        "Saving this for later",
        "The sound on this one is perfect",
        "I've watched this way too many times",
        "Teach me your ways",
        "Wait, how did you do that transition?",
        "This made my morning",
        "Underrated post right here",
        "Need a part two please",
        "The patience this takes!",
        "Adding this to my weekend plans",
        "Can't stop looping this",
        "Is this real time or sped up?",
    )

    val replyTexts = listOf(
        "Thank you! Part two is coming soon",
        "About two hours start to finish",
        "Glad you like it!",
        "Right?! Same here",
        "Real time, believe it or not",
        "Agreed, this one is special",
    )
}
