package app.aura.data

import android.content.Context
import app.aura.core.data.Cities
import app.aura.core.model.City
import app.aura.core.model.Landmark
import org.json.JSONArray
import org.json.JSONObject

/** The viewer's saved cities and the one they last looked at, kept in SharedPreferences. */
class CityStore(context: Context) {
    private val prefs = context.getSharedPreferences("aura_cities", Context.MODE_PRIVATE)

    fun load(): List<City> {
        val raw = prefs.getString(KEY_CITIES, null) ?: return Cities.defaults
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i -> array.getJSONObject(i).toCity() }
        }.getOrDefault(Cities.defaults).ifEmpty { Cities.defaults }
    }

    fun save(cities: List<City>) {
        val array = JSONArray()
        cities.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_CITIES, array.toString()).apply()
    }

    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED, value).apply()
        }

    private companion object {
        const val KEY_CITIES = "cities"
        const val KEY_SELECTED = "selected"
    }
}

internal fun City.toJson() = JSONObject()
    .put("id", id).put("name", name).put("region", region)
    .put("lat", latitude).put("lon", longitude)
    .put("offset", utcOffsetMinutes).put("landmark", landmark.name)

internal fun JSONObject.toCity() = City(
    id = getString("id"),
    name = getString("name"),
    region = optString("region"),
    latitude = getDouble("lat"),
    longitude = getDouble("lon"),
    utcOffsetMinutes = getInt("offset"),
    landmark = runCatching { Landmark.valueOf(getString("landmark")) }.getOrDefault(Landmark.GENERIC_TOWN),
)
