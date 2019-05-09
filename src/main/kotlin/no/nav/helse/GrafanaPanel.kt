package no.nav.helse

import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

data class GrafanaDashboard(val id: String, val panels: Set<GrafanaPanel>) {
    private val lettersAndNumbers = Regex("^[A-Za-z0-9]+$")
    init {
        if (!lettersAndNumbers.matches(id)) {
            throw IllegalArgumentException("invalid dashboardId: $id")
        }
    }
}

fun GrafanaDashboard.fetchAll(baseUrl: String, from: LocalDateTime, to: LocalDateTime, zoneId: ZoneId) =
        panels.map { panel ->
            panel.fetch(baseUrl, id, from, to, zoneId).map {
                panel to it
            }
        }

data class GrafanaPanel(val id: Int, val name: String) {
    private val lettersNumbersUnderscoreAndHyphen = Regex("^[A-Za-z0-9_-]+$")

    init {
        if (!lettersNumbersUnderscoreAndHyphen.matches(name)) {
            throw IllegalArgumentException("invalid panel name: $name")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GrafanaPanel

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

private fun Response.contentType() =
        headers.keys.firstOrNull { key ->
            key.toLowerCase() == "content-type"
        }?.let(headers::get)

private fun Response.isPNG() =
        contentType()?.let { values ->
            values.any { value ->
                value.toLowerCase() == "image/png"
            }
        } ?: false

private fun GrafanaPanel.fetch(baseUrl: String, dashboardId: String, from: LocalDateTime, to: LocalDateTime, zoneId: ZoneId) =
        Fuel.get("$baseUrl/render/d-solo/$dashboardId/$name" +
                "?panelId=$id" +
                "&from=${from.atZone(zoneId).toInstant().toEpochMilli()}" +
                "&to=${to.atZone(zoneId).toInstant().toEpochMilli()}" +
                "&tz=${zoneId.id}").let { request ->
            val (_, response, result) = request.response()

            when {
                response.statusCode != 200 -> result
                response.isPNG() -> result
                else -> com.github.kittinunf.result.Result.Failure(FuelError(
                        exception = IOException("expected a image/png, got ${response.contentType()}"),
                        response = response))
            }
        }.fold({ imageData ->
            imageData.right()
        }, { error ->
            error.left()
        })
