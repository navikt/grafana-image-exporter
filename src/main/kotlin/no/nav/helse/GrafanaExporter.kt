package no.nav.helse

import arrow.core.Try
import com.github.kittinunf.fuel.core.FuelManager
import io.ktor.application.Application
import io.ktor.application.log
import io.ktor.util.KtorExperimentalAPI
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.MDC
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

    FuelManager.instance.addRequestInterceptor { next ->
        { request ->
            log.info("fetching ${request.url}")

            request.allowRedirects(false)

            next(request)
        }
    }
    FuelManager.instance.addResponseInterceptor { next ->
        { request, response ->
            try {
                MDC.put("url", "${response.url}")
                log.info("response status=${response.statusCode}")
            } finally {
                MDC.remove("url")
            }
            next(request, response)
        }
    }

    timer(
            name = "export-loop",
            daemon = true,
            period = TimeUnit.SECONDS.toMillis(60)) {

        dashboards.forEach { dashboard ->
            dashboard.fetchAll(
                    baseUrl = environment.config.property("grafanaBaseUrl").getString(),
                    from = LocalDate.now().atTime(0, 0),
                    to = LocalDateTime.now(),
                    zoneId = ZoneId.of("Europe/Oslo")
            ).forEach { either ->
                either.fold({ error ->
                    try {
                        MDC.put("url", "${error.response.url}")
                        log.info("failed with status ${error.response.statusCode}: ${error.message}", error.exception)
                    } finally {
                        MDC.remove("url")
                    }
                    null
                }, { (panel, imageData) ->
                    log.info("received ${imageData.size} bytes")
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

