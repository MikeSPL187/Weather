package app.aura.core.data

import app.aura.core.model.City
import app.aura.core.model.Landmark

object Cities {
    val moscow = City("msk", "Москва", "Россия", 55.7558, 37.6173, 180, Landmark.MOSCOW_HIGHRISE)
    val petersburg = City("spb", "Санкт-Петербург", "Россия", 59.9386, 30.3141, 180, Landmark.PETERSBURG_SPIRE)
    val kazan = City("kzn", "Казань", "Татарстан, Россия", 55.7963, 49.1088, 180, Landmark.KAZAN_MOSQUE)
    val almaty = City("ala", "Алматы", "Казахстан", 43.2389, 76.8897, 300, Landmark.ALMATY_MOUNTAINS)

    val defaults = listOf(moscow, petersburg, kazan, almaty)

    fun byId(id: String): City? = defaults.firstOrNull { it.id == id }

    /** Cities with a hand-built diorama; every other place gets the generic town. */
    fun landmarkFor(name: String): Landmark = when (name.lowercase()) {
        "москва" -> Landmark.MOSCOW_HIGHRISE
        "санкт-петербург" -> Landmark.PETERSBURG_SPIRE
        "казань" -> Landmark.KAZAN_MOSQUE
        "алматы" -> Landmark.ALMATY_MOUNTAINS
        else -> Landmark.GENERIC_TOWN
    }
}
