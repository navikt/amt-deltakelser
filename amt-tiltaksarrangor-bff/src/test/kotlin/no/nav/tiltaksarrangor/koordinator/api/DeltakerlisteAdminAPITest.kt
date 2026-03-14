package no.nav.tiltaksarrangor.koordinator.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.time.LocalDate
import java.util.UUID

class DeltakerlisteAdminAPITest(
	private val ansattRepository: AnsattRepository,
	private val arrangorRepository: ArrangorRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
) : IntegrationTest() {
	@Test
	fun `getAlleDeltakerlister - ikke autentisert - returnerer 401`() {
		val response =
			sendRequest(
				method = "GET",
				path = "/tiltaksarrangor/koordinator/admin/deltakerlister",
			)

		response.code shouldBe 401
	}

	@Test
	fun `getAlleDeltakerlister - autentisert - returnerer 200`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(
			ArrangorDbo(
				id = arrangorId,
				navn = "Arrangør AS",
				organisasjonsnummer = "88888888",
				overordnetArrangorId = null,
			),
		)
		val deltakerliste1 =
			DeltakerlisteDbo(
				id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		val deltakerliste2 =
			DeltakerlisteDbo(
				id = UUID.fromString("fd70758a-44c5-4868-bdcb-b1ddd26cb5e9"),
				navn = "Gjennomføring 2",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Annet tiltak",
				tiltakskode = Tiltakskode.OPPFOLGING,
				startDato = LocalDate.of(2023, 5, 1),
				sluttDato = LocalDate.of(2023, 6, 1),
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = LocalDate.now().minusYears(1),
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste1)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste2)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste1.id)),
				veilederDeltakere = emptyList(),
			),
		)

		val response =
			sendRequest(
				method = "GET",
				path = "/tiltaksarrangor/koordinator/admin/deltakerlister",
				headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = personIdent)}"),
			)

		val expectedJson =
			"""
			[{"id":"9987432c-e336-4b3b-b73e-b7c781a0823a","navn":"Gjennomføring 1","tiltaksnavn":"Navn på tiltak","arrangorNavn":"Arrangør AS","arrangorOrgnummer":"88888888","arrangorParentNavn":"Arrangør AS","startDato":"2023-02-01","sluttDato":null,"lagtTil":true,"oppstartstype":"LOPENDE"},{"id":"fd70758a-44c5-4868-bdcb-b1ddd26cb5e9","navn":"Gjennomføring 2","tiltaksnavn":"Annet tiltak","arrangorNavn":"Arrangør AS","arrangorOrgnummer":"88888888","arrangorParentNavn":"Arrangør AS","startDato":"2023-05-01","sluttDato":"2023-06-01","lagtTil":false,"oppstartstype":"LOPENDE"}]
			""".trimIndent()
		response.code shouldBe 200
		response.body.string() shouldBe expectedJson
	}

	@Test
	fun `leggTilDeltakerliste - ikke autentisert - returnerer 401`() {
		val response =
			sendRequest(
				method = "POST",
				path = "/tiltaksarrangor/koordinator/admin/deltakerliste/${UUID.randomUUID()}",
				body = emptyRequest(),
			)

		response.code shouldBe 401
	}

	@Test
	fun `leggTilDeltakerliste - autentisert - returnerer 200`() {
		val personIdent = "12345678910"
		val deltakerlisteId = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a")
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = deltakerlisteId,
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)
		mockAmtArrangorServer.addLeggTilEllerFjernDeltakerlisteResponse(arrangorId, deltakerlisteId)

		val response =
			sendRequest(
				method = "POST",
				path = "/tiltaksarrangor/koordinator/admin/deltakerliste/$deltakerlisteId",
				body = emptyRequest(),
				headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = personIdent)}"),
			)

		response.code shouldBe 200

		val ansattFraDb = ansattRepository.getAnsatt(ansattId)
		ansattFraDb?.deltakerlister?.size shouldBe 1
		ansattFraDb?.deltakerlister?.find { it.deltakerlisteId == deltakerlisteId } shouldNotBe null
	}

	@Test
	fun `fjernDeltakerliste - ikke autentisert - returnerer 401`() {
		val response =
			sendRequest(
				method = "DELETE",
				path = "/tiltaksarrangor/koordinator/admin/deltakerliste/${UUID.randomUUID()}",
			)

		response.code shouldBe 401
	}

	@Test
	fun `fjernDeltakerliste - autentisert - returnerer 200`() {
		val personIdent = "12345678910"
		val deltakerlisteId = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a")
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = deltakerlisteId,
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Navn på tiltak",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.of(2023, 2, 1),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerlisteId)),
				veilederDeltakere = emptyList(),
			),
		)
		mockAmtArrangorServer.addLeggTilEllerFjernDeltakerlisteResponse(arrangorId, deltakerlisteId)

		val response =
			sendRequest(
				method = "DELETE",
				path = "/tiltaksarrangor/koordinator/admin/deltakerliste/$deltakerlisteId",
				headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = personIdent)}"),
			)

		response.code shouldBe 200

		val ansattFraDb = ansattRepository.getAnsatt(ansattId)
		ansattFraDb?.deltakerlister?.size shouldBe 0
	}
}
