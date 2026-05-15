import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val SIM_NAV_HTTP_PORT = 9002

fun main() {
    val simNavHttpServer = HttpServer.create(InetSocketAddress(SIM_NAV_HTTP_PORT), 0).apply {
        createContext("/") { exchange ->
            if (!tryHandleUnleashRequest(exchange) && !tryHandlePoaoTilgangRequest(exchange)) {
                respondJson(exchange, 404, "{\"error\":\"not found\"}")
            }
        }
        executor = Executors.newCachedThreadPool()
        start()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            simNavHttpServer.stop(0)
        },
    )

    println("Sim-nav HTTP stub started on port $SIM_NAV_HTTP_PORT")
    println("Set UNLEASH_SERVER_API_URL=http://localhost:$SIM_NAV_HTTP_PORT$UNLEASH_PATH_PREFIX/api and UNLEASH_SERVER_API_TOKEN=dummy")
    println("Set POAO_TILGANG_URL=http://localhost:$SIM_NAV_HTTP_PORT$POAO_TILGANG_PATH_PREFIX")

    CountDownLatch(1).await()
}


