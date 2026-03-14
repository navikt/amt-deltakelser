package no.nav.tiltaksarrangor.melding.forslag

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.melding.forslag.request.AvsluttDeltakelseRequest
import no.nav.tiltaksarrangor.melding.forslag.request.DeltakelsesmengdeRequest
import no.nav.tiltaksarrangor.melding.forslag.request.EndreAvslutningRequest
import no.nav.tiltaksarrangor.melding.forslag.request.FjernOppstartsdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.request.ForlengDeltakelseRequest
import no.nav.tiltaksarrangor.melding.forslag.request.ForslagRequest
import no.nav.tiltaksarrangor.melding.forslag.request.IkkeAktuellRequest
import no.nav.tiltaksarrangor.melding.forslag.request.SluttarsakRequest
import no.nav.tiltaksarrangor.melding.forslag.request.SluttdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.request.StartdatoRequest
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ForslagAPITest : IntegrationTest() {
	private val forlengDeltakelseRequest = ForlengDeltakelseRequest(LocalDate.now().plusWeeks(42), "Forlengelse fordi...")
	private val avsluttDeltakelseRequest =
		AvsluttDeltakelseRequest(LocalDate.now().plusWeeks(1), EndringAarsak.FattJobb, "Avslutning fordi...", false, null)
	private val ikkeAktuellRequest = IkkeAktuellRequest(EndringAarsak.FattJobb, "Ikke aktuell fordi...")
	private val deltakelsesmengdeRequest = DeltakelsesmengdeRequest(42, 3, LocalDate.now(), "Deltakelsesmengde fordi...")
	private val sluttdatoRequest = SluttdatoRequest(LocalDate.now().plusWeeks(42), "Endres fordi...")
	private val startdatoRequest = StartdatoRequest(LocalDate.now(), LocalDate.now().plusWeeks(4), begrunnelse = "Startdato fordi...")
	private val sluttarsakRequest = SluttarsakRequest(EndringAarsak.Utdanning, begrunnelse = "Sluttårsak fordi...")
	private val fjernOppstartdatoRequest = FjernOppstartsdatoRequest("begrunnelse")

	val requests = listOf(
		forlengDeltakelseRequest,
		avsluttDeltakelseRequest,
		ikkeAktuellRequest,
		deltakelsesmengdeRequest,
		sluttdatoRequest,
		startdatoRequest,
		sluttarsakRequest,
		fjernOppstartdatoRequest,
	)

	@Autowired
	private lateinit var forslagService: ForslagService

	@Test
	fun `skal teste token autentisering`() {
		val url = "${serverUrl()}/tiltaksarrangor/deltaker/${UUID.randomUUID()}/forslag"

		val requestBuilders = listOf(
			Request.Builder().post(emptyRequest()).url("$url/forleng"),
			Request.Builder().post(emptyRequest()).url("$url/avslutt"),
			Request.Builder().post(emptyRequest()).url("$url/ikke-aktuell"),
			Request.Builder().post(emptyRequest()).url("$url/deltakelsesmengde"),
			Request.Builder().post(emptyRequest()).url("$url/startdato"),
			Request.Builder().post(emptyRequest()).url("$url/sluttarsak"),
			Request.Builder().post(emptyRequest()).url("$url/fjern-oppstartsdato"),
			Request.Builder().post(emptyRequest()).url("$url/${UUID.randomUUID()}/tilbakekall"),
		)
		testTokenAutentisering(requestBuilders)
	}

	@Test
	fun `forslag - har ikke tilgang til deltakerliste - skal returnere 403`() {
		requests.forEach {
			testIkkeTilgangTilDeltakerliste { deltakerId, ansattPersonIdent ->
				it.send(deltakerId, ansattPersonIdent)
			}
		}

		testIkkeTilgangTilDeltakerliste { deltakerId, ansattPersonIdent ->
			sendTilbakekallRequest(UUID.randomUUID(), deltakerId, ansattPersonIdent)
		}
	}

	@Test
	fun `forslag - deltaker adressebeskyttet, ansatt er ikke veileder - skal returnere 403`() {
		requests.forEach {
			testDeltakerAdressebeskyttet { deltakerId, ansattPersonIdent ->
				it.send(deltakerId, ansattPersonIdent)
			}
		}

		testDeltakerAdressebeskyttet { deltakerId, ansattPersonIdent ->
			sendTilbakekallRequest(UUID.randomUUID(), deltakerId, ansattPersonIdent)
		}
	}

	@Test
	fun `forslag - deltaker skjult - skal returnere 400`() {
		requests.forEach {
			testDeltakerSkjult { deltakerId, ansattPersonIdent ->
				it.send(deltakerId, ansattPersonIdent)
			}
		}

		testDeltakerSkjult { deltakerId, ansattPersonIdent ->
			sendTilbakekallRequest(UUID.randomUUID(), deltakerId, ansattPersonIdent)
		}
	}

	@Test
	fun `forleng - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(forlengDeltakelseRequest) { endring ->
			endring as Forslag.ForlengDeltakelse
			endring.sluttdato shouldBe forlengDeltakelseRequest.sluttdato
		}
	}

	@Test
	fun `avslutt - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(avsluttDeltakelseRequest) { endring ->
			endring as Forslag.AvsluttDeltakelse
			endring.sluttdato shouldBe avsluttDeltakelseRequest.sluttdato
			endring.aarsak shouldBe avsluttDeltakelseRequest.aarsak
		}
	}

	@Test
	fun `ikke-aktuell - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(ikkeAktuellRequest) { endring ->
			endring as Forslag.IkkeAktuell
			endring.aarsak shouldBe avsluttDeltakelseRequest.aarsak
		}
	}

	@Test
	fun `deltakelsesmengde - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(deltakelsesmengdeRequest) { endring ->
			endring as Forslag.Deltakelsesmengde
			endring.deltakelsesprosent shouldBe deltakelsesmengdeRequest.deltakelsesprosent
			endring.dagerPerUke shouldBe deltakelsesmengdeRequest.dagerPerUke
		}
	}

	@Test
	fun `sluttdato - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(sluttdatoRequest) { endring ->
			endring as Forslag.Sluttdato
			endring.sluttdato shouldBe sluttdatoRequest.sluttdato
		}
	}

	@Test
	fun `startdato - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(startdatoRequest) { endring ->
			endring as Forslag.Startdato
			endring.startdato shouldBe startdatoRequest.startdato
			endring.sluttdato shouldBe startdatoRequest.sluttdato
		}
	}

	@Test
	fun `sluttarsak - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(sluttarsakRequest) { endring ->
			endring as Forslag.Sluttarsak
			endring.aarsak shouldBe sluttarsakRequest.aarsak
		}
	}

	@Test
	fun `fjern-oppstartsdato - nytt forslag - skal returnere 200 og riktig response`() {
		testOpprettetForslag(fjernOppstartdatoRequest) { endring ->
			endring as Forslag.FjernOppstartsdato
		}
	}

	@Test
	fun `tilbakekall - aktivt forslag - skal returnere 200`() {
		with(ForslagCtx(applicationContext, forlengDeltakelseForslag())) {
			upsertForslag()
			val response = sendTilbakekallRequest(forslag.id, deltaker.id, koordinator.personIdent)

			response.code shouldBe 200

			forslagService.get(forslag.id).isFailure shouldBe true
		}
	}

	private fun testOpprettetForslag(request: ForslagRequest, block: (endring: Forslag.Endring) -> Unit) {
		with(DeltakerContext(applicationContext)) {
			val response = request.send(deltaker.id, koordinator.personIdent)
			response.code shouldBe 200

			val aktivtForslag = objectMapper.readValue<AktivtForslagResponse>(response.body.string())

			assertSoftly(aktivtForslag) {
				status shouldBe ForslagResponse.Status.VenterPaSvar
				begrunnelse shouldBe request.begrunnelse
				opprettet shouldBeCloseTo LocalDateTime.now()
			}

			block(aktivtForslag.endring)
		}
	}

	private fun url(deltakerId: UUID) = "/tiltaksarrangor/deltaker/$deltakerId/forslag"

	private fun ForslagRequest.send(deltakerId: UUID, ansattIdent: String): Response {
		val mediaTypeJson = "application/json".toMediaType()

		val path = "${url(deltakerId)}/" + when (this) {
			is AvsluttDeltakelseRequest -> "avslutt"
			is ForlengDeltakelseRequest -> "forleng"
			is IkkeAktuellRequest -> "ikke-aktuell"
			is DeltakelsesmengdeRequest -> "deltakelsesmengde"
			is SluttdatoRequest -> "sluttdato"
			is SluttarsakRequest -> "sluttarsak"
			is StartdatoRequest -> "startdato"
			is FjernOppstartsdatoRequest -> "fjern-oppstartsdato"
			is EndreAvslutningRequest -> "endre-avslutning"
		}

		return sendRequest(
			method = "POST",
			path = path,
			body = objectMapper.writeValueAsString(this).toRequestBody(mediaTypeJson),
			headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = ansattIdent)}"),
		)
	}

	private fun sendTilbakekallRequest(
		forslagId: UUID,
		deltakerId: UUID,
		ansattIdent: String,
	) = sendRequest(
		method = "POST",
		path = "${url(deltakerId)}/$forslagId/tilbakekall",
		body = emptyRequest(),
		headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = ansattIdent)}"),
	)
}
