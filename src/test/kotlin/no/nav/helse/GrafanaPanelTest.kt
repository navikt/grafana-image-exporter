package no.nav.helse

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GrafanaPanelTest {

    @Test
    fun `panels with same name should be equal`() {
        val panel1 = GrafanaPanel(
                id = 1,
                name = "test-panel"
        )
        val panel2 = GrafanaPanel(
                id = 2,
                name = "test-panel"
        )

        assertEquals(panel1, panel2)
    }

    @Test
    fun `bad names are not allowed`() {
        val badNames = listOf("asdf/1234", "asdf?1234", "asdf&1234", "asdf=1234", "asdf:1234")
        badNames.forEach { name ->
            try {
                GrafanaPanel(
                        id = 1,
                        name = name
                )
            } catch (err: IllegalArgumentException) {
                // ok
            } catch (err: Exception) {
                Assertions.fail<Exception>(err)
            }
        }
    }
}
