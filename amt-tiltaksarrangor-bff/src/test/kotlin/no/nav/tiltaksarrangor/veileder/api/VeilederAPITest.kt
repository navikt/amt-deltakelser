package no.nav.tiltaksarrangor.veileder.api

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.consumer.model.AdresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.testutils.getAdresse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VeilederAPITest(
	private val ansattRepository: AnsattRepository,
	private val deltakerRepository: DeltakerRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
) : IntegrationTest() {
	@Test
	fun `getMineDeltakere - ikke autentisert - returnerer 401`() {
		val response =
			sendRequest(
				method = "GET",
				path = "/tiltaksarrangor/veileder/mine-deltakere",
			)

		response.code shouldBe 401
	}

	@Test
	fun `getMineDeltakere - autentisert - returnerer 200`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste =
			DeltakerlisteDbo(
				id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
				navn = "Gjennomføring 1",
				gjennomforingstype = GjennomforingType.Gruppe,
				status = GjennomforingStatusType.GJENNOMFORES,
				arrangorId = arrangorId,
				tiltaksnavn = "Arbeidsforberedende trening",
				tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
				startDato = LocalDate.now(),
				sluttDato = null,
				erKurs = false,
				oppstartstype = Oppstartstype.LOPENDE,
				tilgjengeligForArrangorFraOgMedDato = null,
				pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
			)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.fromString("977350f2-d6a5-49bb-a3a0-773f25f863d9")
		val deltaker =
			DeltakerDbo(
				id = deltakerId,
				deltakerlisteId = deltakerliste.id,
				personident = "10987654321",
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				telefonnummer = null,
				epost = null,
				erSkjermet = false,
				adresse = AdresseJsonDbo.fromModel(getAdresse()),
				status = DeltakerStatus.Type.DELTAR,
				statusOpprettetDato = LocalDateTime.now(),
				statusGyldigFraDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
				statusAarsak = null,
				dagerPerUke = null,
				prosentStilling = null,
				startdato = LocalDate.of(2023, 2, 15),
				sluttdato = null,
				innsoktDato = LocalDate.now(),
				bestillingstekst = "tekst",
				navKontor = null,
				navVeilederId = null,
				navVeilederEpost = null,
				navVeilederNavn = null,
				navVeilederTelefon = null,
				skjultAvAnsattId = null,
				skjultDato = null,
				vurderingerFraArrangor = null,
				adressebeskyttet = false,
				innhold = null,
				kilde = Kilde.ARENA,
				historikk = emptyList(),
				sistEndret = LocalDate.of(2024, 10, 12).atStartOfDay(),
				forsteVedtakFattet = LocalDate.now(),
				erManueltDeltMedArrangor = false,
			)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Ansatt",
				mellomnavn = null,
				etternavn = "Ansattsen",
				roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
				deltakerlister = emptyList(),
				veilederDeltakere =
					listOf(
						VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER),
					),
			),
		)

		val response =
			sendRequest(
				method = "GET",
				path = "/tiltaksarrangor/veileder/mine-deltakere",
				headers = mapOf(HttpHeaders.AUTHORIZATION to "Bearer ${getTokenxToken(fnr = personIdent)}"),
			)

		val expectedJson =
			"""
			[{"id":"977350f2-d6a5-49bb-a3a0-773f25f863d9","fornavn":"Fornavn","mellomnavn":null,"etternavn":"Etternavn","fodselsnummer":"10987654321","startDato":"2023-02-15","sluttDato":null,"status":{"type":"DELTAR","endretDato":"2023-02-01T00:00:00","aarsak":null},"deltakerliste":{"id":"9987432c-e336-4b3b-b73e-b7c781a0823a","type":"Arbeidsforberedende trening","navn":"Gjennomføring 1"},"veiledertype":"VEILEDER","aktiveEndringsmeldinger":[],"aktivEndring":null,"sistEndret":"2024-10-12T00:00:00","adressebeskyttet":false,"svarFraNav":false,"oppdateringFraNav":false,"nyDeltaker":false,"erUnderOppfolging":false}]
			""".trimIndent()
		response.code shouldBe 200
		response.body.string() shouldBe expectedJson
	}
}
