package no.nav.amt.deltaker.bff.deltaker.job.leaderelection

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress

class LeaderElection(
    private val httpClient: HttpClient,
    private val electorPath: String,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun isLeader(): Boolean {
        if (electorPath == "dont_look_for_leader") {
            log.info("Ser ikke etter leader, returnerer at jeg er leader")
            return true
        }

        val hostname: String = withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName

        try {
            val leader = httpClient.get(getHttpPath(electorPath)).body<Leader>()
            return leader.name == hostname
        } catch (_: Exception) {
            val message = "Kall mot elector path feiler"
            log.error(message)
            throw RuntimeException(message)
        }
    }

    private fun getHttpPath(url: String): String = when (url.startsWith("http://")) {
        true -> url
        else -> "http://$url"
    }

    private data class Leader(
        val name: String,
    )
}
