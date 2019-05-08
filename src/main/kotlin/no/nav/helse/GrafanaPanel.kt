package no.nav.helse

import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.Fuel
import java.time.LocalDateTime
import java.time.ZoneId

data class GrafanaDashboard(val id: String, val panels: List<GrafanaPanel>) {
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

data class GrafanaPanel(val id: Int) {
    val panelName = "panel"
}

private fun GrafanaPanel.fetch(baseUrl: String, dashboardId: String, from: LocalDateTime, to: LocalDateTime, zoneId: ZoneId) =
        Fuel.get("$baseUrl/render/d-solo/${dashboardId}/${panelName}" +
                "?panelId=${id}" +
                "&from=${from.atZone(zoneId).toInstant().toEpochMilli()}" +
                "&to=${to.atZone(zoneId).toInstant().toEpochMilli()}" +
                "&tz=${zoneId.id}").let { request ->
            request.response().third
        }.fold({ imageData ->
            imageData.right()
        }, { error ->
            error.left()
        })
