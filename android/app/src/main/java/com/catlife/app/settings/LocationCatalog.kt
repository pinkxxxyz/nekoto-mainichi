package com.catlife.app.settings

import java.io.Reader

data class WeatherLocation(
    val prefectureCode: String,
    val prefectureName: String,
    val municipalityCode: String,
    val municipalityName: String,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String get() = "$prefectureName $municipalityName"
}

data class Prefecture(val code: String, val name: String)

data class RegionSelectionDraft(val prefectureCode: String? = null, val municipalityCode: String? = null) {
    fun selectPrefecture(code: String) = RegionSelectionDraft(code)
    fun selection(catalog: LocationCatalog): WeatherLocation? =
        if (prefectureCode == null || municipalityCode == null) null else catalog.find(prefectureCode, municipalityCode)
}

class LocationCatalog private constructor(private val locations: List<WeatherLocation>) {
    val prefectures = locations.map { Prefecture(it.prefectureCode, it.prefectureName) }.distinct()
    private val byMunicipality = locations.associateBy { it.municipalityCode }
    private val byPrefecture = locations.groupBy { it.prefectureCode }
    private val byDisplayName = locations.groupBy { it.displayName }

    fun find(prefectureCode: String, municipalityCode: String): WeatherLocation? =
        byMunicipality[municipalityCode]?.takeIf { it.prefectureCode == prefectureCode }

    fun findDisplayName(name: String?): WeatherLocation? = byDisplayName[name]?.singleOrNull()

    fun searchMunicipalities(prefectureCode: String, query: String = ""): List<WeatherLocation> =
        byPrefecture[prefectureCode].orEmpty().filter { it.municipalityName.contains(query.trim(), ignoreCase = true) }

    companion object {
        fun read(reader: Reader): LocationCatalog {
            val rows = reader.buffered().lineSequence().filter { it.isNotBlank() }.map { line ->
                val columns = line.split('\t')
                require(columns.size == 6)
                WeatherLocation(columns[0], columns[1], columns[2], columns[3], columns[4].toDouble(), columns[5].toDouble()).also {
                    require(it.prefectureCode.matches(Regex("[0-9]{6}")) && it.municipalityCode.matches(Regex("[0-9]{6}")))
                    require(it.prefectureName.isNotBlank() && it.municipalityName.isNotBlank())
                    require(it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0)
                }
            }.toList()
            require(rows.isNotEmpty() && rows.map { it.municipalityCode }.distinct().size == rows.size)
            require(rows.groupBy { it.prefectureCode }.all { (_, entries) -> entries.map { it.prefectureName }.distinct().size == 1 })
            return LocationCatalog(rows)
        }
    }
}
