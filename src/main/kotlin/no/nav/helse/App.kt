package no.nav.helse

import io.ktor.config.MapApplicationConfig
import io.ktor.server.engine.ApplicationEngineEnvironmentBuilder
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
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
        grafanaExporter()
    }
}

@KtorExperimentalAPI
fun Map<String, String>.configureApplicationEnvironment(builder: ApplicationEngineEnvironmentBuilder) = builder.apply {
    with(config as MapApplicationConfig) {
        put("grafanaBaseUrl", this@configureApplicationEnvironment.getValue("GRAFANA_BASE_URL"))
    }
}
