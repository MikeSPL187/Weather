package app.aura.core.format

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun temperatureUsesTypographicMinus() {
        assertEquals("18°", Formatters.temperature(17.6))
        assertEquals("0°", Formatters.temperature(-0.4))
        assertEquals("−3°", Formatters.temperature(-3.2))
    }

    @Test
    fun timeIsZeroPadded() {
        assertEquals("07:05", Formatters.time(LocalTime.of(7, 5)))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals("Сегодня", Formatters.dayLabel(today, today))
        assertEquals("Пт", Formatters.dayLabel(today.plusDays(1), today))
        assertEquals("22 окт", Formatters.shortDate(today))
    }

    @Test
    fun windDirectionNames() {
        assertEquals("С", Formatters.windDirection(0))
        assertEquals("ЮЗ", Formatters.windDirection(225))
        assertEquals("С", Formatters.windDirection(350))
        assertEquals("В", Formatters.windDirection(-270))
    }
}
