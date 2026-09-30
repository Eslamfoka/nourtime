package com.nourtime.app.core.learning

import kotlin.random.Random

/** How exact the times of a Tell the Time level are. [key] is the name used in content files. */
enum class ClockPrecision(val key: String, val minutes: List<Int>) {
    HOUR("hour", listOf(0)),
    HALF("half", listOf(0, 30)),
    QUARTER("quarter", listOf(0, 15, 30, 45)),
    FIVE("five", (0 until 60 step 5).toList()),
    MINUTE("minute", (0 until 60).toList()),
    ;

    /** How far a "nearly right" wrong time is from the right one. */
    val step: Int get() = when (this) {
        HOUR -> 60
        HALF -> 30
        QUARTER -> 15
        FIVE -> 5
        MINUTE -> 1
    }
}

/**
 * One Tell the Time level (from `clock/levels.json`). [tasks]: `clock_to_time` (read an analog clock,
 * pick the written time) and `time_to_clock` (read a written time, pick the clock).
 */
data class ClockLevel(
    override val id: String,
    val tasks: List<Task>,
    val precision: ClockPrecision,
    val choices: Int = 3,
    val questions: Int = ClockGame.QUESTIONS,
) : Level

/** Makes Tell the Time questions. Times are generated, so a level gives endless questions. */
object ClockGame {
    const val QUESTIONS = 6

    val TASKS = setOf(Task.CLOCK_TO_TIME, Task.TIME_TO_CLOCK)

    fun questions(spec: ClockLevel, random: Random, count: Int = spec.questions): List<Question> {
        val seen = mutableSetOf<Pair<Int, Int>>()
        return List(count) { i ->
            // Different times within a level while there are enough of them.
            var t: Pair<Int, Int>
            var tries = 0
            do {
                t = random.nextInt(1, 13) to spec.precision.minutes.random(random)
            } while (!seen.add(t) && ++tries < 30)
            val (hour, minute) = t
            val choices = listOf(t) + wrongTimes(hour, minute, spec, random)
            val task = spec.tasks[i % spec.tasks.size]
            Question(
                task = task,
                prompt = listOf(if (task == Task.CLOCK_TO_TIME) Card.Clock(hour, minute) else Card.Time(hour, minute)),
                choices = choices.map { (h, m) -> if (task == Task.CLOCK_TO_TIME) Card.Time(h, m) else Card.Clock(h, m) },
                answer = 0,
            ).shuffled(random)
        }
    }

    /**
     * [ClockLevel.choices] − 1 different wrong times, the typical mistakes first: the hands swapped,
     * the hour next to the right one, the minutes a step away; then any time of the level.
     */
    fun wrongTimes(hour: Int, minute: Int, spec: ClockLevel, random: Random): List<Pair<Int, Int>> {
        val step = spec.precision.step
        fun h(x: Int) = ((x - 1) % 12 + 12) % 12 + 1
        val typical = buildList {
            // Reading the long hand as the hour: 3:30 → 6:15.
            if (minute % 5 == 0 && minute > 0) add(h(minute / 5) to (hour % 12) * 5)
            add(h(hour + 1) to minute)
            add(h(hour - 1) to minute)
            if (step < 60) {
                add(hour to (minute + step) % 60)
                add(hour to (minute - step + 60) % 60)
            }
        }.filter { it != hour to minute }.distinct().shuffled(random)
        val out = linkedSetOf<Pair<Int, Int>>()
        // Mix: about half typical mistakes, the rest any time of the level.
        typical.take(spec.choices / 2).forEach(out::add)
        while (out.size < spec.choices - 1) {
            val t = random.nextInt(1, 13) to spec.precision.minutes.random(random)
            if (t != hour to minute) out += t
        }
        return out.toList()
    }
}
