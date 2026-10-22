package app.aura.core.nowcast

import app.aura.core.model.Nowcast

/** Rain lighter than this is treated as dry: it does not wet clothes or roads. */
const val DRY_THRESHOLD_MM_H = 0.1

data class NowcastSummary(
    val headline: String,
    /** Minutes until the state changes (rain starts or stops), or null when it holds for the whole window. */
    val minutesUntilChange: Int?,
    val isRainingNow: Boolean,
)

/**
 * Turns minute-by-minute precipitation into the one sentence the home screen shows,
 * for example "Дождь закончится через 25 мин".
 */
object NowcastSummarizer {
    fun summarize(nowcast: Nowcast): NowcastSummary {
        val steps = nowcast.intensitiesMmH
        require(steps.isNotEmpty()) { "Nowcast has no steps" }
        val windowMinutes = steps.size * nowcast.stepMinutes
        val rainingNow = steps.first() >= DRY_THRESHOLD_MM_H
        val changeIndex = steps.indexOfFirst { (it >= DRY_THRESHOLD_MM_H) != rainingNow }

        if (changeIndex < 0) {
            val headline = if (rainingNow) "Дождь не прекратится ${window(windowMinutes)}"
            else "Без осадков ${window(windowMinutes)}"
            return NowcastSummary(headline, null, rainingNow)
        }

        val minutes = changeIndex * nowcast.stepMinutes
        val headline = if (rainingNow) "Дождь закончится через $minutes мин"
        else "Дождь начнётся через $minutes мин"
        return NowcastSummary(headline, minutes, rainingNow)
    }

    private fun window(minutes: Int): String = when (val h = minutes / 60) {
        0 -> "в ближайшие $minutes мин"
        1 -> "в ближайший час"
        in 2..4 -> "в ближайшие $h часа"
        else -> "в ближайшие $h часов"
    }
}
