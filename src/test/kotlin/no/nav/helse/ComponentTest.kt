package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.config.MapApplicationConfig
import io.ktor.server.engine.connector
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.withApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class ComponentTest {
    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        private val embeddedEnvironment = KafkaEnvironment(
                users = listOf(JAASCredential(username, password)),
                autoStart = false,
                withSchemaRegistry = false,
                withSecurity = true,
                topics = listOf(exportedPanelsTopic)
        )

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
            embeddedEnvironment.start()
        }


        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
            embeddedEnvironment.tearDown()
        }
    }

    @BeforeEach
    fun configure() {
        val client = WireMock.create().port(server.port()).build()
        WireMock.configureFor(client)
        client.resetMappings()
    }

    @Test
    @KtorExperimentalAPI
    fun `should put image on topic`() {
        val panel = GrafanaPanel(1)
        val dashboard = GrafanaDashboard("asdf", listOf(panel))

        val expectedImage = testImage()

        grafanaStub(dashboard, panel, expectedImage)

        withApplication(
                environment = createTestEnvironment {
                    with (config as MapApplicationConfig) {
                        put("grafanaBaseUrl", server.baseUrl())
                        put("kafka.bootstrap-servers", embeddedEnvironment.brokersURL)
                        put("kafka.username", username)
                        put("kafka.password", password)
                    }

                    connector {
                        port = 8080
                    }

                    module {
                        grafanaExporter(listOf(dashboard))
                    }
                }) {
            val actual = consumeOneMessage()

            assertEquals("${dashboard.id}:${panel.id}", actual.key())
            assertEquals(expectedImage.size, actual.value().size)

            expectedImage.forEachIndexed { index, byte ->
                assertEquals(byte, actual.value()[index])
            }
        }
    }

    private fun grafanaStub(dashboard: GrafanaDashboard, panel: GrafanaPanel, image: ByteArray) {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/render/d-solo/${dashboard.id}/${panel.panelName}"))
                .withQueryParam("panelId", WireMock.equalTo("${panel.id}"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "image/png")
                        .withBody(image)))
    }

    private fun testImage() =
            ComponentTest::class.java.classLoader.getResource("test.png").readBytes()

    private fun consumeOneMessage(timeout: Long = 10000L): ConsumerRecord<String, ByteArray> {
        val resultConsumer = KafkaConsumer<String, ByteArray>(consumerProperties(), StringDeserializer(), ByteArrayDeserializer())
        resultConsumer.subscribe(listOf(exportedPanelsTopic))

        val end = System.currentTimeMillis() + timeout

        while (System.currentTimeMillis() < end) {
            resultConsumer.seekToBeginning(resultConsumer.assignment())
            val records = resultConsumer.poll(Duration.ofSeconds(1))

            if (!records.isEmpty) {
                return records.records(exportedPanelsTopic).first()
            }
        }

        throw RuntimeException("waited ${timeout/1000} seconds without getting any records")
    }

    private fun consumerProperties(): MutableMap<String, Any>? {
        return HashMap<String, Any>().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedEnvironment.brokersURL)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";")
            put(ConsumerConfig.GROUP_ID_CONFIG, "grafana-image-exporter-consumer")
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1")
        }
    }
}
