package no.nav.helse

import org.junit.jupiter.api.Test

class GrafanaExporterTest {

    @Test
    fun `the grafana panels file should load without errors`() {
        readPanelsFromFile().fold({ throwable ->
            throw throwable
        }, { list ->
            list
        })
    }


}
