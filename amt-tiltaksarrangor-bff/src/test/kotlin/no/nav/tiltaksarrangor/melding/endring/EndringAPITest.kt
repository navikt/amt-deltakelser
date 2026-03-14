package no.nav.tiltaksarrangor.melding.endring

import io.kotest.matchers.shouldBe
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.melding.endring.request.EndringFraArrangorRequest
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.util.UUID

class EndringAPITest : IntegrationTest() {
	private val leggTilOppstartsdatoRequest = LeggTilOppstartsdatoRequest(LocalDate.now(), LocalDate.now().plusMonths(3))

	private val requests = listOf(
		leggTilOppstartsdatoRequest,
	)

	@Test
	fun `skal teste token autentisering`() {
		val url = "${serverUrl()}/tiltaksarrangor/deltaker/${UUID.randomUUID()}/endring"

		val requestBuilders = listOf(
			Request.Builder().post(emptyRequest()).url("$url/legg-til-oppstartsdato"),
		)
		testTokenAutentisering(requestBuilders)
	}

	@Test
	fun `endring - har ikke tilgang til deltakerliste - skal returnere 403`() {
		requests.forEach {
			testIkkeTilgangTilDeltakerliste { deltakerId, ansattPersonIdent ->
				it.send(deltakerId, ansattPersonIdent)
			}
		}
	}

	@Test
	fun `endring - deltaker adressebeskyttet, ansatt er ikke veileder - skal returnere 403`() {
		requests.forEach {
			testDeltakerAdressebeskyttet { deltakerId, ansattPersonIdent ->
				it.send(deltakerId, ansattPersonIdent)
			}
		}
	}

	@Test
	fun `endring - deltaker skjult - skal returnere 400`() {
		requests.forEach {
			testDeltakerSkjult { deltakerId, ansattPersonIdent ->
				it.send(deltakerId, ansattPersonIdent)
			}
		}
	}

	@Test
	fun `startdato - ny endring - skal returnere 200 og riktig response`() {
		testOpprettetEndring(leggTilOppstartsdatoRequest) { deltaker ->
			deltaker.startDato shouldBe leggTilOppstartsdatoRequest.startdato
			deltaker.sluttDato shouldBe leggTilOppstartsdatoRequest.sluttdato
		}
	}

	private fun testOpprettetEndring(request: LeggTilOppstartsdatoRequest, block: (deltaker: Deltaker) -> Unit) {
		with(DeltakerContext(applicationContext)) {
			setVenterPaOppstart()
			val response = request.send(deltaker.id, koordinator.personIdent)
			response.code shouldBe 200

			val deltaker = objectMapper.readValue<Deltaker>(response.body.string())

			block(deltaker)
		}
	}

	private fun url(deltakerId: UUID) = "/tiltaksarrangor/deltaker/$deltakerId/endring"

	private fun EndringFraArrangorRequest.send(deltakerId: UUID, ansattIdent: String): Response {
		val mediaTypeJson = "application/json".toMediaType()

		val path = "${url(deltakerId)}/" + when (this) {
			is LeggTilOppstartsdatoRequest -> "legg-til-oppstartsdato"
		}

		return sendRequest(
			method = "POST",
			path = path,
			body = objectMapper.writeValueAsString(this).toRequestBody(mediaTypeJson),
			headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = ansattIdent)}"),
		)
	}
}
