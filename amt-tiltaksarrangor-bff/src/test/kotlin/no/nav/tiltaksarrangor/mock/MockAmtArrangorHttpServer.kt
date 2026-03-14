package no.nav.tiltaksarrangor.mock

import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.consumer.model.AnsattPersonaliaDto
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.NavnDto
import no.nav.tiltaksarrangor.consumer.model.TilknyttetArrangorDto
import no.nav.tiltaksarrangor.consumer.model.VeilederDto
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.mockwebserver.MockResponse
import java.util.UUID

class MockAmtArrangorHttpServer : MockHttpServer(name = "Amt-Arrangor Mock Server") {
	fun addAnsattResponse(ansattId: UUID = UUID.randomUUID(), personIdent: String) {
		addResponseHandler(
			path = "/api/ansatt",
			MockResponse()
				.setResponseCode(200)
				.setBody(objectMapper.writeValueAsString(getAnsatt(ansattId, personIdent))),
		)
	}

	fun addLeggTilEllerFjernDeltakerlisteResponse(arrangorId: UUID, deltakerlisteId: UUID) {
		addResponseHandler(
			path = "/api/ansatt/koordinator/$arrangorId/$deltakerlisteId",
			MockResponse()
				.setResponseCode(200),
		)
	}

	fun addOppdaterVeilederForDeltakerResponse(deltakerId: UUID) {
		addResponseHandler(
			path = "/api/ansatt/veiledere/$deltakerId",
			MockResponse()
				.setResponseCode(200),
		)
	}

	private fun getAnsatt(ansattId: UUID, personIdent: String): AnsattDto = AnsattDto(
		id = ansattId,
		personalia =
			AnsattPersonaliaDto(
				personident = personIdent,
				navn =
					NavnDto(
						fornavn = "Fornavn",
						mellomnavn = null,
						etternavn = "Etternavn",
					),
			),
		arrangorer =
			listOf(
				TilknyttetArrangorDto(
					arrangorId = UUID.randomUUID(),
					roller = listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
					veileder = listOf(VeilederDto(UUID.randomUUID(), Veiledertype.VEILEDER)),
					koordinator = listOf(UUID.randomUUID()),
				),
				TilknyttetArrangorDto(
					arrangorId = UUID.randomUUID(),
					roller = listOf(AnsattRolle.KOORDINATOR),
					veileder = emptyList(),
					koordinator = listOf(UUID.randomUUID()),
				),
				TilknyttetArrangorDto(
					arrangorId = UUID.randomUUID(),
					roller = listOf(AnsattRolle.VEILEDER),
					veileder = listOf(VeilederDto(UUID.randomUUID(), Veiledertype.VEILEDER)),
					koordinator = emptyList(),
				),
			),
	)
}
