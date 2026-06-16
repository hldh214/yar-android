package dev.yar.android.data

import dev.yar.android.domain.Region
import dev.yar.android.domain.Station
import dev.yar.android.domain.Program

internal object RadikoXmlParser {
    fun parseStationsXml(xml: String): List<Region> {
        val stationGroups = Regex("<stations\\b[^>]*>[\\s\\S]*?</stations>", RegexOption.IGNORE_CASE)
            .findAll(xml)

        return stationGroups.map { group ->
            val block = group.value
            val regionId = block.attribute("region_id")
            val regionName = block.attribute("region_name")
            val stations = Regex("<station>[\\s\\S]*?</station>", RegexOption.IGNORE_CASE)
                .findAll(block)
                .map { stationMatch -> parseStation(stationMatch.value) }
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .toList()

            Region(
                id = regionId,
                name = regionName.ifBlank { regionId },
                stations = stations,
            )
        }.filter { it.id.isNotBlank() && it.stations.isNotEmpty() }
            .toList()
    }

    fun findPlaylistCreateUrl(
        xml: String,
        timefree: Boolean,
        areafree: Boolean,
    ): String {
        val timefreeValue = if (timefree) "1" else "0"
        val areafreeValue = if (areafree) "1" else "0"
        val urlBlocks = Regex("<url\\b[^>]*>[\\s\\S]*?</url>", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { it.value }

        val block = urlBlocks.firstOrNull { candidate ->
            val tag = Regex("<url\\b[^>]*>", RegexOption.IGNORE_CASE).find(candidate)?.value.orEmpty()
            tag.contains("timefree=\"$timefreeValue\"", ignoreCase = true) &&
                tag.contains("areafree=\"$areafreeValue\"", ignoreCase = true)
        } ?: throw IllegalStateException("no matching stream URL found")

        return block.textContent("playlist_create_url")
            .ifBlank { throw IllegalStateException("no playlist_create_url found") }
    }

    fun parseProgramsXml(xml: String, stationId: String): List<Program> {
        val stationBlock = Regex("<station\\b[^>]*id=\"$stationId\"[^>]*>[\\s\\S]*?</station>", RegexOption.IGNORE_CASE)
            .find(xml)
            ?.value
            ?: return emptyList()
        val now = formatDateToRadiko(System.currentTimeMillis())

        return Regex("<prog\\b[^>]*>[\\s\\S]*?</prog>", RegexOption.IGNORE_CASE)
            .findAll(stationBlock)
            .map { match ->
                val block = match.value
                val startTime = block.attribute("ft")
                val endTime = block.attribute("to")
                val duration = block.attribute("dur").toLongOrNull() ?: 0L
                Program(
                    id = "${stationId}_$startTime",
                    stationId = stationId,
                    title = block.textContent("title"),
                    subtitle = block.textContent("sub_title"),
                    performer = block.textContent("pfm"),
                    description = block.textContent("desc").htmlToPlainText(),
                    info = block.textContent("info").htmlToPlainText(),
                    url = block.textContent("url").sanitizeExternalUrl(),
                    startTime = startTime,
                    endTime = endTime,
                    imageUrl = block.textContent("img").ifBlank { null },
                    isOnAir = startTime <= now && now < endTime,
                    isTimefree = isTimefree(endTime),
                    durationSeconds = duration,
                )
            }
            .filter { it.startTime.isNotBlank() && it.endTime.isNotBlank() }
            .toList()
    }

    fun formatBroadcastDateJst(nowMillis: Long = System.currentTimeMillis()): String {
        val jst = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(java.time.ZoneOffset.UTC)
            .plusHours(9)
        val broadcastDay = if (jst.hour < 5) jst.minusDays(1) else jst
        return "%04d%02d%02d".format(broadcastDay.year, broadcastDay.monthValue, broadcastDay.dayOfMonth)
    }

    private fun formatDateToRadiko(nowMillis: Long): String {
        val jst = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(java.time.ZoneOffset.UTC)
            .plusHours(9)
        return "%04d%02d%02d%02d%02d%02d".format(
            jst.year,
            jst.monthValue,
            jst.dayOfMonth,
            jst.hour,
            jst.minute,
            jst.second,
        )
    }

    private fun isTimefree(endTime: String): Boolean {
        val end = runCatching { parseRadikoInstant(endTime) }.getOrNull() ?: return false
        val now = java.time.Instant.now()
        return end.isBefore(now) && end.isAfter(now.minusSeconds(7 * 24 * 60 * 60))
    }

    private fun parseRadikoInstant(timestamp: String): java.time.Instant = java.time.LocalDateTime.of(
        timestamp.substring(0, 4).toInt(),
        timestamp.substring(4, 6).toInt(),
        timestamp.substring(6, 8).toInt(),
        timestamp.substring(8, 10).toInt(),
        timestamp.substring(10, 12).toInt(),
        timestamp.substring(12, 14).toInt(),
    ).atOffset(java.time.ZoneOffset.ofHours(9)).toInstant()

    private fun parseStation(block: String): Station {
        val id = block.textContent("id")
        val logoUrl = Regex("<logo\\b[^>]*width=\"128\"[^>]*>([^<]*)</logo>", RegexOption.IGNORE_CASE)
            .find(block)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.decodeXmlEntities()
            .orEmpty()

        return Station(
            id = id,
            name = block.textContent("name"),
            asciiName = block.textContent("ascii_name"),
            areaId = block.textContent("area_id"),
            logoUrl = logoUrl.ifBlank { "https://radiko.jp/v2/static/station/logo/$id/lrtrim/688x160.png" },
        )
    }

    private fun String.attribute(name: String): String = Regex("$name=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.decodeXmlEntities()
        .orEmpty()

    private fun String.textContent(tag: String): String = Regex("<$tag\\b[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.removeSurrounding("<![CDATA[", "]]>")
        ?.decodeXmlEntities()
        .orEmpty()

    private fun String.decodeXmlEntities(): String = this
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toInt().toChar().toString() }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match -> match.groupValues[1].toInt(16).toChar().toString() }

    private fun String.htmlToPlainText(): String = decodeXmlEntities()
        .replace(Regex("<\\s*br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(p|div|li|section|article|h\\d)>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "- ")
        .replace(Regex("<[^>]+>"), "")
        .replace("\r\n", "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun String.sanitizeExternalUrl(): String {
        if (isBlank()) return ""
        return runCatching {
            val parsed = java.net.URL(this)
            if (parsed.protocol == "http" || parsed.protocol == "https") parsed.toString() else ""
        }.getOrDefault("")
    }
}
