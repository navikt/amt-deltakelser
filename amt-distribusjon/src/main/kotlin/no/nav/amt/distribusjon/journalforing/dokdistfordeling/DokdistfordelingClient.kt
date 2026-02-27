package no.nav.amt.distribusjon.journalforing.dokdistfordeling

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.auth.AzureAdTokenClient
import org.slf4j.LoggerFactory
import java.util.UUID

class DokdistfordelingClient(
    private val httpClient: HttpClient,
    private val azureAdTokenClient: AzureAdTokenClient,
    environment: Environment,
) {
    private val scope = environment.dokdistfordelingScope
    private val url = environment.dokdistfordelingUrl
    private val navCallId = Environment.appName
    private val log = LoggerFactory.getLogger(javaClass)

    /*
        https://confluence.adeo.no/pages/viewpage.action?pageId=320038938
        distribusjon av dokumenter knyttet til en utgående, ferdigstilt journalpost.
        Denne brukes kun dersom brukeren ikke er digital så resultatet blir i praksis at det sendes brev
     */
    suspend fun distribuerJournalpost(
        journalpostId: String,
        distribusjonstype: DistribuerJournalpostRequest.Distribusjonstype = DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
        tvingSentralPrint: Boolean = false,
    ): UUID? {
        val request = DistribuerJournalpostRequest(journalpostId, tvingSentralPrint, distribusjonstype = distribusjonstype)
        return distribuerJournalpost(request)
    }

    suspend fun distribuerJournalpost(request: DistribuerJournalpostRequest): UUID? {
        val token = azureAdTokenClient.getMachineToMachineToken(scope)
        val journalpostId = request.journalpostId
        val response = httpClient.post("$url/rest/v1/distribuerjournalpost") {
            header(HttpHeaders.Authorization, token)
            header("Nav-Callid", navCallId)
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(request))
        }

        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.Conflict) {
                log.warn("Journalpost $journalpostId er allerede distribuert")
                return response.body<DistribuerJournalpostResponse>().bestillingsId
            }
            if (response.status == HttpStatusCode.Gone) {
                log.warn("Journalpost $journalpostId tilhører bruker som er død og som mangler adresse i PDL. Kan ikke sende brev.")
                return null
            }
            error("Distribuering av journalpost $journalpostId feilet: ${response.status} ${response.bodyAsText()}")
        }

        val bestillingsId = response.body<DistribuerJournalpostResponse>().bestillingsId

        log.info("Distribuerte journalpost $journalpostId, bestillingsId=$bestillingsId")

        return bestillingsId
    }
}

data class DistribuerJournalpostRequest(
    val journalpostId: String,
    val tvingSentralPrint: Boolean = false,
    val bestillendeFagsystem: String = Environment.appName,
    val dokumentProdApp: String = Environment.appName,
    val distribusjonstype: Distribusjonstype = Distribusjonstype.VEDTAK,
    val distribusjonstidspunkt: Distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID,
) {
    enum class Distribusjonstidspunkt {
        UMIDDELBART,
        KJERNETID,
    }

    enum class Distribusjonstype {
        VEDTAK,
        VIKTIG,
        ANNET,
    }
}

data class DistribuerJournalpostResponse(
    val bestillingsId: UUID,
)
