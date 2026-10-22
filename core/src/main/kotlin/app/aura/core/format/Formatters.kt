package app.aura.core.format

import app.aura.core.model.Condition
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val RU = Locale.forLanguageTag("ru")

object Formatters {
    /** Whole degrees with a typographic minus: "−3°", "0°", "18°". */
    fun temperature(celsius: Double): String {
        val rounded = celsius.roundToInt()
        return if (rounded < 0) "−${-rounded}°" else "$rounded°"
    }

    fun time(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun minutes(count: Int): String = "$count мин"

    fun condition(condition: Condition, isNight: Boolean = false): String = when (condition) {
        Condition.CLEAR -> if (isNight) "Ясно, звёздное небо" else "Солнечно"
        Condition.PARTLY_CLOUDY -> "Переменная облачность"
        Condition.CLOUDY -> "Облачно"
        Condition.RAIN -> "Небольшой дождь"
        Condition.STORM -> "Гроза"
        Condition.SNOW -> "Снегопад"
        Condition.FOG -> "Туман"
    }

    /** "Сегодня" for today, otherwise a short weekday: "Пт", "Сб". */
    fun dayLabel(date: LocalDate, today: LocalDate): String =
        if (date == today) "Сегодня"
        else date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU).replaceFirstChar { it.titlecase(RU) }.trimEnd('.')

    /** "22 окт" */
    fun shortDate(date: LocalDate): String =
        "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, RU).trimEnd('.')}"

    /** Eight-point compass name the wind blows from. */
    fun windDirection(degrees: Int): String {
        val names = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        return names[(((degrees % 360) + 360) % 360 + 22) / 45 % 8]
    }

    /** The wind's name by where it blows from: "западный", "юго-восточный". */
    fun windName(degrees: Int): String {
        val names = listOf("северный", "северо-восточный", "восточный", "юго-восточный", "южный", "юго-западный", "западный", "северо-западный")
        return names[(((degrees % 360) + 360) % 360 + 22) / 45 % 8]
    }
}
