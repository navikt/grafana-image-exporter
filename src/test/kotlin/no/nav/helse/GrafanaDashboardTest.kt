package no.nav.helse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class GrafanaDashboardTest {

    @Test
    fun `letters and numbers should be ok`() {
        val dashboard = GrafanaDashboard(
                id = "asdfASDF1234",
                panels = emptyList()
        )

        assertEquals("asdfASDF1234", dashboard.id)
    }

    @Test
    fun `bad IDs are not allowed`() {
        val badNames = listOf("asdf_1234", "asdf-1243",
                "asdf/1234", "asdf?1234", "asdf&1234", "asdf=1234")
        badNames.forEach { id ->
            try {
                GrafanaDashboard(
                        id = id,
                        panels = emptyList()
                )
            } catch (err: IllegalArgumentException) {
                // ok
            } catch (err: Exception) {
                fail<Exception>(err)
            }
        }
    }
}
