import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val SIM_NAV_HTTP_PORT = 9002
private const val MOCK_OAUTH_TOKEN_PROXY_PATH = "/mock-oauth/token"

fun main() {
    val simNavHttpServer = HttpServer.create(InetSocketAddress(SIM_NAV_HTTP_PORT), 0).apply {
        createContext("/") { exchange ->
            if (!tryHandleUnleashRequest(exchange)) {
                respondJson(exchange, 404, "{" + "\"error\":\"not found\"" + "}")
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
    println("Mock OAuth token proxy: GET http://localhost:$SIM_NAV_HTTP_PORT$MOCK_OAUTH_TOKEN_PROXY_PATH")

    CountDownLatch(1).await()
}
