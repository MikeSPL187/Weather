package app.aura.core.advice

import app.aura.core.format.Formatters
import app.aura.core.model.Condition
import app.aura.core.model.HourlyForecast
import kotlin.math.roundToInt

enum class Garment { OUTERWEAR, LAYER, BOTTOM, SHOES, ACCESSORY }

data class OutfitItem(val label: String, val kind: Garment)

data class Outfit(
    val summary: String,
    val items: List<OutfitItem>,
    val umbrella: Boolean,
    val sunscreen: Boolean,
)

/**
 * Picks clothes for the hours someone will spend outside, from the coldest feels-like
 * temperature in that window plus wind, precipitation and UV.
 */
object OutfitAdvisor {
    fun advise(hours: List<HourlyForecast>): Outfit {
        require(hours.isNotEmpty()) { "No hours to dress for" }
        val coldest = hours.minOf { it.feelsLikeC }
        val warmest = hours.maxOf { it.temperatureC }
        val windiest = hours.maxOf { it.windSpeedMs }
        val wettest = hours.maxOf { it.precipitationChancePercent }
        val snow = hours.any { it.condition == Condition.SNOW }
        val umbrella = !snow && (wettest >= 50 || hours.any { it.precipitationMm >= 0.3 })
        val sunscreen = hours.any { it.isDay && it.uvIndex >= 3 }

        val items = mutableListOf<OutfitItem>()
        when {
            coldest <= -15 -> items += listOf(
                OutfitItem("Пуховик", Garment.OUTERWEAR), OutfitItem("Термобельё", Garment.LAYER),
                OutfitItem("Утеплённые брюки", Garment.BOTTOM), OutfitItem("Зимние ботинки", Garment.SHOES),
                OutfitItem("Шапка и варежки", Garment.ACCESSORY),
            )
            coldest <= -5 -> items += listOf(
                OutfitItem("Зимняя куртка", Garment.OUTERWEAR), OutfitItem("Свитер", Garment.LAYER),
                OutfitItem("Джинсы", Garment.BOTTOM), OutfitItem("Зимние ботинки", Garment.SHOES),
                OutfitItem("Шапка и перчатки", Garment.ACCESSORY),
            )
            coldest <= 5 -> items += listOf(
                OutfitItem("Тёплая куртка", Garment.OUTERWEAR), OutfitItem("Свитер", Garment.LAYER),
                OutfitItem("Джинсы", Garment.BOTTOM), OutfitItem("Ботинки", Garment.SHOES),
                OutfitItem("Шапка", Garment.ACCESSORY),
            )
            coldest <= 12 -> items += listOf(
                OutfitItem("Демисезонная куртка", Garment.OUTERWEAR), OutfitItem("Лонгслив", Garment.LAYER),
                OutfitItem("Джинсы", Garment.BOTTOM), OutfitItem("Кроссовки", Garment.SHOES),
            )
            coldest <= 18 -> items += listOf(
                OutfitItem("Худи или лёгкая куртка", Garment.OUTERWEAR),
                OutfitItem("Джинсы", Garment.BOTTOM), OutfitItem("Кроссовки", Garment.SHOES),
            )
            coldest <= 24 -> items += listOf(
                OutfitItem("Футболка", Garment.LAYER), OutfitItem("Лёгкие брюки", Garment.BOTTOM),
                OutfitItem("Кроссовки", Garment.SHOES),
            )
            else -> items += listOf(
                OutfitItem("Футболка", Garment.LAYER), OutfitItem("Шорты", Garment.BOTTOM),
                OutfitItem("Сандалии", Garment.SHOES), OutfitItem("Кепка", Garment.ACCESSORY),
            )
        }
        if (umbrella) items += OutfitItem("Зонт", Garment.ACCESSORY)
        if (windiest >= 8 && coldest <= 12) items += OutfitItem("Капюшон или шарф", Garment.ACCESSORY)
        if (sunscreen) items += OutfitItem("Крем SPF 30", Garment.ACCESSORY)

        val parts = mutableListOf("От ${Formatters.temperature(coldest)} до ${Formatters.temperature(warmest)} по ощущениям")
        if (windiest >= 6) parts += "ветер до ${windiest.roundToInt()} м/с"
        parts += when {
            snow -> "снег"
            umbrella -> "возможен дождь: $wettest %"
            else -> "без осадков"
        }
        return Outfit(parts.joinToString(", ") + ".", items, umbrella, sunscreen)
    }
}
