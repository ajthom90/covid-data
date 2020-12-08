package dev.ajthom90.wayback

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.serialization.responseObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Paths
import java.time.*
import java.time.format.DateTimeFormatter

val ymdFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
val fullFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

val waybackRegex = "<!-- BEGIN WAYBACK TOOLBAR INSERT -->.*<!-- END WAYBACK TOOLBAR INSERT -->".toRegex()

fun main(args: Array<String>) {
	val situationUrl = "www.health.state.mn.us/diseases/coronavirus/situation.html"
	var date = LocalDate.of(2020, Month.MARCH, 4).minusDays(1L)
	while (date < LocalDate.now()) {
		val dateString = date.atTime(6, 0, 0).format(ymdFormatter)
		val htmlPath = Paths.get("../${date.format(ymdFormatter)}.html")
		val html = if (Files.exists(htmlPath)) {
			Files.readAllBytes(htmlPath).decodeToString()
		} else {
			val apiUrl = "https://archive.org/wayback/available?url=$situationUrl&timestamp=$dateString"
			val (_, _, result) = apiUrl.httpGet().responseObject<Wrapper>()
			val wrapper = result.get()
			val url = wrapper.archivedSnapshots.closest.url
			val html = url.httpGet().responseString().third.get().replace(waybackRegex, "")
			Files.write(htmlPath, html.toByteArray())
			html
		}
		val doc = Jsoup.parse(html)
		val updatedDate = doc.select("strong").firstOrNull { it.text().contains("Updated ", ignoreCase = true) || it.text().contains("As of ", ignoreCase = true) } ?: date.format(ymdFormatter)
		val mapTable = if (date >= LocalDate.of(2020, Month.APRIL, 10)) {
			doc.select("div#map").select("table").firstOrNull()
		} else if (date >= LocalDate.of(2020, Month.APRIL, 5)) {
			doc.select("table").first {
				it.select("tr").first().text().contains("county", ignoreCase = true)
			}
		} else if (date < LocalDate.of(2020, Month.APRIL, 5)) {
			null
		} else {
			null
		}
		if (mapTable != null) {
			val bec = mapTable.select("tr").firstOrNull { it.text().contains(args[0], ignoreCase = true) }
			if (bec != null) {
				val (totalCases, deaths) = if (date >= LocalDate.of(2020, Month.OCTOBER, 14)) {
					val totalCases = bec.select("td")[3].text().replace(",", "").toInt()
					val deaths = bec.select("td")[4].text().replace(",", "").toInt()
					totalCases to deaths
				} else if (date < LocalDate.of(2020, Month.OCTOBER, 14) && date >= LocalDate.of(2020, Month.APRIL, 6)) {
					val totalCases = bec.select("td")[1].text().replace(",", "").toInt()
					val deaths = bec.select("td")[2].text().replace(",", "").toInt()
					totalCases to deaths
				} else if (date <= LocalDate.of(2020, Month.APRIL, 5)) {
					bec.select("td")[1].text().replace(",", "").toInt() to 0
				} else {
					0 to 0
				}
				println("$updatedDate: $totalCases; $deaths")
			}
		}
		date = date.plusDays(1L)
	}
}

@Serializable
class Wrapper(@SerialName("archived_snapshots") val archivedSnapshots: ArchivedSnapshots, val url: String, val timestamp: String)

@Serializable
class ArchivedSnapshots(val closest: Closest)

@Serializable
class Closest(val available: Boolean, val url: String, val timestamp: String, val status: String) {
	val localDateTime: LocalDateTime
		get() {
			return LocalDateTime.parse(timestamp, fullFormatter)
		}
}

data class Result(val dateString: String, val becCases: Int, val becDeaths: Int)


