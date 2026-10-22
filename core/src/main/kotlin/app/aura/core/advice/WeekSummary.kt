package app.aura.core.advice

import app.aura.core.model.Condition
import app.aura.core.model.DailyForecast
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** One or two plain sentences about the coming days, shown above the 10-day list. */
object WeekSummary {

    fun describe(days: List<DailyForecast>, today: LocalDate): String {
        if (days.isEmpty()) return ""
        val sentences = mutableListOf<String>()

        val firstSnow = days.firstOrNull { it.condition == Condition.SNOW }
        val firstStorm = days.firstOrNull { it.condition == Condition.STORM }
        val wetDays = days.filter {
            it.condition != Condition.SNOW &&
                (it.condition == Condition.RAIN || it.condition == Condition.STORM || it.precipitationChancePercent >= 60)
        }
        when {
            firstStorm != null -> sentences += "Гроза ${on(firstStorm.date, today)}."
            wetDays.isEmpty() && firstSnow == null -> sentences += "Без осадков все ${days.size} дней."
            wetDays.isNotEmpty() -> {
                val first = wetDays.first()
                val run = days.dropWhile { it.date != first.date }.takeWhile { it in wetDays }
                sentences += if (run.size >= 3) "Дождливо с ${from(first.date, today)}, ${run.size} ${if (run.size <= 4) "дня" else "дней"} подряд."
                else "Дождь ${on(first.date, today)}."
            }
        }
        if (firstSnow != null) sentences += "Снег ${on(firstSnow.date, today)}."

        if (days.size >= 6) {
            val early = days.take(3).map { it.maxC }.average()
            val late = days.drop(3).take(4).map { it.maxC }.average()
            val delta = (late - early).roundToInt()
            when {
                delta <= -4 -> sentences += "Похолодание: днём на ${-delta}° ниже, чем сейчас."
                delta >= 4 -> sentences += "Потепление: днём на $delta° выше, чем сейчас."
            }
        }
        return sentences.take(2).joinToString(" ")
    }

    /** "сегодня", "завтра", "в пятницу", "во вторник". */
    fun on(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "сегодня"
        today.plusDays(1) -> "завтра"
        else -> when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "в понедельник"
            DayOfWeek.TUESDAY -> "во вторник"
            DayOfWeek.WEDNESDAY -> "в среду"
            DayOfWeek.THURSDAY -> "в четверг"
            DayOfWeek.FRIDAY -> "в пятницу"
            DayOfWeek.SATURDAY -> "в субботу"
            DayOfWeek.SUNDAY -> "в воскресенье"
        }
    }

    private fun from(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "сегодняшнего дня"
        today.plusDays(1) -> "завтрашнего дня"
        else -> when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "понедельника"
            DayOfWeek.TUESDAY -> "вторника"
            DayOfWeek.WEDNESDAY -> "среды"
            DayOfWeek.THURSDAY -> "четверга"
            DayOfWeek.FRIDAY -> "пятницы"
            DayOfWeek.SATURDAY -> "субботы"
            DayOfWeek.SUNDAY -> "воскресенья"
        }
    }
}
