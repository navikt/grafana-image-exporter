package no.nav.helse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class GrafanaDashboardTest {

    @Test
    fun `letters and numbers should be ok`() {
        val dashboard = GrafanaDashboard(
                id = "asdfASDF1234",
                panels = emptySet()
        )

        assertEquals("asdfASDF1234", dashboard.id)
    }

    @Test
    fun `duplicate panels are not allowed`() {
        val panel1 = GrafanaPanel(
                id = 1,
                name = "test-panel"
        )
        val panel2 = GrafanaPanel(
                id = 2,
                name = "test-panel"
        )

        val dashboard = GrafanaDashboard(
                id = "asdf",
                panels = setOf(panel1, panel2)
        )

        assertEquals(1, dashboard.panels.size)
    }

    @Test
    fun `bad IDs are not allowed`() {
        val badNames = listOf("asdf_1234", "asdf-1243",
                "asdf/1234", "asdf?1234", "asdf&1234", "asdf=1234", "asdf:1234")
        badNames.forEach { id ->
            try {
                GrafanaDashboard(
                        id = id,
                        panels = emptySet()
                )
            } catch (err: IllegalArgumentException) {
                // ok
            } catch (err: Exception) {
                fail<Exception>(err)
            }
        }
    }
}
