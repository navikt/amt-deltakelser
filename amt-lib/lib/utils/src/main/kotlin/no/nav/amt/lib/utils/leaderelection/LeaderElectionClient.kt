package no.nav.amt.lib.utils.leaderelection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 * A client for leader election.
 * This class helps determine if the current instance is the leader in a distributed system.
 * It relies on a `LeaderProvider` to fetch the current leader from an elector path.
 *
 * The `electorPath` is the URL to the leader election service.
 * If `electorPath` is set to "dont_look_for_leader", `isLeader()` will always return true.
 *
 * Example usage with Ktor's HttpClient:
 * ```
 * val client = HttpClient(CIO) {
 *     install(ContentNegotiation) {
 *         jackson()
 *     }
 * }
 *
 * val leaderProvider = LeaderProvider { path ->
 *     client.get(path).body<Leader>()
 * }
 *
 * val leaderElection = LeaderElection(leaderProvider, "http://elector-path")
 *
 * // To check for leadership:
 * if (leaderElection.isLeader()) {
 *     // ... run job
 * }
 * ```
 *
 * Example usage with Spring Boot's RestTemplate:
 * ```
 * @Configuration
 * class LeaderElectionConfig {
 *
 *     @Bean
 *     fun leaderElection(restTemplate: RestTemplate): LeaderElection {
 *         val leaderProvider = LeaderProvider { path ->
 *             val leader = restTemplate.getForObject(path, Leader::class.java)
 *             leader ?: throw RuntimeException("Could not get leader from $path")
 *         }
 *         return LeaderElection(leaderProvider, "http://elector-path")
 *     }
 * }
 *
 * data class Leader(val name: String)
 * ```
 */
class LeaderElectionClient(
    private val leaderProvider: LeaderProvider,
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
            val leader = leaderProvider.getLeader(electorPath)
            return leader.name == hostname
        } catch (e: Exception) {
            val message = "Kall mot elector path feiler"
            log.error(message, e)
            throw RuntimeException(message, e)
        }
    }
}

fun interface LeaderProvider {
    suspend fun getLeader(path: String): Leader
}

data class Leader(
    val name: String,
)
