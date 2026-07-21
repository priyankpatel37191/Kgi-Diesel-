package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Real nearby-places lookup using free, no-API-key OpenStreetMap services:
 *  - Nominatim: turns a typed location (e.g. "NH-48 Bypass, Jaipur, Rajasthan") into lat/lon
 *  - Overpass: finds real fuel stations / restaurants / hospitals / repair shops near that lat/lon
 *
 * These are free public services (no billing, no API key needed), unlike Google Places.
 */
object NearbyPlacesApi {

    data class Place(
        val name: String,
        val lat: Double,
        val lon: Double,
        val distanceKm: Double,
        val address: String,
        val phone: String
    )

    private val client = OkHttpClient()
    private const val USER_AGENT = "KGIDieselsApp/1.0 (contact: support@kgidiesels.example)"

    suspend fun geocode(locationQuery: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (locationQuery.isBlank()) return@withContext null
        try {
            val encoded = URLEncoder.encode(locationQuery, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val obj = arr.getJSONObject(0)
                Pair(obj.getString("lat").toDouble(), obj.getString("lon").toDouble())
            }
        } catch (e: Exception) {
            null
        }
    }

    // categoryTag examples: "fuel", "restaurant", "hospital", "car_repair"
    suspend fun fetchNearby(
        lat: Double,
        lon: Double,
        categoryTag: String,
        radiusMeters: Int = 10000
    ): List<Place> = withContext(Dispatchers.IO) {
        try {
            val filter = if (categoryTag == "car_repair") "shop=car_repair" else "amenity=$categoryTag"
            val overpassQuery = """
                [out:json][timeout:25];
                node[$filter](around:$radiusMeters,$lat,$lon);
                out body 30;
            """.trimIndent()

            val formBody = FormBody.Builder()
                .add("data", overpassQuery)
                .build()

            val request = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val elements = json.optJSONArray("elements") ?: return@withContext emptyList()

                val results = mutableListOf<Place>()
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags")
                    val name = tags?.optString("name", "")?.takeIf { it.isNotBlank() } ?: continue
                    val elLat = el.optDouble("lat")
                    val elLon = el.optDouble("lon")
                    if (elLat.isNaN() || elLon.isNaN()) continue

                    val street = tags.optString("addr:street", "")
                    val houseNo = tags.optString("addr:housenumber", "")
                    val addressLine = listOf(houseNo, street).filter { it.isNotBlank() }.joinToString(" ").trim()
                    val phone = tags.optString("phone", tags.optString("contact:phone", ""))

                    results.add(
                        Place(
                            name = name,
                            lat = elLat,
                            lon = elLon,
                            distanceKm = haversineKm(lat, lon, elLat, elLon),
                            address = addressLine,
                            phone = phone
                        )
                    )
                }
                results.sortedBy { it.distanceKm }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusKm * c
    }
}
