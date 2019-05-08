package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
    }

    @AfterEach
    fun verify() {
        listAllStubMappings().mappings.forEach { mapping ->
            verify(RequestPatternBuilder.like(mapping.request))
        }
    }

    @Test
    fun `should fetch image data from grafana`() {
        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = listOf(
                        GrafanaPanel(
                                id = 1
                        )
                )
        )

        val zoneId = ZoneId.of("UTC")
        val from = LocalDate.now().atTime(0, 0).atZone(zoneId)
        val to = LocalDateTime.now().atZone(zoneId)

        val body = ByteArray(512)
        Random().nextBytes(body)

        val mapping = get(urlPathEqualTo("/render/d-solo/${dashboard.id}/${dashboard.panels[0].panelName}"))
                .withQueryParam("panelId", equalTo("${dashboard.panels[0].id}"))
                .withQueryParam("from", equalTo("${from.toInstant().toEpochMilli()}"))
                .withQueryParam("to", equalTo("${to.toInstant().toEpochMilli()}"))
                .withQueryParam("tz", equalTo("UTC"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "image/png")
                        .withBody(body))
        stubFor(mapping)

        val actual = dashboard.fetchAll(server.baseUrl(), from.toLocalDateTime(), to.toLocalDateTime(), zoneId)

        actual.forEach {
            it.fold({ throwable ->
                throw throwable
            }, { (_, imageData) ->
                assertEquals(body.size, imageData.size)
                body.forEachIndexed { index, byte ->
                    assertEquals(byte, imageData[index])
                }
            })
        }
    }

    @Test
    fun `should not fetch image data when content type is not png`() {
        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = listOf(
                        GrafanaPanel(
                                id = 1
                        )
                )
        )

        val zoneId = ZoneId.of("UTC")
        val from = LocalDate.now().atTime(0, 0).atZone(zoneId)
        val to = LocalDateTime.now().atZone(zoneId)

        val body = ByteArray(512)
        Random().nextBytes(body)

        val mapping = get(urlPathEqualTo("/render/d-solo/${dashboard.id}/${dashboard.panels[0].panelName}"))
                .withQueryParam("panelId", equalTo("${dashboard.panels[0].id}"))
                .withQueryParam("from", equalTo("${from.toInstant().toEpochMilli()}"))
                .withQueryParam("to", equalTo("${to.toInstant().toEpochMilli()}"))
                .withQueryParam("tz", equalTo("UTC"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(body))
        stubFor(mapping)

        val actual = dashboard.fetchAll(server.baseUrl(), from.toLocalDateTime(), to.toLocalDateTime(), zoneId)

        actual.forEach {
            it.map {
                fail { "expected request to fail" }
            }
        }
    }

    @Test
    fun `should not follow redirects`() {
        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = listOf(
                        GrafanaPanel(
                                id = 1
                        )
                )
        )

        val zoneId = ZoneId.of("UTC")
        val from = LocalDate.now().atTime(0, 0).atZone(zoneId)
        val to = LocalDateTime.now().atZone(zoneId)

        val mapping = get(urlPathEqualTo("/render/d-solo/${dashboard.id}/${dashboard.panels[0].panelName}"))
                .withQueryParam("panelId", equalTo("${dashboard.panels[0].id}"))
                .withQueryParam("from", equalTo("${from.toInstant().toEpochMilli()}"))
                .withQueryParam("to", equalTo("${to.toInstant().toEpochMilli()}"))
                .withQueryParam("tz", equalTo("UTC"))
                .willReturn(aResponse()
                        .withStatus(302))
        stubFor(mapping)

        val actual = dashboard.fetchAll(server.baseUrl(), from.toLocalDateTime(), to.toLocalDateTime(), zoneId)

        actual.forEach {
            it.map {
                fail { "expected request to fail" }
            }
        }
    }
}
