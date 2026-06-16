package dev.yar.android.data

import android.content.Context
import dev.yar.android.domain.NoaItem
import dev.yar.android.domain.Program
import dev.yar.android.domain.Region
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.random.Random

class HttpRadikoClient(
    private val context: Context? = null,
) : RadikoClient {
    override suspend fun getRegions(): List<Region> = withContext(Dispatchers.IO) {
        cachedRegions?.let { regions ->
            if (System.currentTimeMillis() - cachedRegionsTimestamp < REGIONS_CACHE_TTL_MS) {
                return@withContext regions
            }
        }

        val xml = getText("https://radiko.jp/v3/station/region/full.xml")
        RadikoXmlParser.parseStationsXml(xml).also { regions ->
            cachedRegions = regions
            cachedRegionsTimestamp = System.currentTimeMillis()
        }
    }

    override suspend fun getPrograms(stationId: String, date: String?): List<Program> {
        return withContext(Dispatchers.IO) {
            val station = getRegions()
                .asSequence()
                .flatMap { it.stations.asSequence() }
                .firstOrNull { it.id == stationId }
                ?: throw IllegalArgumentException("unknown station: $stationId")
            val dateString = date ?: RadikoXmlParser.formatBroadcastDateJst()
            val xml = getText("https://radiko.jp/v3/program/date/$dateString/${station.areaId}.xml")
            RadikoXmlParser.parseProgramsXml(xml, stationId)
        }
    }

    override suspend fun getLatestNoaItems(stationId: String): List<NoaItem> = withContext(Dispatchers.IO) {
        val json = getTextOrNull("https://api.radiko.jp/music/api/v1/noas/$stationId/latest?size=20") ?: return@withContext emptyList()
        parseNoaItems(json)
    }

    override suspend fun getNoaItems(stationId: String, startTime: String, endTime: String): List<NoaItem> = withContext(Dispatchers.IO) {
        val startIso = URLEncoder.encode(radikoToIsoJst(startTime), Charsets.UTF_8.name())
        val endIso = URLEncoder.encode(radikoToIsoJst(endTime), Charsets.UTF_8.name())
        val json = getTextOrNull(
            "https://api.radiko.jp/music/api/v1/noas/$stationId?start_time_gte=$startIso&end_time_lt=$endIso",
        ) ?: return@withContext emptyList()
        parseNoaItems(json)
    }

    override suspend fun getLivePlaylistUrl(stationId: String): String {
        return getLiveStream(stationId).playlistUrl
    }

    override suspend fun getLiveStream(stationId: String): ResolvedStream = withContext(Dispatchers.IO) {
        val station = getRegions()
            .asSequence()
            .flatMap { it.stations.asSequence() }
            .firstOrNull { it.id == stationId }
            ?: throw IllegalArgumentException("unknown station: $stationId")
        val auth = getAuth(station.areaId)
        val xml = getText("https://radiko.jp/v3/station/stream/pc_html5/$stationId.xml")
        val baseUrl = RadikoXmlParser.findPlaylistCreateUrl(xml, timefree = false, areafree = false)
        val lsid = UUID.randomUUID().toString().replace("-", "")

        ResolvedStream(
            playlistUrl = "$baseUrl?station_id=$stationId&l=15&lsid=$lsid&type=b",
            authToken = auth.token,
            areaId = auth.areaId,
        )
    }

    override suspend fun getTimefreePlaylistUrl(
        stationId: String,
        startTime: String,
        endTime: String,
        seekSeconds: Long,
    ): String {
        val seekTime = addSecondsToRadikoTimestamp(startTime, seekSeconds)
        return getTimefreeStream(stationId, startTime, endTime, seekTime).playlistUrl
    }

    override suspend fun getTimefreeStream(
        stationId: String,
        startTime: String,
        endTime: String,
        seekTime: String,
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val station = getRegions()
            .asSequence()
            .flatMap { it.stations.asSequence() }
            .firstOrNull { it.id == stationId }
            ?: throw IllegalArgumentException("unknown station: $stationId")
        val auth = getAuth(station.areaId)
        val xml = getText("https://radiko.jp/v3/station/stream/pc_html5/$stationId.xml")
        val baseUrl = RadikoXmlParser.findPlaylistCreateUrl(xml, timefree = true, areafree = false)
        val lsid = UUID.randomUUID().toString().replace("-", "")

        ResolvedStream(
            playlistUrl = "$baseUrl?station_id=$stationId&l=300&lsid=$lsid&type=b&start_at=$startTime&ft=$startTime&seek=$seekTime&end_at=$endTime&to=$endTime",
            authToken = auth.token,
            areaId = auth.areaId,
        )
    }

    private fun getText(url: String): String {
        val parsedUrl = URL(url)
        val connection = parsedUrl.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 YarAndroid/0.1",
        )

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("radiko request failed: HTTP $code")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getTextOrNull(url: String): String? {
        val parsedUrl = URL(url)
        val connection = parsedUrl.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 YarAndroid/0.1",
        )

        return try {
            val code = connection.responseCode
            when {
                code == 404 -> null
                code in 200..299 -> connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                else -> throw IllegalStateException("radiko request failed: HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseNoaItems(json: String): List<NoaItem> {
        val data = org.json.JSONObject(json).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val music = item.optJSONObject("music")
                val image = music?.optJSONObject("image")
                val shops = music?.optJSONObject("shops")
                val artist = item.optString("artist_name").ifBlank {
                    item.optJSONObject("artist")?.optString("name").orEmpty()
                }
                add(
                    NoaItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        artist = artist,
                        stamp = item.optString("displayed_start_time"),
                        imageUrl = sanitizeExternalUrl(image?.optString("medium").orEmpty()),
                        largeImageUrl = sanitizeExternalUrl(image?.optString("large").orEmpty()),
                        amazonUrl = sanitizeExternalUrl(shops?.optString("amazon").orEmpty()),
                        itunesUrl = sanitizeExternalUrl(shops?.optString("itunes").orEmpty()),
                        recochokuUrl = sanitizeExternalUrl(shops?.optString("recochoku").orEmpty()),
                    ),
                )
            }
        }
    }

    private fun sanitizeExternalUrl(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val parsed = URL(url)
            if (parsed.protocol == "http" || parsed.protocol == "https") parsed.toString() else ""
        }.getOrDefault("")
    }

    private fun getAuth(areaId: String): AuthResult {
        authCache[areaId]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < AUTH_CACHE_TTL_MS) {
                return cached.result
            }
        }

        val keyBytes = loadFullAuthKeyBytes()
        val device = RadikoAuthDevice.random()
        val userId = UUID.randomUUID().toString().replace("-", "")
        val appName = "aSmartPhone8"

        val auth1 = openConnection("https://radiko.jp/v2/api/auth1")
        auth1.setRequestProperty("X-Radiko-App", appName)
        auth1.setRequestProperty("X-Radiko-App-Version", device.appVersion)
        auth1.setRequestProperty("X-Radiko-Device", device.device)
        auth1.setRequestProperty("X-Radiko-User", userId)
        auth1.setRequestProperty("User-Agent", device.userAgent)

        val auth1Code = auth1.responseCode
        if (auth1Code !in 200..299) {
            auth1.disconnect()
            throw IllegalStateException("auth1 failed: HTTP $auth1Code")
        }
        val token = auth1.getHeaderField("x-radiko-authtoken")
            ?: throw IllegalStateException("auth1 did not return a token")
        val keyLength = auth1.getHeaderField("x-radiko-keylength")?.toIntOrNull() ?: 0
        val keyOffset = auth1.getHeaderField("x-radiko-keyoffset")?.toIntOrNull() ?: 0
        auth1.disconnect()

        val partialKey = Base64.getEncoder().encodeToString(keyBytes.copyOfRange(keyOffset, keyOffset + keyLength))
        val auth2 = openConnection("https://radiko.jp/v2/api/auth2")
        auth2.setRequestProperty("X-Radiko-App", appName)
        auth2.setRequestProperty("X-Radiko-App-Version", device.appVersion)
        auth2.setRequestProperty("X-Radiko-Device", device.device)
        auth2.setRequestProperty("X-Radiko-User", userId)
        auth2.setRequestProperty("X-Radiko-AuthToken", token)
        auth2.setRequestProperty("X-Radiko-Partialkey", partialKey)
        auth2.setRequestProperty("X-Radiko-Location", gpsForArea(areaId))
        auth2.setRequestProperty("X-Radiko-Connection", "wifi")
        auth2.setRequestProperty("User-Agent", device.userAgent)

        val auth2Code = auth2.responseCode
        val auth2Body = auth2.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        auth2.disconnect()
        if (auth2Code !in 200..299) {
            throw IllegalStateException("auth2 failed: HTTP $auth2Code")
        }

        return AuthResult(token = token, areaId = auth2Body.trim().split(",").firstOrNull().orEmpty())
            .also { result -> authCache[areaId] = CachedAuth(result, System.currentTimeMillis()) }
    }

    private fun openConnection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 15_000
    }

    private fun loadFullAuthKeyBytes(): ByteArray {
        val appContext = context ?: throw IllegalStateException("context is required for radiko auth")
        val source = appContext.assets.open("auth-key-data.ts").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val base64 = Regex("AUTH_KEY_BASE64\\s*=\\s*\"([^\"]+)\"").find(source)?.groupValues?.get(1)
            ?: throw IllegalStateException("auth key asset is invalid")
        return Base64.getDecoder().decode(base64)
    }

    private fun gpsForArea(areaId: String): String {
        val base = AREA_GPS[areaId] ?: AREA_GPS.getValue("JP13")
        val lat = base.first + Random.nextDouble(-0.025, 0.025)
        val lng = base.second + Random.nextDouble(-0.025, 0.025)
        return "%.6f,%.6f,gps".format(lat, lng)
    }

    private fun addSecondsToRadikoTimestamp(timestamp: String, seconds: Long): String {
        if (seconds <= 0) return timestamp
        val instant = java.time.LocalDateTime.of(
            timestamp.substring(0, 4).toInt(),
            timestamp.substring(4, 6).toInt(),
            timestamp.substring(6, 8).toInt(),
            timestamp.substring(8, 10).toInt(),
            timestamp.substring(10, 12).toInt(),
            timestamp.substring(12, 14).toInt(),
        ).atOffset(java.time.ZoneOffset.ofHours(9)).toInstant().plusSeconds(seconds)
        val jst = instant.atOffset(java.time.ZoneOffset.ofHours(9))
        return "%04d%02d%02d%02d%02d%02d".format(
            jst.year,
            jst.monthValue,
            jst.dayOfMonth,
            jst.hour,
            jst.minute,
            jst.second,
        )
    }

    private fun radikoToIsoJst(timestamp: String): String {
        return "${timestamp.substring(0, 4)}-${timestamp.substring(4, 6)}-${timestamp.substring(6, 8)}" +
            "T${timestamp.substring(8, 10)}:${timestamp.substring(10, 12)}:${timestamp.substring(12, 14)}+09:00"
    }

    private companion object {
        const val REGIONS_CACHE_TTL_MS = 60 * 60 * 1000L
        const val AUTH_CACHE_TTL_MS = 70 * 60 * 1000L

        var cachedRegions: List<Region>? = null
        var cachedRegionsTimestamp: Long = 0L
        val authCache = mutableMapOf<String, CachedAuth>()

        val AREA_GPS = mapOf(
            "JP1" to (43.064615 to 141.346807), "JP2" to (40.824308 to 140.739998),
            "JP3" to (39.703619 to 141.152684), "JP4" to (38.268837 to 140.872100),
            "JP5" to (39.718614 to 140.102364), "JP6" to (38.240436 to 140.363633),
            "JP7" to (37.750299 to 140.467551), "JP8" to (36.341811 to 140.446793),
            "JP9" to (36.565725 to 139.883565), "JP10" to (36.390668 to 139.060406),
            "JP11" to (35.856999 to 139.648849), "JP12" to (35.605057 to 140.123306),
            "JP13" to (35.689488 to 139.691706), "JP14" to (35.447507 to 139.642345),
            "JP15" to (37.902552 to 139.023095), "JP16" to (36.695291 to 137.211338),
            "JP17" to (36.594682 to 136.625573), "JP18" to (36.065178 to 136.221527),
            "JP19" to (35.664158 to 138.568449), "JP20" to (36.651299 to 138.180956),
            "JP21" to (35.391227 to 136.722291), "JP22" to (34.977120 to 138.383084),
            "JP23" to (35.180188 to 136.906565), "JP24" to (34.730283 to 136.508588),
            "JP25" to (35.004531 to 135.868590), "JP26" to (35.021247 to 135.755597),
            "JP27" to (34.686297 to 135.519661), "JP28" to (34.691269 to 135.183071),
            "JP29" to (34.685334 to 135.832742), "JP30" to (34.225987 to 135.167509),
            "JP31" to (35.503891 to 134.237736), "JP32" to (35.472295 to 133.050500),
            "JP33" to (34.661751 to 133.934406), "JP34" to (34.396560 to 132.459622),
            "JP35" to (34.185956 to 131.470649), "JP36" to (34.065718 to 134.559360),
            "JP37" to (34.340149 to 134.043444), "JP38" to (33.841624 to 132.765681),
            "JP39" to (33.559706 to 133.531079), "JP40" to (33.606576 to 130.418297),
            "JP41" to (33.249442 to 130.299794), "JP42" to (32.744839 to 129.873756),
            "JP43" to (32.789827 to 130.741667), "JP44" to (33.238172 to 131.612619),
            "JP45" to (31.911096 to 131.423893), "JP46" to (31.560146 to 130.557978),
            "JP47" to (26.212400 to 127.680932),
        )
    }
}

private data class AuthResult(val token: String, val areaId: String)

private data class CachedAuth(val result: AuthResult, val timestamp: Long)

private data class RadikoAuthDevice(
    val appVersion: String,
    val device: String,
    val userAgent: String,
) {
    companion object {
        fun random(): RadikoAuthDevice {
            val appVersion = APP_VERSIONS.random()
            val android = "15"
            val sdk = "35"
            val model = MODELS.random()
            val build = BUILDS.random()
            return RadikoAuthDevice(
                appVersion = appVersion,
                device = "$sdk.$model",
                userAgent = "Dalvik/2.1.0 (Linux; U; Android $android; $model Build/$build)",
            )
        }

        private val APP_VERSIONS = listOf("8.2.4", "8.2.2", "8.2.1", "8.2.0")
        private val MODELS = listOf("Pixel 6", "Pixel 7", "Pixel 8", "G9FPL", "GWKK3", "GQML3")
        private val BUILDS = listOf("AP4A.250105.002.B1", "AP4A.250105.002.A1", "AP4A.241205.013")
    }
}
