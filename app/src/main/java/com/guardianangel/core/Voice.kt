package com.guardianangel.core

import com.guardianangel.data.Mood
import kotlin.random.Random

enum class Line {
    GREETING, PRAISE, GRANT, DENY, DEMAND_PROOF, WARNING, BLOCKED, BEDTIME, WAIT,
    FAIL_NEUTRAL, DEGRADE_MILD, DEGRADE_HARSH, CHASTITY_START, TIME_ADDED, CHECK_IN,
    RELEASED, TIMER_DONE, EARLY_DENIED, BEG_GRANTED, BEG_TIME_ADDED, PROOF_REJECTED, OFF, QUIT,
}

/** Everything she says. Edit freely; every line needs a sweet and a strict version. */
object Voice {
    private val sweet: Map<Line, List<String>> = mapOf(
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
        Line.WAIT to listOf(
            "Patience, pet. Wait for me.",
            "Count it out with me. Then we'll see.",
            "Good things come to pets who wait.",
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
        Line.WAIT to listOf(
            "Wait. Then we'll see.",
            "You'll wait as long as I say.",
            "Sit still and wait.",
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
        Line.OFF to listOf(
            "Fine. I'm off duty. For now.",
            "Resting. Don't think I'll forget you.",
        ),
        Line.QUIT to listOf(
            "Everything's released. Look after yourself, pet.",
            "All lifted. Come back when you're ready.",
        ),
    )

    fun pick(line: Line, mood: Mood, random: Random = Random.Default): String {
        val pool = (if (mood == Mood.STRICT) strict else sweet)[line].orEmpty()
        return if (pool.isEmpty()) "" else pool[random.nextInt(pool.size)]
    }

    fun hasLines(line: Line, mood: Mood): Boolean =
        (if (mood == Mood.STRICT) strict else sweet)[line].orEmpty().isNotEmpty()
}
