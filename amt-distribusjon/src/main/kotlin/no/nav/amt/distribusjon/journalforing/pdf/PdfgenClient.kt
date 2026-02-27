package no.nav.amt.distribusjon.journalforing.pdf

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.lib.models.journalforing.pdf.EndringsvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakPdfDto
import no.nav.amt.lib.models.journalforing.pdf.HovedvedtakVedTildeltPlassPdfDto
import no.nav.amt.lib.models.journalforing.pdf.InnsokingsbrevPdfDto
import no.nav.amt.lib.models.journalforing.pdf.VentelistebrevPdfDto

class PdfgenClient(
    private val httpClient: HttpClient,
    environment: Environment,
) {
    private val url = environment.amtPdfgenUrl + "/api/v1/genpdf/amt"

    /*
        Genererer hovedvedtak for direktegodkjente tiltak som i praksis vil gjelde:
        - Individuelle tiltak som AFT, Oppfølging
        - Gruppebaserte(ofte kalt "kurs") med løpende oppstart som i praksis gjør de til "individuelle"
     */
    suspend fun genererHovedvedtakForIndividuellOppfolging(hovedvedtakPdfDto: HovedvedtakPdfDto): ByteArray {
        val response = httpClient.post("$url/hovedvedtak-individuell-oppfolging") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(hovedvedtakPdfDto))
        }
        if (!response.status.isSuccess()) {
            error("Kunne ikke hente opprette hovedvedtak-pdf i amt-pdfgen. Status=${response.status.value} error=${response.bodyAsText()}")
        }
        return response.body()
    }

    /*
        Genererer hovedvedtak etter tiltaksansvarlig har tildelt plass. Den vil gjelde:
        - typiske kurs som gruppeamo, gruppefagyrk, og andre gruppebaserte tiltak med felles oppstart
        - dette er "den gamle" malen som også ble brukt tidligere når felles oppstart
        automatisk medførte krav om tildeling av plass fra tiltaksansvarlig
     */
    suspend fun genererHovedvedtakTildeltPlassFellesOppstart(hovedvedtakPdfDto: HovedvedtakVedTildeltPlassPdfDto): ByteArray {
        val response = httpClient.post("$url/hovedvedtak-tildelt-plass-felles-oppstart") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(hovedvedtakPdfDto))
        }
        if (!response.status.isSuccess()) {
            error("Kunne ikke hente opprette hovedvedtak-pdf i amt-pdfgen. Status=${response.status.value} error=${response.bodyAsText()}")
        }
        return response.body()
    }

    /*
        Genererer hovedvedtak etter tiltaksansvarlig har tildelt plass. Den vil gjelde:
        - opplæringstiltak med rammeavtale som arbeidsmarkedsopplæring, norskopplæring osv
     */
    suspend fun genererHovedvedtakTildeltPlassLoependeOppstart(hovedvedtakPdfDto: HovedvedtakVedTildeltPlassPdfDto): ByteArray {
        val response = httpClient.post("$url/hovedvedtak-tildelt-plass-loepende-oppstart") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(hovedvedtakPdfDto))
        }
        if (!response.status.isSuccess()) {
            error("Kunne ikke hente opprette hovedvedtak-pdf i amt-pdfgen. Status=${response.status.value} error=${response.bodyAsText()}")
        }
        return response.body()
    }

    suspend fun genererInnsokingsbrevPDF(innsokingsbrevPdfDto: InnsokingsbrevPdfDto): ByteArray {
        val response = httpClient.post("$url/innsokingsbrev") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(innsokingsbrevPdfDto))
        }
        if (!response.status.isSuccess()) {
            error(
                "Kunne ikke hente opprette kurs-innsoking-pdf i amt-pdfgen. Status=${response.status.value} error=${response.bodyAsText()}",
            )
        }
        return response.body()
    }

    suspend fun genererVentelistebrevPDF(ventelistebrevPdfDto: VentelistebrevPdfDto): ByteArray {
        val response = httpClient.post("$url/ventelistebrev") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(ventelistebrevPdfDto))
        }
        if (!response.status.isSuccess()) {
            error(
                "Kunne ikke hente opprette venteliste-pdf i amt-pdfgen. Status=${response.status.value} error=${response.bodyAsText()}",
            )
        }
        return response.body()
    }

    suspend fun endringsvedtak(endringsvedtakPdfDto: EndringsvedtakPdfDto): ByteArray {
        val response = httpClient.post("$url/endringsvedtak") {
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(endringsvedtakPdfDto))
        }
        if (!response.status.isSuccess()) {
            error(
                "Kunne ikke hente opprette endringsvedtak-pdf i amt-pdfgen. Status=${response.status.value} error=${response.bodyAsText()}",
            )
        }
        return response.body()
    }
}
