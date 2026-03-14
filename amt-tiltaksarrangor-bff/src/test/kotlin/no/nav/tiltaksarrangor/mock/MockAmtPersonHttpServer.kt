package no.nav.tiltaksarrangor.mock

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.client.amtperson.NavAnsattResponse
import no.nav.tiltaksarrangor.client.amtperson.NavEnhetDto
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.UUID

class MockAmtPersonHttpServer : MockHttpServer(name = "amt-person-server") {
	fun addEnhetResponse(id: UUID = UUID.randomUUID()) {
		val enhetResponse = NavEnhetDto(
			id,
			"EnhetId",
			"Nav Oslo",
		)
		addResponseHandler(
			path = "/api/nav-enhet/$id",
			MockResponse()
				.setResponseCode(200)
				.setBody(objectMapper.writeValueAsString(enhetResponse)),
		)
	}

	fun addAnsattResponse(id: UUID = UUID.randomUUID()) {
		val ansattResponse = NavAnsattResponse(
			id = id,
			navIdent = "NAVident",
			navn = "Navn Navnsen",
			epost = "navn.navsen@test.no",
			telefon = "123",
		)
		addResponseHandler(
			path = "/api/nav-ansatt/$id",
			MockResponse()
				.setResponseCode(200)
				.setBody(objectMapper.writeValueAsString(ansattResponse)),
		)
	}

	fun addKontaktinformasjonResponse(
		personident: String,
		epost: String = "foo@bar.baz",
		telefonnnummer: String = "12345678",
	) {
		val kontaktinformasjon = mapOf(
			personident to Kontaktinformasjon(
				epost = epost,
				telefonnummer = telefonnnummer,
			),
		)

		val requestPredicate = { req: RecordedRequest ->
			req.path == "/api/nav-bruker/kontaktinformasjon" &&
				req.method == "POST" &&
				req.getBodyAsString() == objectMapper.writeValueAsString(setOf(personident))
		}

		addResponseHandler(
			requestPredicate,
			MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(kontaktinformasjon)),
		)
	}
}
