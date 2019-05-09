package no.nav.helse

import com.github.kittinunf.fuel.core.FuelManager
import io.ktor.application.Application
import io.ktor.application.log
import io.ktor.util.KtorExperimentalAPI
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.MDC
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

internal const val exportedPanelsTopic = "aapen-grafana-paneler-v1"

@KtorExperimentalAPI
fun Application.grafanaExporter(dashboards: List<GrafanaDashboard>) {
    val kafkaProducer = KafkaProducer<String, ByteArray>(kafkaConfig(), StringSerializer(), ByteArraySerializer())

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
            try {
                MDC.put("dashboardId", dashboard.id)
                dashboard.fetchAll(
                        baseUrl = environment.config.property("grafanaBaseUrl").getString(),
                        from = LocalDate.now().atTime(0, 0),
                        to = LocalDateTime.now(),
                        zoneId = ZoneId.of("Europe/Oslo")
                ).forEach { (panel, either) ->
                    try {
                        MDC.put("panelId", "${panel.id}")
                        MDC.put("panelName", panel.name)

                        either.fold({ error ->
                            try {
                                MDC.put("url", "${error.response.url}")
                                log.info("failed with status ${error.response.statusCode}: ${error.message}", error.exception)
                            } finally {
                                MDC.remove("url")
                            }
                            null
                        }, { imageData ->
                            log.info("received ${imageData.size} bytes")
                            recordMapper(dashboard, panel, imageData)
                        })?.let { record ->
                            kafkaProducer.send(record)
                        }
                    } finally {
                        MDC.remove("panelName")
                        MDC.remove("panelId")
                    }
                }
            } finally {
                MDC.remove("dashboardId")
            }
        }
    }
}

fun recordMapper(dashboard: GrafanaDashboard, panel: GrafanaPanel, imageData: ByteArray) =
        ProducerRecord(exportedPanelsTopic, "${dashboard.id}:${panel.name}", imageData)

@KtorExperimentalAPI
private fun Application.kafkaConfig() = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.config.property("kafka.bootstrap-servers").getString())

    put(SaslConfigs.SASL_MECHANISM, "PLAIN")
    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")

    environment.config.propertyOrNull("kafka.username")?.getString()?.let { username ->
        environment.config.propertyOrNull("kafka.password")?.getString()?.let { password ->
            put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";")
        }
    }

    environment.config.propertyOrNull("kafka.truststore-path")?.getString()?.let { truststorePath ->
        environment.config.propertyOrNull("kafka.truststore-password")?.getString().let { truststorePassword ->
            try {
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(truststorePath).absolutePath)
                put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword)
                log.info("Configured '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location ")
            } catch (ex: Exception) {
                log.error("Failed to set '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location", ex)
            }
        }
    }
}

