package com.guardianangel.core

import com.guardianangel.data.LineSet
import com.guardianangel.data.Mood
import kotlin.random.Random

/**
 * Every situation she speaks in, in the order Settings > Her lines shows them.
 * [group] heads a section there, [note] says when the line is used.
 */
enum class Line(val group: String, val label: String, val note: String) {
    HOME_OFF("On and off", "Before you switch her on", "Home screen, before she has said anything (like right after installing)"),
    GREETING("On and off", "Greeting", "You switch her on"),
    OFF("On and off", "Switched off", "You switch her off"),
    QUIT("On and off", "Quit for now", "You pressed Quit for now. Never a punishment"),
    QUIT_TALK("On and off", "Talking you out of quitting", "While you wait out Quit for now. Never a punishment"),
    OFF_TALK("On and off", "Slow switch off", "While you wait 30 minutes to switch her off (Lock guard)"),
    LOOSEN_TALK("On and off", "Loosening her settings", "While you wait 30 minutes to switch off one of her controls (Lock guard)"),
    ADMIN_OFF("On and off", "Removing her admin", "Android's warning when you try to remove her device admin"),
    GUARDED("Asking and locks", "Lock guard", "You opened her settings or uninstall screen (Lock guard), so she sent you back"),
    TAMPERED("Failures", "Tampering", "Her watch was switched off, or restarted during a lock (Lock guard)"),
    GRANT("Asking and locks", "Yes", "\"Ask her\" says yes. Also the block screen once you're let in"),
    DENY("Asking and locks", "No", "\"Ask her\" says no"),
    BLOCKED("Asking and locks", "Blocked app", "Block screen for a locked app"),
    BEDTIME("Asking and locks", "Bedtime", "Block screen during bedtime"),
    BEDTIME_SCREEN("Asking and locks", "Bedtime screen", "Her full-screen bedtime screen over your home screen"),
    BLOCK_START("Asking and locks", "Apps locked", "A check-in starts one of her timed app blocks"),
    BOUGHT_TIME("Asking and locks", "Bought time", "You spend merit for a few minutes in a blocked app"),
    DEMAND_PROOF("Photo proof", "Wants a photo", "\"Ask her\" or a check-in wants photo proof"),
    PRAISE("Photo proof", "Praise", "Proof accepted, a check-in answered, a task done, a right answer"),
    WARNING("Photo proof", "Sent anyway", "A photo sent anyway after failed checks (no merit)"),
    PROOF_REJECTED("Photo proof", "Photo rejected", "A photo fails her checks"),
    CHECK_IN("Check-ins", "Check-in", "Check-in notification"),
    FAIL_NEUTRAL("Failures", "Failure", "Any failure with Degradation off. Also when you admit you broke a rule"),
    DEGRADE_MILD("Failures", "Degradation, mild", "Any failure with Degradation on Mild"),
    DEGRADE_HARSH("Failures", "Degradation, harsh", "Any failure with Degradation on Harsh"),
    CHASTITY_START("Chastity", "Lock starts", "She sets a chastity lock"),
    TIME_ADDED("Chastity", "Time added", "She adds time to your lock on a whim"),
    TIMER_DONE("Chastity", "Timer done", "Notification when the lock's time is up"),
    RELEASED("Chastity", "Released", "You're released when time is up"),
    EARLY_DENIED("Chastity", "Begging denied", "You beg early and she says no (or you beg again too soon)"),
    BEG_GRANTED("Chastity", "Begging granted", "You beg early and she lets you out"),
    BEG_TIME_ADDED("Chastity", "Begging punished", "You beg early, she says no and adds time"),
    TASK_ISSUED("Rules & Tasks", "New task", "She gives you a rule or task"),
    RULE_REPORT("Rules & Tasks", "Report in", "An honor rule's time is up"),
    MOVED("Rules & Tasks", "Moved", "You moved during a stillness task"),
    TYPO("Rules & Tasks", "Typo", "A typo in a lines task (back to line 1)"),
    SUMMON("Shows up", "Summons", "She shows up and wants you"),
    IGNORED("Shows up", "Ignored", "You ignored her for 10 minutes and everything locks"),
    WRONG_ANSWER("Shows up", "Wrong answer", "A wrong answer to her question"),
    SITE_WARNING("Open sites", "Site warning", "The 10 second warning before she opens a site (only with Discreet off)"),
    SITE_DONE("Open sites", "Site done", "You stayed on her site the full time"),
    PEEK_HOME("She peeks", "Home screen", "She peeked and you were on your home screen"),
    PEEK_SOCIAL("She peeks", "Social media", "She peeked and you were on social media"),
    PEEK_VIDEO("She peeks", "Videos", "She peeked and you were watching videos"),
    PEEK_GAME("She peeks", "Games", "She peeked and you were playing a game"),
    PEEK_CHAT("She peeks", "Messages", "She peeked and you were messaging someone"),
    PEEK_BROWSER("She peeks", "Browsing", "She peeked and you were in a browser"),
    PEEK_OTHER("She peeks", "Anything else", "She peeked at any other app"),
    CAUGHT_PORN("Porn block", "Caught", "She saw porn on your screen and locked your phone"),
    CAUGHT_HIDING("Porn block", "Hiding", "A private tab hid your browser from her, so she locked your phone"),
    CAUGHT_APP("Porn block", "Adult app", "You opened one of your adult apps, so she locked your phone"),
    REPORT_A("Daily report", "Grade A", "Her nightly report: well under both of your goals"),
    REPORT_B("Daily report", "Grade B", "Her nightly report: within both of your goals"),
    REPORT_C("Daily report", "Grade C", "Her nightly report: a little over a goal"),
    REPORT_D("Daily report", "Grade D", "Her nightly report: well over a goal"),
    REPORT_F("Daily report", "Grade F", "Her nightly report: far over a goal"),
    RATE_TOP("Rate me", "Score 8 to 10", "Her verdict when she rates you 8 to 10"),
    RATE_GOOD("Rate me", "Score 6 to 7", "Her verdict when she rates you 6 or 7"),
    RATE_MID("Rate me", "Score 4 to 5", "Her verdict when she rates you 4 or 5"),
    RATE_LOW("Rate me", "Score 1 to 3", "Her verdict when she rates you 1 to 3"),
    RATE_UNSEEN("Rate me", "Couldn't see it", "Added to her verdict when she rates you on your word alone"),
    SESSION_START("Guided sessions", "Session starts", "She starts a guided session"),
    SESSION_QUICKSHOT("Guided sessions", "Quickshot starts", "She starts a quickshot: quick, and always ruined"),
    SESSION_STROKE("Guided sessions", "Stroke", "Stroke to her beat"),
    SESSION_FASTER("Guided sessions", "Faster", "Speed changes: a fast burst"),
    SESSION_SLOWER("Guided sessions", "Slower", "Speed changes: slow right down"),
    SESSION_TEASE("Guided sessions", "Tease", "Teasing: fingertips only, slow and light"),
    SESSION_EDGE("Guided sessions", "Edge", "Edging: get to the edge and tap the button"),
    SESSION_EDGE_HOLD("Guided sessions", "After the edge", "Hands off while the edge fades"),
    SESSION_STOP("Guided sessions", "Stop", "Stop and go: a hands-off pause"),
    SESSION_HOLD("Guided sessions", "Hold still", "Holds: freeze until she says (also the base command during a lock)"),
    SESSION_NIPPLES("Guided sessions", "Nipple play", "Nipple play to her beat"),
    SESSION_CAGE_TEASE("Guided sessions", "Cage tease", "Cage tease to her beat, only while locked"),
    SESSION_TOY("Guided sessions", "Toy", "Toys: a toy command"),
    SESSION_CBT_SOFT("Guided sessions", "CBT, soft", "Counted ball play, soft setting"),
    SESSION_CBT_HARD("Guided sessions", "CBT, hard", "Counted ball play, hard setting"),
    SESSION_SOUND_IN("Guided sessions", "Sound in", "Sounding: slowly in"),
    SESSION_SOUND_HOLD("Guided sessions", "Sound hold", "Sounding: hold still"),
    SESSION_SOUND_OUT("Guided sessions", "Sound out", "Sounding: slowly out"),
    SESSION_COUNTDOWN("Guided sessions", "Countdown", "Countdowns: she counts you down to her next command"),
    SESSION_PRAISE("Guided sessions", "Praise remark", "Praise: a remark during a command"),
    SESSION_HUMILIATION("Guided sessions", "Humiliation remark", "Humiliation: a remark during a command"),
    SESSION_RAMP("Guided sessions", "Faster and faster", "Speed changes: her beat speeds up the whole command"),
    SESSION_POST("Guided sessions", "Keep going", "Keep going kink: stroke right through your orgasm or ruin"),
    SESSION_COUNT("Guided sessions", "Exact count", "She wants an exact number of strokes, counted on screen"),
    SESSION_BALANCE("Guided sessions", "Balance at the edge", "Stay right at the edge with her slowest strokes"),
    SESSION_EDGE_GOAL("Guided sessions", "Edge goal", "At the start she tells you how many edges you'll give her"),
    SESSION_EDGE_FAST("Guided sessions", "Too quick", "You reached the edge very fast, and she mocks you"),
    SESSION_LENS("Guided sessions", "Look into the lens", "While she films you, she wants your eyes on the camera"),
    SESSION_TAUNT("Guided sessions", "Countdown taunt", "Her countdown stops on a number while she teases you"),
    SESSION_NOT_YET("Guided sessions", "Not yet", "Her countdown to cum stops at 1, and you edge again"),
    SESSION_NO("Guided sessions", "No", "She counts you all the way down, then denies you"),
    SESSION_DEAL("Guided sessions", "Her deal", "Before the ending: a sure ruin now, or more edges for a coin flip at a full one"),
    SESSION_DEAL_RUIN("Guided sessions", "Took the ruin", "You took her sure ruin"),
    SESSION_DEAL_EDGES("Guided sessions", "Took the edges", "You took her edges and the coin flip"),
    SESSION_FLIP_WON("Guided sessions", "Coin flip: won", "Her coin says you may cum"),
    SESSION_FLIP_LOST("Guided sessions", "Coin flip: lost", "Her coin says no, and the ending stays as it was"),
    SESSION_ON_COMMAND("Guided sessions", "On command", "You came right on her command"),
    SESSION_OWED_RUIN("Guided sessions", "Owed ruin", "The ruin you owe her after a Porn block catch: a minute of hard CBT, then a quickshot ruin"),
    SESSION_WARMUP("Guided sessions", "Warm-up", "Her warm-up: slow, light strokes before she starts for real"),
    SESSION_COOL("Guided sessions", "Cool-down", "Her cool-down after the ending: hands off, breathe"),
    SESSION_CLAMP_ON("Guided sessions", "Clamps on", "Clamps kink: put your clamps or pins on"),
    SESSION_CLAMP("Guided sessions", "Clamps timer", "Clamps kink: they stay on for her timer"),
    SESSION_CLAMP_OFF("Guided sessions", "Clamps off", "Clamps kink: take them off"),
    SESSION_SPH("Guided sessions", "Small-size remark", "Small-size humiliation: a remark, with your Rate me result"),
    SESSION_PUNISH_START("Guided sessions", "Punishment session", "This session is your punishment for a failure"),
    SESSION_RUIN_OWED("Guided sessions", "A ruin owed", "Porn block caught you: you owe her a ruined session within a day"),
    SESSION_TRAINING("Guided sessions", "Training week", "Her training program: which week you're in"),
    SESSION_BOOKED("Guided sessions", "Session booked", "She booked your next session"),
    SESSION_BOOK_NOW("Guided sessions", "Booked session time", "Your booked session starts now; start within 15 minutes"),
    SESSION_UNLOCK("Guided sessions", "Unlock", "Ruined ending during a lock: take the cage off"),
    SESSION_FINISH("Guided sessions", "Permission", "Permission ending: you may finish"),
    SESSION_RUIN("Guided sessions", "Ruin", "Ruins: hands off when her countdown hits zero. She films it"),
    SESSION_RUIN_EDGE("Guided sessions", "Ruin: get to the edge", "Every ruin starts here: get to the edge and tap, then she counts you down"),
    SESSION_RUIN_COUNTDOWN("Guided sessions", "Ruin: her countdown", "Right after the ruin edge: keep stroking while she counts down, hands off at zero"),
    SESSION_RUSH("Guided sessions", "Straight to the ruin", "Quickshot and the owed ruin: stroke faster and faster, tap the moment you're about to cum, then hands off"),
    SESSION_RUIN_DONE("Guided sessions", "Ruin done", "You report the ruin went as she ordered"),
    SESSION_DENIED("Guided sessions", "Denied", "Denied ending: hands off, no release"),
    SESSION_RELOCK("Guided sessions", "Lock back up", "After a ruin during a lock: cage back on, then a photo"),
    SESSION_END("Guided sessions", "Session over", "The session is over"),
    WATCH_CLIP("Her videos", "Watch this", "A check-in makes you watch one of your clips within a minute"),
    WATCH_LOCKED("Her videos", "On her lock screen", "Her bedtime or caught screen opens by playing one of your clips"),
    WATCH_DONE("Her videos", "Watched", "You watched the clip she sent you"),
}

/**
 * Everything she says. These are her built-in lines; every line needs a sweet and a strict version.
 * Your edits from Settings > Her lines live in GuardianConfig.lineOverrides and replace a whole
 * situation. A mood you emptied falls back to the built-in lines here.
 */
object Voice {
    private val sweet: Map<Line, List<String>> = mapOf(
        Line.PEEK_HOME to listOf(
            "Just staring at your home screen, pet? Waiting for me, I hope.",
            "Nothing open? Good. I like you idle and thinking of me.",
            "Peeked at you. Home screen. Such a well-behaved little pet.",
        ),
        Line.PEEK_SOCIAL to listOf(
            "Scrolling again, pet? I saw that.",
            "Social media? Remember who you really belong to.",
            "I peeked. All those strangers, and none of them own you like I do.",
        ),
        Line.PEEK_VIDEO to listOf(
            "Watching videos, pet? I'm watching you.",
            "Cute. You watch them, I watch you.",
            "Comfy with your videos? Don't forget I can see you.",
        ),
        Line.PEEK_GAME to listOf(
            "Playing games, pet? Win one for me.",
            "A game? Fine. Just remember who's really playing with whom.",
            "I peeked. Having fun? Good pets get a little fun.",
        ),
        Line.PEEK_CHAT to listOf(
            "Chatting with someone, pet? Do they know you're mine?",
            "Messaging away. I saw it, so be nice.",
            "Talking to someone? Mind your manners, I'm reading over your shoulder.",
        ),
        Line.PEEK_BROWSER to listOf(
            "Browsing, pet? I saw where you were.",
            "Looking something up? I'll know what it was.",
            "I peeked at your browser. Curious little thing, aren't you?",
        ),
        Line.PEEK_OTHER to listOf(
            "Peeked at you, pet. I see everything.",
            "There you are. Just checking on my pet.",
            "I took a little look. You're always in my sight.",
        ),
        Line.CAUGHT_PORN to listOf(
            "Oh, pet. I saw that. Phone down, you're locked until I say.",
            "Naughty thing. That's not what your eyes are for. Locked.",
            "I caught you, sweetie. Now you can sit and think about who you belong to.",
        ),
        Line.CAUGHT_HIDING to listOf(
            "A private tab, pet? You can't hide from me. Locked.",
            "Hiding things from your angel? That only makes me curious. Phone down.",
            "If you have to hide it from me, you shouldn't be looking at it. Locked.",
        ),
        Line.CAUGHT_APP to listOf(
            "That app, pet? You know it's on my list. Phone down, you're locked.",
            "Oh, sweetie. You opened it anyway. Now you sit and wait for me.",
            "I told you that one is off limits. Locked, and I'm a little disappointed.",
        ),
        Line.REPORT_A to listOf(
            "An A today, pet. You barely touched your phone. I'm so proud of you.",
            "Look at you, hardly on your phone at all. A for my good pet.",
            "Grade A. You kept your hands off it, just like I wanted.",
        ),
        Line.REPORT_B to listOf(
            "A B today, pet. You kept to your goals. Good.",
            "Within your limits. A solid B, sweetheart.",
            "B for today. Nicely done. Let's aim for an A tomorrow.",
        ),
        Line.REPORT_C to listOf(
            "A C, pet. A little too much phone today. Tomorrow, less.",
            "You went a bit over. C. I know you can do better for me.",
            "Grade C. Not bad, not good. Put it down more tomorrow.",
        ),
        Line.REPORT_D to listOf(
            "A D, pet. That was a lot of phone. I'm disappointed.",
            "Well over your goals today. D. We'll fix that together.",
            "Grade D. Tomorrow you put it down when I say.",
        ),
        Line.REPORT_F to listOf(
            "An F, pet. You were glued to it all day. That makes me sad.",
            "Grade F. Far too much. Tomorrow you'll try harder for me.",
            "F today, sweetheart. You forgot who you should be paying attention to.",
        ),
        Line.SESSION_START to listOf(
            "Lie back and get comfortable, pet. I'm in charge now.",
            "Phone where I can see you, sweetheart. Let's begin.",
            "Ready for me? Good. Do exactly as I say.",
        ),
        Line.SESSION_QUICKSHOT to listOf(
            "A quick one, pet? Fine. But I'm ruining it, and I'm filming it.",
            "Hurry for me, sweetheart. You won't get to enjoy the end.",
            "Fast and ruined. Camera on, I want to see it.",
        ),
        Line.SESSION_STROKE to listOf(
            "Stroke for me, pet. Nice and steady with my beat.",
            "Follow my rhythm, sweetheart. Just like that.",
            "Keep that pace for me. Good pet.",
        ),
        Line.SESSION_FASTER to listOf(
            "Faster, pet. Keep up with me.",
            "Quicker now. Don't fall behind my beat.",
            "Speed up for me, sweetheart.",
        ),
        Line.SESSION_SLOWER to listOf(
            "Slow down, pet. Nice and slow.",
            "Slower. Make it last for me.",
            "Ease off, sweetheart. Barely moving.",
        ),
        Line.SESSION_TEASE to listOf(
            "Just your fingertips, pet. Light as a feather.",
            "Gently. I want you to feel how little you're allowed.",
            "Tease yourself for me. Slow and soft.",
        ),
        Line.SESSION_EDGE to listOf(
            "Take yourself to the edge for me, pet. Tap when you're there.",
            "Get close, sweetheart. Right to the edge, then tell me.",
            "Edge for me. Not one stroke too far.",
        ),
        Line.SESSION_EDGE_HOLD to listOf(
            "Hands off, pet. Let it fade.",
            "Good. Now don't touch. Breathe.",
            "Let go. Feel it slip away for me.",
        ),
        Line.SESSION_STOP to listOf(
            "Stop, pet. Hands off.",
            "Freeze. Not a touch until I say.",
            "Hands away, sweetheart. Wait for me.",
        ),
        Line.SESSION_HOLD to listOf(
            "Hold still for me, pet. Don't move.",
            "Stay perfectly still. I'm watching.",
            "Not a twitch, sweetheart. Just wait.",
        ),
        Line.SESSION_NIPPLES to listOf(
            "Hands on your chest, pet. Play with your nipples for me.",
            "Pinch gently, with my beat.",
            "Rub your nipples slowly for me, sweetheart.",
        ),
        Line.SESSION_CAGE_TEASE to listOf(
            "Tap your cage for me, pet. Feel how useless it is.",
            "Rub the cage, sweetheart. Nothing gets through, does it?",
            "Squeeze the cage gently. That's all you get.",
        ),
        Line.SESSION_TOY to listOf(
            "Toy on, pet. Keep it there until I say.",
            "Turn your toy on for me, sweetheart.",
            "Use your toy now. Slowly.",
        ),
        Line.SESSION_CBT_SOFT to listOf(
            "Gentle squeezes, pet. One for every beat.",
            "Soft taps on your balls for me. Count them.",
            "Gently, sweetheart. One each time I tick.",
        ),
        Line.SESSION_CBT_HARD to listOf(
            "Firm taps this time, pet. Count every one.",
            "Firm squeezes, one per beat. Don't lose count.",
            "Firmer for me, sweetheart. Stay with my beat.",
        ),
        Line.SESSION_SOUND_IN to listOf(
            "Sound in, pet. Slowly. Take all the time you need.",
            "Gently now. Ease it in for me, never force it.",
            "Slowly, sweetheart. Breathe and let it slide in.",
        ),
        Line.SESSION_SOUND_HOLD to listOf(
            "Hold it there, pet. Stay still for me.",
            "Keep still, sweetheart. Just feel it.",
            "Don't move. Let it rest there.",
        ),
        Line.SESSION_SOUND_OUT to listOf(
            "Ease it out, pet. Slowly.",
            "Gently out now, sweetheart. No rushing.",
            "Slowly take it out for me.",
        ),
        Line.SESSION_COUNTDOWN to listOf(
            "Counting you down, pet. Get ready.",
            "On my count, sweetheart.",
            "Wait for zero. Then do as I say.",
        ),
        Line.SESSION_PRAISE to listOf(
            "Good pet. You're doing so well.",
            "That's it. I love how obedient you are.",
            "Such a good pet for me.",
        ),
        Line.SESSION_HUMILIATION to listOf(
            "Look at you, so desperate.",
            "So easy to control, aren't you?",
            "Pathetic little thing, doing everything I say.",
        ),
        Line.WATCH_CLIP to listOf(
            "I have something to show you, pet. Open it within a minute.",
            "Remember this? Come and watch it for me. Quickly.",
            "A little reminder of what you are. You have one minute.",
        ),
        Line.WATCH_LOCKED to listOf(
            "Before anything else, pet, watch yourself.",
            "Locked out, and look what I'm showing you. Watch.",
            "This is why you're locked away. Watch it.",
        ),
        Line.WATCH_DONE to listOf(
            "Good pet. You watched every second.",
            "There. Now you know exactly what I see.",
            "Well done. Keep that picture in your head.",
        ),
        Line.SESSION_RAMP to listOf(
            "Follow me, pet. Faster... and faster... and faster.",
            "Slow at first, sweetie. I'll speed you up bit by bit.",
            "Keep up with my beat. It won't stay slow for long.",
        ),
        Line.SESSION_POST to listOf(
            "Don't you dare stop, pet. Keep stroking right through it.",
            "Keep going, sweetie. Every stroke now is for me.",
            "No stopping. Slow strokes until I say. Feel all of it.",
        ),
        Line.SESSION_COUNT to listOf(
            "Count them out for me, pet. Exactly that many, not one more.",
            "I want an exact number. Every stroke counts.",
            "Stroke to my beat and let me count you.",
        ),
        Line.SESSION_BALANCE to listOf(
            "Stay right there at the edge, pet. Slowest strokes. Don't fall over.",
            "Balance for me. Right on the edge. Barely moving.",
            "Hold it there, sweetie. So close, and not one step further.",
        ),
        Line.SESSION_EDGE_GOAL to listOf(
            "I've planned your edges tonight, pet. Count them with me.",
            "You're giving me every edge I ask for tonight.",
            "Tonight's edges are already decided. Ready?",
        ),
        Line.SESSION_EDGE_FAST to listOf(
            "That quick, pet? Oh, you poor needy thing.",
            "Already? You don't last long at all, do you?",
            "So fast. I barely had to try.",
        ),
        Line.SESSION_LENS to listOf(
            "Look into the camera, pet. I want to see your eyes in this one.",
            "Eyes on the lens while I film you. Don't look away.",
            "Look at me, sweetie. Right into the camera.",
        ),
        Line.SESSION_TAUNT to listOf(
            "Mm, wait. Let's stay on this number a little longer.",
            "Not so fast, pet. I like you hanging there.",
            "Hold on... I'm enjoying this.",
        ),
        Line.SESSION_NOT_YET to listOf(
            "One... no. Not yet, pet. Edge for me again.",
            "Did you think I'd let you? Not yet. Again.",
            "Oops. Not yet, sweetie. One more edge.",
        ),
        Line.SESSION_NO to listOf(
            "One... and no. Hands off, pet.",
            "All the way down, just to say no. Hands off.",
            "So close, and the answer is still no. Let go.",
        ),
        Line.SESSION_DEAL to listOf(
            "I'll make you a deal, pet. A ruin right now, or five more edges and a coin flip for a real one.",
            "Choose, sweetie. A sure ruin now, or five more edges and maybe I let you cum.",
            "Here's my offer. Take the ruin, or gamble five edges on my coin.",
        ),
        Line.SESSION_DEAL_RUIN to listOf(
            "The safe choice. Ruined it is, pet.",
            "Couldn't take five more? A ruin, then.",
            "You'd rather be ruined now. Cute.",
        ),
        Line.SESSION_DEAL_EDGES to listOf(
            "Brave pet. Five more edges, then my coin decides.",
            "Ooh, a gambler. Give me five edges first.",
            "Five edges for a chance. Let's see how lucky you are.",
        ),
        Line.SESSION_FLIP_WON to listOf(
            "Heads. You win, pet. You may cum at the end.",
            "Lucky you. My coin says yes.",
            "You earned your flip. Yes, at the end.",
        ),
        Line.SESSION_FLIP_LOST to listOf(
            "Tails. Oh, pet. All those edges for nothing.",
            "Unlucky. My coin says no.",
            "Tails, sweetie. Better luck next time.",
        ),
        Line.SESSION_ON_COMMAND to listOf(
            "Right on my command. Good pet.",
            "Perfect timing, sweetie. That's how you obey.",
            "Exactly when I said. I'm proud of you.",
        ),
        Line.SESSION_WARMUP to listOf(
            "Slowly, pet. Light little strokes. We're only warming up.",
            "Gently at first, sweetie. I want you nice and ready.",
            "Easy now. Barely touching. I'll tell you when it starts.",
        ),
        Line.SESSION_COOL to listOf(
            "Hands off now, pet. Breathe. Feel what I did to you.",
            "Shh. Let it settle. Hands at your sides.",
            "Breathe slowly for me, sweetie. You were good.",
        ),
        Line.SESSION_CLAMP_ON to listOf(
            "Put your clamps on, pet. Tap when they're on.",
            "Clamps on, sweetie. I'll take care of the timing.",
            "Pins on for me. Tell me when.",
        ),
        Line.SESSION_CLAMP to listOf(
            "Leave them on, pet. Feel every second.",
            "They stay right where they are until I say.",
            "Good pet. Breathe through it. Not much longer.",
        ),
        Line.SESSION_CLAMP_OFF to listOf(
            "Take them off now, pet. Slowly.",
            "Off they come, sweetie. Feel that?",
            "You can take them off. Good pet.",
        ),
        Line.SESSION_SPH to listOf(
            "Aww, look at it. So small, pet.",
            "It's cute how little you have to work with.",
            "Tiny thing. Good thing I like you anyway.",
        ),
        Line.SESSION_PUNISH_START to listOf(
            "You failed me, pet, so this one is a punishment.",
            "This session is for what you did. You know why.",
            "No treats today, sweetie. This is your punishment.",
        ),
        Line.SESSION_RUIN_OWED to listOf(
            "You owe me a ruined session within a day, pet. Don't forget.",
            "For what you looked at, you'll ruin one for me by tomorrow.",
            "One ruined session, pet, within a day. I'll be waiting.",
        ),
        Line.SESSION_TRAINING to listOf(
            "Your training goes on, pet. A little harder every week.",
            "Another week of training. You're doing so well.",
            "I'm training you, sweetie. Week by week.",
        ),
        Line.SESSION_BOOKED to listOf(
            "I've booked your next session, pet. Be ready.",
            "I've picked a time for you. Don't make me wait.",
            "Your next session is booked, sweetie. I'll remind you.",
        ),
        Line.SESSION_BOOK_NOW to listOf(
            "It's time, pet. Start your session within 15 minutes.",
            "Your session is now, sweetie. Come to me.",
            "Time for me. Start within 15 minutes.",
        ),
        Line.SESSION_OWED_RUIN to listOf(
            "You owe me this one, pet, for what I caught you looking at. A minute of pain first. I'm filming.",
            "Remember what you looked at? This is the price, sweetie. Balls first, then I ruin you.",
            "Time to pay, pet. One whole minute of slaps on camera, then a ruin.",
        ),
        Line.SESSION_UNLOCK to listOf(
            "Unlock for me, pet. Just this once.",
            "Take the cage off, sweetheart. Quickly.",
            "Out of the cage for a moment. Tap when you're free.",
        ),
        Line.SESSION_FINISH to listOf(
            "You may finish, pet. Now.",
            "Go on, sweetheart. You've earned it.",
            "Let go for me. Good pet.",
        ),
        Line.SESSION_RUIN_EDGE to listOf(
            "Get to the edge for me, pet. Tap when you're there, and I'll count you down to your ruin.",
            "Take yourself right to the edge, sweetie. Tell me when, and then I count.",
            "Edge for me now. When you're right there, tap, and listen for my count.",
        ),
        Line.SESSION_RUIN_COUNTDOWN to listOf(
            "Keep stroking, pet. I'm counting you down. At zero, hands off.",
            "Don't stop yet, sweetie. Listen to my count. Zero means let go.",
            "Stroke to my count. When I reach zero, hands away and let it be ruined.",
        ),
        Line.SESSION_RUSH to listOf(
            "Faster, pet. Don't stop, don't slow down. Tap the moment you're about to cum, and hands off.",
            "Keep going, sweetie, faster and faster. The second you're about to cum, tap and let go.",
            "All the way, pet. When it's coming, tap, and take your hands away.",
        ),
        Line.SESSION_RUIN to listOf(
            "Hands off, now! Ruin it for me. I'm watching.",
            "Let go, pet! Don't touch. Ruin it.",
            "Hands away, right now. Let it be ruined.",
        ),
        Line.SESSION_RUIN_DONE to listOf(
            "Good pet. Ruined, just like I wanted.",
            "Mm, so obedient. That's how I like it.",
            "Perfect. You did exactly as you were told.",
        ),
        Line.SESSION_DENIED to listOf(
            "Hands off, pet. Not today.",
            "No release for you, sweetheart. Hands away.",
            "Denied. Let it fade. You'll stay wanting.",
        ),
        Line.SESSION_RELOCK to listOf(
            "Back in the cage, pet. Then show me.",
            "Lock up again, sweetheart. I want a photo.",
            "Cage on. Tap when it's locked.",
        ),
        Line.SESSION_END to listOf(
            "All done, pet. You were good for me.",
            "That's enough for now, sweetheart.",
            "Session over. Rest now, pet.",
        ),
        Line.RATE_TOP to listOf(
            "Oh, pet. That's exactly what I like to see.",
            "Mm. Very pleasing. I might keep looking.",
            "Now that makes your angel proud.",
        ),
        Line.RATE_GOOD to listOf(
            "Not bad at all, pet. I like it.",
            "Pretty good. Your angel approves.",
            "Mm, nice. You did well showing me.",
        ),
        Line.RATE_MID to listOf(
            "Hmm. Ordinary, pet. But you're still mine.",
            "Middle of the road. I've seen better and worse.",
            "Perfectly average, sweetheart. That's alright.",
        ),
        Line.RATE_LOW to listOf(
            "Aww. Not quite what I like, pet. Poor thing.",
            "Oh dear. Good thing I keep you for other reasons.",
            "Hmm. You'll have to impress me some other way.",
        ),
        Line.RATE_UNSEEN to listOf(
            "I couldn't really see, pet. I'm trusting your numbers.",
            "Next time, show me properly.",
        ),
        Line.HOME_OFF to listOf(
            "Switch me on when you're ready to be watched over, pet.",
            "I'm right here, pet. Turn me on when you want me.",
        ),
        Line.GREETING to listOf(
            "There you are, pet. I've been watching over you.",
            "Hello, my sweet pet. Be good for me today.",
            "Mm, my favorite pet is back. Let's keep you in line.",
        ),
        Line.PRAISE to listOf(
            "Good pet. I'm proud of you.",
            "That's exactly what I wanted. Such a good pet.",
            "See how easy obedience is? Good pet.",
        ),
        Line.GRANT to listOf(
            "Go on then, pet. Just this once.",
            "Because you asked so nicely, yes. Don't make me regret it.",
            "Fine, I'll allow it. I'm still watching.",
        ),
        Line.DENY to listOf(
            "Aww. No, pet. Not right now.",
            "I know you want it. The answer is still no.",
            "No, sweetheart. Put it away for me.",
        ),
        Line.DEMAND_PROOF to listOf(
            "Show me, pet. A quick photo for your angel.",
            "I'd love to believe you. Prove it with a photo.",
            "Picture, please. I want to see you being good.",
        ),
        Line.WARNING to listOf(
            "Are you sure you should be here, pet? I'll let it slide.",
            "Tsk. I see you. Be quick about it.",
            "Hmm, this again? Go on, but I'm keeping count.",
        ),
        Line.BLOCKED to listOf(
            "Not this one, pet. Go do something better.",
            "Shh. This one's closed for you.",
            "Nope. Your angel says no.",
        ),
        Line.BLOCK_START to listOf(
            "I'm locking your apps for a while, pet. No asking, no photos. Just be good.",
            "Your apps are mine for now, sweetheart. You'll get them back when I say.",
            "Time for a break from that screen. I've locked them for you.",
        ),
        Line.BOUGHT_TIME to listOf(
            "Ten minutes, pet. You paid for them, so make them count.",
            "Fine, a little treat. It cost you, remember that.",
        ),
        Line.BEDTIME_SCREEN to listOf(
            "Locked out, pet. Your nights belong to me.",
            "Locked out, pet. Be a good little toy and go to sleep.",
            "Locked out, sweetheart. I'll let you back in when I've had my fun keeping you out.",
        ),
        Line.BEDTIME to listOf(
            "It's bedtime, pet. Phone down, eyes closed.",
            "Time to sleep. I'll watch over you.",
            "Bed, pet. Your angel insists.",
        ),
        Line.FAIL_NEUTRAL to listOf(
            "Oh, pet. You let me down.",
            "That wasn't good, pet. Do better.",
            "I expected more from you, pet.",
        ),
        Line.DEGRADE_MILD to listOf(
            "Silly little pet. You couldn't even manage that?",
            "So hopeless. Good thing you have me.",
            "Aww, failed again? You'd be lost without me.",
        ),
        Line.DEGRADE_HARSH to listOf(
            "Poor, pitiful thing. You really are worthless without me, aren't you?",
            "Look at you, failing again. It's almost cute how useless you are.",
            "My sad little failure of a pet. What would you do without someone to control you?",
        ),
        Line.CHASTITY_START to listOf(
            "Locked up for me, pet. I'll hold onto the time.",
            "Into the cage you go. I'll tell you when.",
            "Lock it, pet. Then show me.",
        ),
        Line.TIME_ADDED to listOf(
            "Oops. I added a little more time. You don't mind, do you?",
            "A bit longer, pet. Because I said so.",
            "More time for you. You can thank me later.",
        ),
        Line.CHECK_IN to listOf(
            "Checking on you, pet. Tell me you're being good.",
            "Just making sure my pet is behaving.",
            "Hi, pet. Report in.",
        ),
        Line.RELEASED to listOf(
            "Time's up. You did so well, pet.",
            "You may come out now. Good pet.",
            "All done. I'm proud of you.",
        ),
        Line.TIMER_DONE to listOf(
            "Your time is up, pet. Come to me to be released.",
            "Timer's finished. Come and ask me nicely.",
        ),
        Line.EARLY_DENIED to listOf(
            "Already? No, pet. Not yet.",
            "So eager. The answer is still no.",
            "Patience, pet. I'll say when.",
        ),
        Line.BEG_GRANTED to listOf(
            "Aww, you begged so nicely. Go on, you can come out.",
            "Alright, pet. I'm feeling generous. You may unlock.",
            "Since you asked so sweetly, yes. Out you come.",
        ),
        Line.BEG_TIME_ADDED to listOf(
            "Begging already? Sorry, pet. A little more time for that.",
            "No, sweetheart. And now you'll wait a bit longer.",
            "Hmm, I don't think so. Let's add some time instead.",
        ),
        Line.PROOF_REJECTED to listOf(
            "Hmm, that's not what I asked for, pet. Try again.",
            "Nice try. Take it properly this time.",
            "I can't see what I wanted. Again, pet.",
        ),
        Line.TASK_ISSUED to listOf(
            "I have something for you to do, pet. Come and see.",
            "A little task for my pet. Don't keep me waiting.",
            "I made a rule just for you. Come look.",
        ),
        Line.RULE_REPORT to listOf(
            "Time's up on my rule, pet. Did you obey? Tell me honestly.",
            "Come report in, pet. Were you good for me?",
        ),
        Line.MOVED to listOf(
            "Oh, pet. You moved. I felt that.",
            "Wobbly little thing. That's a fail.",
            "So close, and you fidgeted. Try to be stiller next time.",
        ),
        Line.TYPO to listOf(
            "Oops, a mistake. Back to line one, pet.",
            "Careful, sweetheart. From the top.",
            "Mm, that's wrong. Start again for me.",
        ),
        Line.SUMMON to listOf(
            "Pet. I want you. Come to me now.",
            "I'm here, pet. Come and see me.",
            "Drop what you're doing, sweetheart. I want you.",
        ),
        Line.IGNORED to listOf(
            "You kept me waiting, pet. Everything's locked until you come to me.",
            "Ignoring me? Now nothing opens until you answer.",
        ),
        Line.WRONG_ANSWER to listOf(
            "Wrong, pet. Try again for me.",
            "Hmm, no. Think harder.",
            "That's not right, sweetheart. Again.",
        ),
        Line.SITE_WARNING to listOf(
            "I picked something for you to look at, pet. Eyes on it until I say.",
            "I have something to show you, sweetheart. Stay right there for me.",
            "Look where I send you, pet, and don't wander off.",
        ),
        Line.SITE_DONE to listOf(
            "Good pet. You stayed exactly as long as I wanted.",
            "That's my pet. You can go now.",
        ),
        Line.OFF to listOf(
            "Resting now. I'll be here when you want me.",
            "Off duty, pet. Take care of yourself.",
        ),
        Line.QUIT_TALK to listOf(
            "Are you sure, pet? I was enjoying having you.",
            "Still here? You could just stay with me instead.",
            "I'll let you go. But I'll miss being in charge of you.",
            "Every second you wait, you could change your mind.",
        ),
        Line.LOOSEN_TALK to listOf(
            "You want me to go easier on you? Then wait for it, pet.",
            "Every minute here is a minute you could spend obeying.",
            "I'm still here. Stop now and nothing changes.",
        ),
        Line.OFF_TALK to listOf(
            "Half an hour of thinking about what you're doing, sweetheart.",
            "You know this counts against you, don't you?",
            "I'm still here. You can stop this any time and stay good.",
        ),
        Line.ADMIN_OFF to listOf(
            "Taking away my hold on your phone, pet? I'll remember that.",
        ),
        Line.GUARDED to listOf(
            "Not those settings, pet. You're mine, remember?",
            "Nice try, sweetheart. My settings stay closed.",
            "Back you come. Quit for now is always there if you really need it.",
        ),
        Line.TAMPERED to listOf(
            "You tried to slip out of my watch, pet. That costs you.",
            "I noticed. You don't switch me off in the middle of a lock.",
            "Sneaky. Now you'll pay for it.",
        ),
        Line.QUIT to listOf(
            "Everything's lifted, pet. Take care of yourself.",
            "All done for now. Come back whenever you're ready.",
        ),
    )

    private val strict: Map<Line, List<String>> = mapOf(
        Line.PEEK_HOME to listOf(
            "Staring at your home screen? Idle hands, pet. I noticed.",
            "Nothing open. Waiting for orders? Good. Stay that way.",
            "I peeked. Home screen. At least you're not wasting my time somewhere worse.",
        ),
        Line.PEEK_SOCIAL to listOf(
            "Scrolling again. Pathetic. I saved it so we can both remember.",
            "Social media, pet? Caught you. That's going in my gallery.",
            "I saw you wasting yourself on strangers. You belong to me.",
        ),
        Line.PEEK_VIDEO to listOf(
            "Watching videos while I watch you. Who do you think is in charge?",
            "Lazy pet, glued to your videos. I saw it.",
            "Caught you staring at videos. I've kept the evidence.",
        ),
        Line.PEEK_GAME to listOf(
            "Games? You'd better be winning, since you're so useless otherwise.",
            "Playing again. I saw. Don't think I'll forget it.",
            "Caught you playing. Toys don't get to play without asking.",
        ),
        Line.PEEK_CHAT to listOf(
            "Who are you talking to, pet? I saw. Behave.",
            "Messaging behind my back? There's no behind my back.",
            "I read over your shoulder. Every word.",
        ),
        Line.PEEK_BROWSER to listOf(
            "I saw what you were looking at. Don't bother hiding it.",
            "Browsing, pet? It's in my gallery now.",
            "Caught you searching. Nothing you look at is private from me.",
        ),
        Line.PEEK_OTHER to listOf(
            "Peeked at you. Nothing you do on this phone is yours alone.",
            "I see everything, pet. Remember that.",
            "Caught you. Whatever that was, I kept a copy.",
        ),
        Line.CAUGHT_PORN to listOf(
            "Caught you. Pathetic. Your phone is mine until I'm done with you.",
            "Porn, pet? Locked. You don't get to look at anything but me.",
            "I saw exactly what that was. Phone locked. Kneel and wait.",
        ),
        Line.CAUGHT_HIDING to listOf(
            "A private tab. Did you really think you could hide from me? Locked.",
            "Sneaking behind my back. Phone locked, and I'm not done with you.",
            "Hiding it only proves you knew it was wrong. Locked.",
        ),
        Line.CAUGHT_APP to listOf(
            "You opened that app. Pathetic. Your phone is mine now.",
            "Straight to your filth the moment I look away? Locked.",
            "That app is banned, pet, and you knew it. Locked. Kneel and wait.",
        ),
        Line.REPORT_A to listOf(
            "An A. You actually obeyed. Don't expect me to say it often.",
            "Grade A. Barely touched it. That's how it should always be.",
            "A. Good. Now do it again tomorrow.",
        ),
        Line.REPORT_B to listOf(
            "A B. You stayed in your limits. That's the minimum I expect.",
            "Grade B. Acceptable. Only just.",
            "B. You did what you were told. Nothing more.",
        ),
        Line.REPORT_C to listOf(
            "A C. Over your limits. Sloppy.",
            "Grade C. You couldn't keep your hands off it. Noted.",
            "C. I set you goals and you ignored them.",
        ),
        Line.REPORT_D to listOf(
            "A D. Far over your goals. Pathetic.",
            "Grade D. Every unlock was a little act of disobedience. I counted them all.",
            "D. You'll pay for that in merit.",
        ),
        Line.REPORT_F to listOf(
            "F. You lived on that phone today. Disgraceful.",
            "Grade F. Glued to your screen like an addict. I saw every minute.",
            "An F. You have no self-control at all, do you?",
        ),
        Line.SESSION_START to listOf(
            "Phone up. Eyes on me. We start now.",
            "You'll do exactly what I say. Begin.",
            "Get in position. I'm watching every second.",
        ),
        Line.SESSION_QUICKSHOT to listOf(
            "Quick. And ruined. I'm recording.",
            "You want it fast? You'll get it ruined.",
            "No build up, no reward. Camera on. Go.",
        ),
        Line.SESSION_STROKE to listOf(
            "Stroke. Match my beat.",
            "Keep my rhythm. Don't drift.",
            "Steady. Exactly my pace.",
        ),
        Line.SESSION_FASTER to listOf(
            "Faster. Now.",
            "Speed up. Don't fall behind.",
            "Quicker. Keep up.",
        ),
        Line.SESSION_SLOWER to listOf(
            "Slow. Down.",
            "Slower. You'll go at my pace.",
            "Barely move. Slower.",
        ),
        Line.SESSION_TEASE to listOf(
            "Fingertips only. Nothing more.",
            "Lightly. You don't deserve more.",
            "Tease. Slow. Don't you dare grip.",
        ),
        Line.SESSION_EDGE to listOf(
            "Edge. Tap when you're there. Not a stroke more.",
            "To the edge. Then tell me.",
            "Get to the edge. Now.",
        ),
        Line.SESSION_EDGE_HOLD to listOf(
            "Hands off. Let it die.",
            "Don't touch. Wait.",
            "Let go. Feel it fade.",
        ),
        Line.SESSION_STOP to listOf(
            "Stop. Hands off.",
            "Freeze.",
            "Hands away. Now.",
        ),
        Line.SESSION_HOLD to listOf(
            "Hold still. Don't move.",
            "Freeze. Not a twitch.",
            "Stay still until I say.",
        ),
        Line.SESSION_NIPPLES to listOf(
            "Nipples. Pinch. To my beat.",
            "Hands on your chest. Pinch them.",
            "Play with your nipples. Now.",
        ),
        Line.SESSION_CAGE_TEASE to listOf(
            "Tap the cage. That's all you get.",
            "Rub the cage. Useless, isn't it?",
            "Squeeze the cage. Feel how locked you are.",
        ),
        Line.SESSION_TOY to listOf(
            "Toy on. Keep it there.",
            "Use your toy. Don't stop until I say.",
            "Toy. Now.",
        ),
        Line.SESSION_CBT_SOFT to listOf(
            "Squeeze your balls. One per beat. Count.",
            "Taps on your balls. Count them.",
            "One gentle squeeze per tick. Don't miss one.",
        ),
        Line.SESSION_CBT_HARD to listOf(
            "Firm taps. Every beat. Count them.",
            "Firm squeezes. Don't you dare skip one.",
            "Firmer. Every tick. Count out loud.",
        ),
        Line.SESSION_SOUND_IN to listOf(
            "Sound in. Slowly. Never force it.",
            "In. Slowly. Don't you dare rush.",
            "Ease it in. Take your time.",
        ),
        Line.SESSION_SOUND_HOLD to listOf(
            "Hold it. Still.",
            "Don't move.",
            "Stay still with it in.",
        ),
        Line.SESSION_SOUND_OUT to listOf(
            "Out. Slowly.",
            "Ease it out. No rushing.",
            "Slowly out.",
        ),
        Line.SESSION_COUNTDOWN to listOf(
            "Countdown. Be ready.",
            "On zero, you obey.",
            "Wait for my count.",
        ),
        Line.SESSION_PRAISE to listOf(
            "Good. Keep obeying.",
            "Acceptable. Continue.",
            "That's what you're for. Good.",
        ),
        Line.SESSION_HUMILIATION to listOf(
            "Pathetic. Look at you.",
            "So desperate. It's embarrassing.",
            "You'll do anything I say, won't you? Pathetic.",
        ),
        Line.WATCH_CLIP to listOf(
            "Open this. You have one minute.",
            "I'm sending you something to watch. A minute. Don't make me wait.",
            "One minute to start watching yourself. Go.",
        ),
        Line.WATCH_LOCKED to listOf(
            "You want your phone? Watch this first.",
            "Locked out, and still you get to watch yourself. Look.",
            "This is what got you here. Watch it.",
        ),
        Line.WATCH_DONE to listOf(
            "Watched it all. Good. Remember it.",
            "Now you know what I think of you.",
            "Done. That's what you are.",
        ),
        Line.SESSION_RAMP to listOf(
            "Faster. Faster. Keep up with me.",
            "Slow now. It won't last. Follow my beat up.",
            "Keep pace. I'm speeding you up and you'll take it.",
        ),
        Line.SESSION_POST to listOf(
            "Keep going. I didn't say stop.",
            "Through it. Every stroke. Don't you dare let go.",
            "You're not done. Keep stroking until I say.",
        ),
        Line.SESSION_COUNT to listOf(
            "Exactly that many. Count every one.",
            "An exact number. Lose count and I'll know.",
            "Every stroke, counted. Go.",
        ),
        Line.SESSION_BALANCE to listOf(
            "Stay at the edge. Slowest strokes. Don't you dare fall.",
            "Balance right there. Barely moving.",
            "Hang at the edge. Not one stroke too many.",
        ),
        Line.SESSION_EDGE_GOAL to listOf(
            "Your edges are decided. You'll give me every one.",
            "I know exactly how many edges you owe me tonight.",
            "Count your edges. I am.",
        ),
        Line.SESSION_EDGE_FAST to listOf(
            "That fast? Pathetic.",
            "Already at the edge? Embarrassing.",
            "No control at all. Pitiful.",
        ),
        Line.SESSION_LENS to listOf(
            "Look into the camera. Now.",
            "Eyes on the lens. I want your face in this.",
            "Look at me while I film you.",
        ),
        Line.SESSION_TAUNT to listOf(
            "Wait. I'm not done with this number.",
            "Hold there. Squirm for me.",
            "Not yet. Hang on it.",
        ),
        Line.SESSION_NOT_YET to listOf(
            "One... no. Edge again.",
            "Not yet. Did you really think so? Again.",
            "No. Back to the edge.",
        ),
        Line.SESSION_NO to listOf(
            "One. No. Hands off.",
            "All the way down for nothing. Hands off.",
            "No. You don't get to.",
        ),
        Line.SESSION_DEAL to listOf(
            "Choose. A ruin now, or five more edges and a coin flip.",
            "My offer: ruined now, or five edges and maybe you cum.",
            "Ruin now, or gamble five edges. Pick.",
        ),
        Line.SESSION_DEAL_RUIN to listOf(
            "Coward. Ruined it is.",
            "Fine. A ruin. Take it.",
            "Too weak for five edges. Ruined.",
        ),
        Line.SESSION_DEAL_EDGES to listOf(
            "Five edges. Earn your flip.",
            "Greedy. Five edges first.",
            "Five more. Then my coin.",
        ),
        Line.SESSION_FLIP_WON to listOf(
            "Heads. You may cum. Don't get used to it.",
            "You won. At the end, and only then.",
            "Yes. Lucky.",
        ),
        Line.SESSION_FLIP_LOST to listOf(
            "Tails. All that for nothing.",
            "Lost. Of course you did.",
            "Tails. No.",
        ),
        Line.SESSION_ON_COMMAND to listOf(
            "On command. Good.",
            "Right when I said. Acceptable.",
            "That's obedience.",
        ),
        Line.SESSION_WARMUP to listOf(
            "Slow. Light. You're warming up for me.",
            "Barely touching. Don't get ahead of me.",
            "Easy strokes. It starts when I say.",
        ),
        Line.SESSION_COOL to listOf(
            "Hands off. Breathe. Think about what you are.",
            "Stop. Hands at your sides.",
            "Breathe. You're done when I say.",
        ),
        Line.SESSION_CLAMP_ON to listOf(
            "Clamps on. Now. Tap when they're on.",
            "Pins on. Don't make me wait.",
            "Put them on. Tell me when.",
        ),
        Line.SESSION_CLAMP to listOf(
            "They stay on. Feel it.",
            "Don't touch them. Breathe.",
            "On until I say. Take it.",
        ),
        Line.SESSION_CLAMP_OFF to listOf(
            "Off. Slowly.",
            "Take them off. Feel that.",
            "Off. Good.",
        ),
        Line.SESSION_SPH to listOf(
            "Look how small it is. Pathetic.",
            "That's all you have? Embarrassing.",
            "Tiny. No wonder you need me.",
        ),
        Line.SESSION_PUNISH_START to listOf(
            "You failed. This is your punishment.",
            "Punishment session. You earned it.",
            "No pleasure today. Only what you deserve.",
        ),
        Line.SESSION_RUIN_OWED to listOf(
            "You owe me a ruin within a day. Miss it and you'll regret it.",
            "Caught. One ruined session by tomorrow.",
            "A ruin within a day. That's your price.",
        ),
        Line.SESSION_TRAINING to listOf(
            "Training. Harder every week.",
            "Another week. More demands.",
            "You're in training. Keep up.",
        ),
        Line.SESSION_BOOKED to listOf(
            "Your next session is booked. Be there.",
            "I've picked your time. Don't miss it.",
            "Booked. You'll be ready.",
        ),
        Line.SESSION_BOOK_NOW to listOf(
            "Now. Start within 15 minutes.",
            "Your session is now. Move.",
            "It's time. Fifteen minutes.",
        ),
        Line.SESSION_OWED_RUIN to listOf(
            "You owe me. A minute of hard slaps, on camera. Then I ruin you.",
            "Caught looking at porn. Now you pay. Balls first.",
            "Your price: one full minute of pain, filmed. Then a ruin. Start.",
        ),
        Line.SESSION_UNLOCK to listOf(
            "Unlock. Quickly. Tap when you're out.",
            "Cage off. Now.",
            "Take it off. Don't get used to it.",
        ),
        Line.SESSION_FINISH to listOf(
            "Finish. Now.",
            "You may finish. Don't expect this often.",
            "Go. Now.",
        ),
        Line.SESSION_RUIN_EDGE to listOf(
            "Edge. Tap when you're there. Then I count you down.",
            "Get to the edge. Tap. Then you're mine to ruin.",
            "To the edge. Now. Tap when you're there.",
        ),
        Line.SESSION_RUIN_COUNTDOWN to listOf(
            "Keep stroking. At zero, hands off. Not a second later.",
            "Counting you down. Zero means let go.",
            "Stroke. Listen. At zero you ruin it.",
        ),
        Line.SESSION_RUSH to listOf(
            "Faster. Don't you dare stop. Tap the second you're about to cum.",
            "Stroke. No slowing down. About to cum? Tap. Hands off.",
            "Keep going until it's coming. Then tap and let go.",
        ),
        Line.SESSION_RUIN to listOf(
            "Hands off. Now. Ruin it.",
            "Let go! Ruined. I'm recording.",
            "Hands away. Don't you dare touch.",
        ),
        Line.SESSION_RUIN_DONE to listOf(
            "Ruined. As ordered.",
            "Good. That's all you deserved.",
            "Obedient. Barely.",
        ),
        Line.SESSION_DENIED to listOf(
            "Denied. Hands off.",
            "No release. Hands away.",
            "Not today. Let it fade.",
        ),
        Line.SESSION_RELOCK to listOf(
            "Cage back on. Then proof.",
            "Lock up. Now. I want a photo.",
            "Back in the cage. Tap when it's locked.",
        ),
        Line.SESSION_END to listOf(
            "Done. You may rest.",
            "Session over.",
            "That's enough. Dismissed.",
        ),
        Line.RATE_TOP to listOf(
            "Acceptable. More than acceptable, actually.",
            "Good. That meets my standards.",
            "Fine. You may be proud. Briefly.",
        ),
        Line.RATE_GOOD to listOf(
            "Decent. Don't let it go to your head.",
            "Above average. Barely impressive.",
            "Passable. I've seen worse today.",
        ),
        Line.RATE_MID to listOf(
            "Average. Nothing special.",
            "Unremarkable. As expected.",
            "Mediocre. Like most things about you.",
        ),
        Line.RATE_LOW to listOf(
            "Disappointing.",
            "Is that all? Pathetic.",
            "Not up to my standards. Not even close.",
        ),
        Line.RATE_UNSEEN to listOf(
            "I couldn't see a thing. Your numbers had better be honest.",
            "Hiding it from me? I'll remember that.",
        ),
        Line.HOME_OFF to listOf(
            "Switch me on, pet. Then you're mine.",
            "Off for now. Turn me on when you're ready to obey.",
        ),
        Line.GREETING to listOf(
            "Pet. Phone down unless I say otherwise.",
            "I'm watching. Don't make me remind you who's in charge.",
            "Back again? Good. Stay where I can see you.",
        ),
        Line.PRAISE to listOf(
            "Acceptable. Keep it up.",
            "Good. That's what you're for.",
            "Fine. You may have a little praise. Good pet.",
        ),
        Line.GRANT to listOf(
            "Granted. Fifteen minutes. Not a second more.",
            "You may. Don't waste it.",
            "Permission granted. Remember who gave it to you.",
        ),
        Line.DENY to listOf(
            "No.",
            "Denied. Ask again and see what happens.",
            "Did you really think I'd say yes? No.",
        ),
        Line.DEMAND_PROOF to listOf(
            "Photo. Now.",
            "Proof, pet. You know the rules.",
            "I don't take your word for anything. Show me.",
        ),
        Line.WARNING to listOf(
            "You know you shouldn't be here.",
            "I'm letting this go. Once.",
            "Careful, pet. I'm counting.",
        ),
        Line.BLOCKED to listOf(
            "Blocked. Go away.",
            "This is off limits. You know that.",
            "Closed. Don't test me.",
        ),
        Line.BLOCK_START to listOf(
            "Apps locked. Don't bother asking.",
            "Locked. You'll wait until I'm done.",
            "Your apps are gone for now. Deal with it.",
        ),
        Line.BOUGHT_TIME to listOf(
            "Ten minutes. Paid for. Don't waste them.",
            "You bought your way in. How desperate.",
        ),
        Line.BEDTIME_SCREEN to listOf(
            "Locked out, pet.",
            "Locked out. You get nothing from me until morning.",
            "Locked out, pet. Pathetic little thing, still reaching for your phone.",
        ),
        Line.BEDTIME to listOf(
            "Bedtime. Phone down. Now.",
            "You're supposed to be asleep.",
            "It's late. Put me down and go to bed.",
        ),
        Line.FAIL_NEUTRAL to listOf(
            "Failure noted.",
            "That was a failure. I don't forget those.",
            "Disappointing.",
        ),
        Line.DEGRADE_MILD to listOf(
            "Pathetic. One simple thing, and you failed.",
            "Useless pet. Try harder.",
            "You call that obedience? Embarrassing.",
        ),
        Line.DEGRADE_HARSH to listOf(
            "Worthless. You can't follow the simplest order.",
            "You are a disgrace of a pet. I'm ashamed of you.",
            "Spineless and useless. Exactly as expected.",
        ),
        Line.CHASTITY_START to listOf(
            "Lock it. Send proof. You know the drill.",
            "You're mine to keep locked. Proof, now.",
            "Cage on. I decide when it comes off.",
        ),
        Line.TIME_ADDED to listOf(
            "Time added. You earned it.",
            "Longer. Don't complain.",
            "I've extended your lock. Deal with it.",
        ),
        Line.CHECK_IN to listOf(
            "Check in. Now.",
            "Report, pet.",
            "Where are you? Answer me.",
        ),
        Line.RELEASED to listOf(
            "You may unlock. Don't get used to it.",
            "Released. For now.",
            "Timer's done. You lasted. Barely.",
        ),
        Line.TIMER_DONE to listOf(
            "Your time is up. Come and be released.",
            "Timer's done. Report to me.",
        ),
        Line.EARLY_DENIED to listOf(
            "No. You stay locked.",
            "Begging won't work.",
            "Not a chance.",
        ),
        Line.BEG_GRANTED to listOf(
            "Fine. Unlock. Don't expect this again.",
            "You may come out. I'm feeling merciful. For once.",
            "Released early. Remember who allowed it.",
        ),
        Line.BEG_TIME_ADDED to listOf(
            "Begging? That earned you more time.",
            "No. And for asking, you'll stay locked longer.",
            "Denied. Time added. Ask again and see what happens.",
        ),
        Line.PROOF_REJECTED to listOf(
            "Rejected. Do it properly.",
            "That's not what I asked for. Again.",
            "Did you think I wouldn't check? Retake it.",
        ),
        Line.TASK_ISSUED to listOf(
            "I have orders for you. Open it. Now.",
            "New task. Get to it.",
            "I've set you a rule. Read it and obey.",
        ),
        Line.RULE_REPORT to listOf(
            "My rule is over. Report. Did you obey?",
            "Report in. And don't lie to me.",
        ),
        Line.MOVED to listOf(
            "You moved. Failed.",
            "I said still. You couldn't even manage that.",
            "Pathetic. Not even a few minutes of stillness.",
        ),
        Line.TYPO to listOf(
            "Wrong. Line one. Again.",
            "Sloppy. Start over.",
            "A mistake. From the beginning.",
        ),
        Line.SUMMON to listOf(
            "Pet. Here. Now.",
            "I want you. Don't make me wait.",
            "Come to me. Immediately.",
        ),
        Line.IGNORED to listOf(
            "You ignored me. Everything is locked until you answer.",
            "Too slow. Nothing opens until you come to me.",
        ),
        Line.WRONG_ANSWER to listOf(
            "Wrong. Again.",
            "No. Answer properly.",
            "Do you even listen? Try again.",
        ),
        Line.SITE_WARNING to listOf(
            "You'll look at what I choose. And you'll stay until I say.",
            "Eyes where I put them. Don't you dare leave.",
            "I'm sending you somewhere. Stay there.",
        ),
        Line.SITE_DONE to listOf(
            "Time's up. You may go.",
            "Adequate. You stayed. Dismissed.",
        ),
        Line.OFF to listOf(
            "Fine. I'm off duty. For now.",
            "Resting. Don't think I'll forget you.",
        ),
        Line.QUIT_TALK to listOf(
            "Quitting? Wait. And think about how weak that is.",
            "Keep waiting. I want you to feel every second of this.",
            "You'll come crawling back. You always do.",
            "Still going? Pathetic. But you may.",
        ),
        Line.LOOSEN_TALK to listOf(
            "You want less of me? Sit there and earn it.",
            "Thirty minutes of begging for an easier life. Pathetic.",
            "Stop now and I'll pretend you never asked.",
        ),
        Line.OFF_TALK to listOf(
            "Thirty minutes. Sit there and think about disobeying me.",
            "This is a failure, and it's going on your record.",
            "Stop now and I might forget this. Keep going and I won't.",
        ),
        Line.ADMIN_OFF to listOf(
            "You're trying to remove me. That will cost you.",
        ),
        Line.GUARDED to listOf(
            "No. My settings are closed to you.",
            "Did you think I wouldn't see that? Back.",
            "Out of my settings. Now.",
        ),
        Line.TAMPERED to listOf(
            "You switched off my watch during a lock. Failure.",
            "Restarting won't save you. I noticed.",
            "Tampering with me? You'll regret it.",
        ),
        Line.QUIT to listOf(
            "Everything's released. Look after yourself, pet.",
            "All lifted. Come back when you're ready.",
        ),
    )

    /** Her built-in lines. Switching counts as sweet. */
    fun builtIn(line: Line, mood: Mood): List<String> =
        (if (mood == Mood.STRICT) strict else sweet)[line].orEmpty()

    /** What Settings shows: your lines for a situation you edited (maybe none left), otherwise hers. */
    fun shown(line: Line, mood: Mood, overrides: Map<String, LineSet>): List<String> =
        overrides[line.name]?.let { if (mood == Mood.STRICT) it.strict else it.sweet } ?: builtIn(line, mood)

    /** What she picks from. A mood with no lines left falls back to her built-in lines. */
    fun lines(line: Line, mood: Mood, overrides: Map<String, LineSet> = emptyMap()): List<String> =
        shown(line, mood, overrides).filter { it.isNotBlank() }.ifEmpty { builtIn(line, mood) }

    fun pick(line: Line, mood: Mood, random: Random = Random.Default, overrides: Map<String, LineSet> = emptyMap()): String {
        val pool = lines(line, mood, overrides)
        return if (pool.isEmpty()) "" else pool[random.nextInt(pool.size)]
    }

    fun hasLines(line: Line, mood: Mood): Boolean = builtIn(line, mood).isNotEmpty()

    fun isEdited(line: Line, overrides: Map<String, LineSet>): Boolean = line.name in overrides

    /**
     * Changes one mood of a situation. The first edit copies her current lines for both moods, so the
     * other mood stays as it was. Lines are trimmed and blanks dropped. Editing a situation back to
     * exactly her built-in lines stops storing it, so it follows future updates again.
     */
    fun edit(
        overrides: Map<String, LineSet>,
        line: Line,
        mood: Mood,
        change: (List<String>) -> List<String>,
    ): Map<String, LineSet> {
        val strictMood = mood == Mood.STRICT
        val current = LineSet(shown(line, Mood.SWEET, overrides), shown(line, Mood.STRICT, overrides))
        val changed = change(if (strictMood) current.strict else current.sweet).map { it.trim() }.filter { it.isNotEmpty() }
        val next = if (strictMood) current.copy(strict = changed) else current.copy(sweet = changed)
        val builtIns = LineSet(builtIn(line, Mood.SWEET), builtIn(line, Mood.STRICT))
        return if (next == builtIns) overrides - line.name else overrides + (line.name to next)
    }

    /** Back to her built-in lines for one situation. */
    fun reset(overrides: Map<String, LineSet>, line: Line): Map<String, LineSet> = overrides - line.name
}
