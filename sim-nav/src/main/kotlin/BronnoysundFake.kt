import com.sun.net.httpserver.HttpExchange
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

const val BRONNOYSUND_PATH_PREFIX = "/brreg"

private const val ORGNUMMER = "810000001"
private const val OVERORDNET_ENHET = "810000000"

fun tryHandleBronnoysundRequest(exchange: HttpExchange): Boolean {
    val path = exchange.requestURI.path

    if (!path.startsWith(BRONNOYSUND_PATH_PREFIX)) {
        return false
    }

    if (exchange.requestMethod != "GET") {
        respondJson(exchange, 405, "{\"error\":\"method not allowed\"}")
        return true
    }

    return when (path.removePrefix(BRONNOYSUND_PATH_PREFIX)) {
        "/enhetsregisteret/api/oppdateringer/enheter" -> {
            respondJson(exchange, 200, moderenhetOppdateringerJson())
            true
        }

        "/enhetsregisteret/api/oppdateringer/underenheter" -> {
            respondJson(exchange, 200, underenhetOppdateringerJson())
            true
        }

        "/enhetsregisteret/api/enheter/lastned" -> {
            respondGzipJson(
                exchange,
                "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
                "[${moderenhetJson()}]",
            )
            true
        }

        "/enhetsregisteret/api/underenheter/lastned" -> {
            respondGzipJson(
                exchange,
                "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
                "[${underenhetJson()}]",
            )
            true
        }

        else -> when {
            path.removePrefix(BRONNOYSUND_PATH_PREFIX).startsWith("/enhetsregisteret/api/enheter/") -> {
                respondJson(exchange, 200, moderenhetJson())
                true
            }

            path.removePrefix(BRONNOYSUND_PATH_PREFIX).startsWith("/enhetsregisteret/api/underenheter/") -> {
                respondJson(exchange, 200, underenhetJson())
                true
            }

            path == BRONNOYSUND_PATH_PREFIX || path == "$BRONNOYSUND_PATH_PREFIX/" -> {
                respondJson(exchange, 200, "{\"status\":\"ok\"}")
                true
            }

            else -> false
        }
    }
}

private fun moderenhetOppdateringerJson(): String {
    return """
        {
          "_embedded": {
            "oppdaterteEnheter": [
              {
                "oppdateringsid": 1,
                "dato": "2026-01-01T00:00:00Z",
                "organisasjonsnummer": "$OVERORDNET_ENHET",
                "endringstype": "Endring"
              }
            ]
          }
        }
    """.trimIndent()
}

private fun underenhetOppdateringerJson(): String {
    return """
        {
          "_embedded": {
            "oppdaterteUnderenheter": [
              {
                "oppdateringsid": 1,
                "dato": "2026-01-01T00:00:00Z",
                "organisasjonsnummer": "$ORGNUMMER",
                "endringstype": "Endring"
              }
            ]
          }
        }
    """.trimIndent()
}

private fun moderenhetJson(): String {
    return """
        {
          "organisasjonsnummer": "$OVERORDNET_ENHET",
          "navn": "BRONNOYSUND FAKE MODERENHET",
          "slettedato": null
        }
    """.trimIndent()
}

private fun underenhetJson(): String {
    return """
        {
          "organisasjonsnummer": "$ORGNUMMER",
          "navn": "BRONNOYSUND FAKE UNDERENHET",
          "slettedato": null,
          "overordnetEnhet": "$OVERORDNET_ENHET"
        }
    """.trimIndent()
}

private fun respondGzipJson(exchange: HttpExchange, contentType: String, body: String) {
    val gzippedBody = ByteArrayOutputStream().use { baos ->
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(body.toByteArray(StandardCharsets.UTF_8))
        }
        baos.toByteArray()
    }

    exchange.responseHeaders.add("Content-Type", contentType)
    exchange.responseHeaders.add("Content-Encoding", "gzip")
    exchange.sendResponseHeaders(200, gzippedBody.size.toLong())
    exchange.responseBody.use { output -> output.write(gzippedBody) }
    exchange.close()
}

