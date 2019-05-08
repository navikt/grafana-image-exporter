package no.nav.helse

import arrow.core.Try
import io.ktor.application.Application
import io.ktor.application.log
import io.ktor.util.KtorExperimentalAPI
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

@KtorExperimentalAPI
fun Application.grafanaExporter() {
    val dashboards = readPanelsFromFile().fold({ throwable ->
        throw throwable
    }, { list ->
        list
    })

    timer(
            name = "export-loop",
            daemon = true,
            period = TimeUnit.SECONDS.toMillis(60)) {

        log.info("fetching panels")
        dashboards.forEach { dashboard ->
            dashboard.fetchAll(
                    baseUrl = environment.config.property("grafanaBaseUrl").getString(),
                    from = LocalDate.now().atTime(0, 0),
                    to = LocalDateTime.now(),
                    zoneId = ZoneId.of("Europe/Oslo")
            ).forEach { either ->
                either.fold({ error ->
                    log.info("Fetching ${error.response.url} failed with " +
                            "status ${error.response.statusCode}: ${error.message}", error.exception)
                    null
                }, { (panel, imageData) ->
                    log.info("fetched image of ${imageData.size} bytes")
                })
            }
        }
    }
}

fun readPanelsFromFile() =
        Try {
            Application::class.java.classLoader.getResource("grafana-panels.json").readText().let { contents ->
                JSONArray(contents)
            }.map { item ->
                with(item as JSONObject) {
                    GrafanaDashboard(
                            id = getString("id"),
                            panels = getJSONArray("panels").map { panel ->
                                with (panel as JSONObject) {
                                    GrafanaPanel(
                                            id = getInt("id")
                                    )
                                }
                            }
                    )
                }
            }
        }

