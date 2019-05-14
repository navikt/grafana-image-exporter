package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.*
import java.util.*

class GrafanaPanelIntegrationTest {

    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }


        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    @BeforeEach
    fun configure() {
        val client = create().port(server.port()).build()
        configureFor(client)
        client.resetMappings()
    }

    @AfterEach
    fun verify() {
        listAllStubMappings().mappings.forEach { mapping ->
            verify(RequestPatternBuilder.like(mapping.request))
        }
    }

    @Test
    fun `should fetch image data from grafana`() {
        val panel1 = GrafanaPanel(
                id = 1,
                name = "panel1"
        )
        val panel2 = GrafanaPanel(
                id = 2,
                name = "panel2",
                relativeTime = Duration.parse("P1D")
        )
        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = setOf(panel1, panel2)
        )

        val zoneId = ZoneId.of("UTC")
        val from = LocalDate.now().atTime(0, 0).atZone(zoneId)
        val to = LocalDateTime.now().atZone(zoneId)

        val body1 = ByteArray(512)
        Random().nextBytes(body1)
        val body2 = ByteArray(512)
        Random().nextBytes(body2)

        grafanaStub(dashboard, panel1, from, to, body1)
        grafanaStub(dashboard, panel2, from, to, body2)

        val actual = dashboard.fetchAll(server.baseUrl(), from.toLocalDateTime(), to.toLocalDateTime(), zoneId)
                .toList()

        assertEquals(2, actual.size)

        actual.first { (panel, _) ->
            panel == panel1
        }.let { (_, either) ->
            either.fold({ throwable ->
                throw throwable
            }, { imageData ->
                assertEquals(body1.size, imageData.size)
                body1.forEachIndexed { index, byte ->
                    assertEquals(byte, imageData[index])
                }
            })
        }

        actual.first { (panel, _) ->
            panel == panel2
        }.let { (_, either) ->
            either.fold({ throwable ->
                throw throwable
            }, { imageData ->
                assertEquals(body2.size, imageData.size)
                body2.forEachIndexed { index, byte ->
                    assertEquals(byte, imageData[index])
                }
            })
        }
    }

    @Test
    fun `should not fetch image data when content type is not png`() {
        val panel = GrafanaPanel(
                id = 1,
                name = "panel"
        )
        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = setOf(panel)
        )

        val zoneId = ZoneId.of("UTC")
        val from = LocalDate.now().atTime(0, 0).atZone(zoneId)
        val to = LocalDateTime.now().atZone(zoneId)

        val body = ByteArray(512)
        Random().nextBytes(body)

        grafanaStub(dashboard, panel, from, to, 200, null, body)

        val actual = dashboard.fetchAll(server.baseUrl(), from.toLocalDateTime(), to.toLocalDateTime(), zoneId)

        actual.forEach { (_, either) ->
            either.map {
                fail { "expected request to fail" }
            }
        }
    }

    @Test
    fun `should not follow redirects`() {
        val panel = GrafanaPanel(
                id = 1,
                name = "panel"
        )
        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = setOf(panel)
        )

        val zoneId = ZoneId.of("UTC")
        val from = LocalDate.now().atTime(0, 0).atZone(zoneId)
        val to = LocalDateTime.now().atZone(zoneId)

        grafanaStub(dashboard, panel, from, to, 302, null, ByteArray(0))

        val actual = dashboard.fetchAll(server.baseUrl(), from.toLocalDateTime(), to.toLocalDateTime(), zoneId)

        actual.forEach { (_, either) ->
            either.map {
                fail { "expected request to fail" }
            }
        }
    }

    private fun grafanaStub(dashboard: GrafanaDashboard, panel: GrafanaPanel, from: ZonedDateTime, to: ZonedDateTime, body: ByteArray) =
            grafanaStub(dashboard, panel, from, to, 200, "image/png", body)

    private fun grafanaStub(dashboard: GrafanaDashboard, panel: GrafanaPanel, from: ZonedDateTime, to: ZonedDateTime, statusCode: Int, contentType: String?, body: ByteArray) =
            stubFor(get(urlPathEqualTo("/render/d-solo/${dashboard.id}/${panel.name}"))
                    .withQueryParam("panelId", equalTo("${panel.id}"))
                    .withQueryParam("from", equalTo("${(panel.relativeTime?.let { from.minus(panel.relativeTime) } ?: from).toInstant().toEpochMilli()}"))
                    .withQueryParam("to", equalTo("${to.toInstant().toEpochMilli()}"))
                    .withQueryParam("tz", equalTo("UTC"))
                    .willReturn(aResponse()
                            .withStatus(statusCode).also { responseBuilder ->
                                if (contentType != null) {
                                    responseBuilder.withHeader("Content-Type", contentType)
                                }
                            }
                            .withBody(body)))
}
