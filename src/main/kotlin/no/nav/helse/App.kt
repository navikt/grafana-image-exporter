package no.nav.helse

import arrow.core.Try
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngineEnvironmentBuilder
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
fun main() {
    val env = System.getenv()

    embeddedServer(Netty, createApplicationEnvironment(env)).let { app ->
        app.start(wait = false)

        Runtime.getRuntime().addShutdownHook(Thread {
            app.stop(1, 5, TimeUnit.SECONDS)
        })
    }
}

@KtorExperimentalAPI
fun createApplicationEnvironment(env: Map<String, String>) = applicationEngineEnvironment {
    env.configureApplicationEnvironment(this)

    connector {
        port = 8080
    }

    module {
        nais()
        grafanaExporter(readPanelsFromFile().fold({ throwable ->
            throw throwable
        }, { list ->
            list
        }))
    }
}

@KtorExperimentalAPI
fun Map<String, String>.configureApplicationEnvironment(builder: ApplicationEngineEnvironmentBuilder) = builder.apply {
    with(config as MapApplicationConfig) {
        put("grafanaBaseUrl", this@configureApplicationEnvironment.getValue("GRAFANA_BASE_URL"))

        put("kafka.app-id", "grafana-image-exporter-v1")
        put("kafka.bootstrap-servers", this@configureApplicationEnvironment.getValue("KAFKA_BOOTSTRAP_SERVERS"))
        this@configureApplicationEnvironment["KAFKA_USERNAME"]?.let { put("kafka.username", it) }
        this@configureApplicationEnvironment["KAFKA_PASSWORD"]?.let { put("kafka.password", it) }

        this@configureApplicationEnvironment["NAV_TRUSTSTORE_PATH"]?.let { put("kafka.truststore-path", it) }
        this@configureApplicationEnvironment["NAV_TRUSTSTORE_PASSWORD"]?.let { put("kafka.truststore-password", it) }
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
