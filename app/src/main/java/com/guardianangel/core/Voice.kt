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
    GRANT("Asking and locks", "Yes", "\"Ask her\" says yes. Also the block screen once you're let in"),
    DENY("Asking and locks", "No", "\"Ask her\" says no"),
    BLOCKED("Asking and locks", "Blocked app", "Block screen for a locked app"),
    BEDTIME("Asking and locks", "Bedtime", "Block screen during bedtime"),
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
}

/**
 * Everything she says. These are her built-in lines; every line needs a sweet and a strict version.
 * Your edits from Settings > Her lines live in GuardianConfig.lineOverrides and replace a whole
 * situation. A mood you emptied falls back to the built-in lines here.
 */
object Voice {
    private val sweet: Map<Line, List<String>> = mapOf(
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
        Line.QUIT to listOf(
            "Everything's lifted, pet. Take care of yourself.",
            "All done for now. Come back whenever you're ready.",
        ),
    )

    private val strict: Map<Line, List<String>> = mapOf(
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
