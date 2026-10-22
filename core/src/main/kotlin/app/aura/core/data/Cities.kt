package app.aura.core.data

import app.aura.core.model.City
import app.aura.core.model.Landmark

object Cities {
    val moscow = City("msk", "Москва", 55.7558, 37.6173, 3, Landmark.MOSCOW_HIGHRISE)
    val petersburg = City("spb", "Санкт-Петербург", 59.9386, 30.3141, 3, Landmark.PETERSBURG_SPIRE)
    val kazan = City("kzn", "Казань", 55.7963, 49.1088, 3, Landmark.KAZAN_MOSQUE)
    val almaty = City("ala", "Алматы", 43.2389, 76.8897, 5, Landmark.ALMATY_MOUNTAINS)

    val all = listOf(moscow, petersburg, kazan, almaty)

    fun byId(id: String): City? = all.firstOrNull { it.id == id }
}
