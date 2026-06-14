package no.nav.tiltaksarrangor.consumer.jobs.leaderelection

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import java.net.InetAddress

@Component
class LeaderElection(
    @Value($$"${elector.path}") private val electorPath: String,
    builder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val hostname by lazy { InetAddress.getLocalHost().hostName }

    private val client = if (electorPath == "dont_look_for_leader") {
        null
    } else {
        builder
            .baseUrl(httpPrefixed(electorPath))
            .build()
    }

    fun isLeader(): Boolean {
        if (client == null) {
            log.info("Ser ikke etter leader, returnerer at jeg er leader")
            return true
        }

        val leader = client
            .get()
            .retrieve()
            .onStatus(HttpStatusCode::isError) { _, response ->
                log.error("Feil ved henting av leader fra elector. Responsekode: ${response.statusCode.value()}")
                throw RuntimeException("Feil ved kall mot elector service")
            }.requiredBody<Leader>()

        return leader.name == hostname
    }

    private data class Leader(
        val name: String,
    )

    companion object {
        private fun httpPrefixed(path: String) = if (path.startsWith("http://")) path else "http://$path"
    }
}
