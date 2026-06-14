package no.nav.tiltaksarrangor.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.api.request.RegistrerVurderingRequest
import no.nav.tiltaksarrangor.api.response.DeltakerHistorikkResponse
import no.nav.tiltaksarrangor.api.response.EndringFraArrangorResponse
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.model.DeltakerStatusAarsakJsonDboDto
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.NavAnsattRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.testutils.getMockAnsatt
import no.nav.tiltaksarrangor.testutils.getVurderinger
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.resttestclient.exchange
import org.springframework.boot.resttestclient.postForEntity
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@AutoConfigureTestRestTemplate
class TiltaksarrangorApiTest(
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val endringsmeldingRepository: EndringsmeldingRepository,
    private val arrangorRepository: ArrangorRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val restTemplate: TestRestTemplate,
) : IntegrationTest() {
    @Nested
    inner class GetMineRollerTests {
        @Test
        fun `getMineRoller - ikke autentisert - returnerer 401`() {
            val response = restTemplate.exchange<String>(
                "/tiltaksarrangor/meg/roller",
                HttpMethod.GET,
            )

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        @Test
        fun `getMineRoller - autentisert - returnerer 200`() {
            every { amtArrangorClient.getAnsatt() } returns getMockAnsatt(personIdent = PERSONIDENT_IN_TEST)

            val response = getWithAuthResponse<List<String>>("/tiltaksarrangor/meg/roller")

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe listOf("KOORDINATOR", "VEILEDER")
        }
    }

    @Nested
    inner class GetDeltakerTests {
        @Test
        fun `getDeltaker - ikke autentisert - returnerer 401`() {
            val response = restTemplate.exchange<String>(
                "/tiltaksarrangor/deltaker/${UUID.randomUUID()}",
                HttpMethod.GET,
                HttpEntity<String>(HttpHeaders()),
            )

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        @Test
        fun `getDeltaker - autentisert, har ikke tilgang - returnerer 403`() {
            val arrangorId = UUID.randomUUID()
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorDbo(
                    id = arrangorId,
                    navn = "Orgnavn",
                    organisasjonsnummer = "orgnummer",
                    overordnetArrangorId = null,
                ),
            )
            val deltakerliste = getDeltakerliste(arrangorId)
            deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
            val deltakerId = UUID.randomUUID()
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id))
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller =
                        listOf(
                            AnsattRolleDbo(UUID.randomUUID(), AnsattRolle.KOORDINATOR),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = emptyList(),
                ),
            )

            val response = getWithAuthResponse<String>(
                url = "/tiltaksarrangor/deltaker/$deltakerId",
            )

            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        @Test
        fun `getDeltaker - autentisert, har tilgang - returnerer 200`() {
            val arrangorId = UUID.randomUUID()
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorDbo(
                    id = arrangorId,
                    navn = "Orgnavn",
                    organisasjonsnummer = "orgnummer",
                    overordnetArrangorId = null,
                ),
            )
            val deltakerliste = getDeltakerliste(arrangorId).copy(
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                startDato = LocalDate.of(2023, 2, 1),
            )
            deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
            val deltakerId = UUID.fromString("977350f2-d6a5-49bb-a3a0-773f25f863d9")
            val gyldigFra = LocalDateTime.now()

            val navVeileder = NavAnsatt(
                id = UUID.randomUUID(),
                navident = "Z123456",
                navn = "Veileder Veiledersen",
                epost = "epost@nav.no",
                telefon = "56565656",
            )
            navAnsattRepository.upsert(navVeileder)

            val deltaker = getDeltaker(deltakerId, deltakerliste.id).copy(
                personident = "10987654321",
                telefonnummer = "90909090",
                epost = "mail@test.no",
                status = DeltakerStatus.Type.DELTAR,
                statusOpprettetDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
                startdato = LocalDate.of(2023, 2, 1),
                dagerPerUke = 2.5f,
                innsoktDato = LocalDate.of(2023, 1, 15),
                bestillingstekst = "Tror deltakeren vil ha nytte av dette",
                navKontor = "Nav Oslo",
                navVeilederId = navVeileder.id,
                navVeilederNavn = navVeileder.navn,
                navVeilederTelefon = navVeileder.telefon,
                navVeilederEpost = navVeileder.epost,
                vurderingerFraArrangor = getVurderinger(deltakerId, gyldigFra),
            )
            deltakerRepository.insertOrUpdateDeltaker(deltaker)
            val endringsmeldinger = getEndringsmeldinger(deltakerId)
            endringsmeldinger.forEach { endringsmeldingRepository.insertOrUpdateEndringsmelding(it) }
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.fromString("2d5fc2f7-a9e6-4830-a987-4ff135a70c10"),
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller =
                        listOf(
                            AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
                ),
            )
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.fromString("7c43b43b-43be-4d4b-8057-d907c5f1e5c5"),
                    personIdent = UUID.randomUUID().toString(),
                    fornavn = "Per",
                    mellomnavn = null,
                    etternavn = "Person",
                    roller =
                        listOf(
                            AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.MEDVEILEDER)),
                ),
            )

            val response = getWithAuthResponse<Deltaker>("/tiltaksarrangor/deltaker/$deltakerId")

            response.statusCode shouldBe HttpStatus.OK
            assertSoftly(response.body.shouldNotBeNull()) {
                id shouldBe deltaker.id
                fornavn shouldBe deltaker.fornavn
                etternavn shouldBe deltaker.etternavn
                fodselsnummer shouldBe deltaker.personident
                telefonnummer shouldBe deltaker.telefonnummer
                epost shouldBe deltaker.epost
                status.type shouldBe DeltakerStatus.Type.DELTAR
                startDato shouldBe deltaker.startdato
                dagerPerUke shouldBe deltaker.dagerPerUke
                bestillingTekst shouldBe deltaker.bestillingstekst
            }
        }
    }

    @Nested
    inner class GetDeltakerhistorikkTests {
        @Test
        fun `getDeltakerhistorikk - ikke autentisert - returnerer 401`() {
            val response = restTemplate.exchange<String>(
                url = "/tiltaksarrangor/deltaker/${UUID.randomUUID()}/historikk",
                method = HttpMethod.GET,
            )

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        @Test
        fun `getDeltakerhistorikk - har tilgang, deltaker finnes, ingen historikk - returnerer tom liste`() {
            val arrangorId = UUID.randomUUID()
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorDbo(
                    id = arrangorId,
                    navn = "Orgnavn",
                    organisasjonsnummer = "orgnummer",
                    overordnetArrangorId = null,
                ),
            )
            val deltakerliste =
                getDeltakerliste(arrangorId).copy(
                    id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                    startDato = LocalDate.of(2023, 2, 1),
                )
            deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
            val deltakerId = UUID.randomUUID()
            val gyldigFra = LocalDateTime.now()
            val deltaker =
                getDeltaker(deltakerId, deltakerliste.id).copy(
                    personident = "10987654321",
                    telefonnummer = "90909090",
                    epost = "mail@test.no",
                    status = DeltakerStatus.Type.DELTAR,
                    statusOpprettetDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
                    startdato = LocalDate.of(2023, 2, 1),
                    dagerPerUke = 2.5f,
                    innsoktDato = LocalDate.of(2023, 1, 15),
                    bestillingstekst = "Tror deltakeren vil ha nytte av dette",
                    navKontor = "Nav Oslo",
                    navVeilederId = UUID.randomUUID(),
                    navVeilederNavn = "Veileder Veiledersen",
                    navVeilederTelefon = "56565656",
                    navVeilederEpost = "epost@nav.no",
                    vurderingerFraArrangor = getVurderinger(deltakerId, gyldigFra),
                    historikk = emptyList(),
                )
            deltakerRepository.insertOrUpdateDeltaker(deltaker)

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.fromString("2d5fc2f7-a9e6-4830-a987-4ff135a70c10"),
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller =
                        listOf(
                            AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
                ),
            )

            val response = getWithAuthResponse<List<DeltakerHistorikkResponse>>(
                "/tiltaksarrangor/deltaker/$deltakerId/historikk",
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe emptyList()
        }

        @Test
        fun `getDeltakerhistorikk - har tilgang, deltaker finnes, har historikk - returnerer historikk`() {
            val arrangorId = UUID.randomUUID()
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorDbo(
                    id = arrangorId,
                    navn = "Orgnavn",
                    organisasjonsnummer = "orgnummer",
                    overordnetArrangorId = null,
                ),
            )
            val deltakerliste = getDeltakerliste(arrangorId).copy(
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                startDato = LocalDate.of(2023, 2, 1),
            )
            deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
            val ansattId = UUID.fromString("2d5fc2f7-a9e6-4830-a987-4ff135a70c10")
            val deltakerId = UUID.randomUUID()
            val gyldigFra = LocalDateTime.now()
            val endringId = UUID.fromString("fe640f60-88ef-46d8-9bc4-148aecdef6da")

            val expectedHistorikk = listOf(
                DeltakerHistorikk.EndringFraArrangor(
                    EndringFraArrangor(
                        id = endringId,
                        deltakerId = deltakerId,
                        opprettetAvArrangorAnsattId = ansattId,
                        opprettet = LocalDate.of(2023, 1, 1).atStartOfDay(),
                        endring = EndringFraArrangor.LeggTilOppstartsdato(
                            startdato = LocalDate.of(2023, 2, 1),
                            sluttdato = null,
                        ),
                    ),
                ),
            )

            val deltaker = getDeltaker(deltakerId, deltakerliste.id).copy(
                personident = "10987654321",
                telefonnummer = "90909090",
                epost = "mail@test.no",
                status = DeltakerStatus.Type.DELTAR,
                statusOpprettetDato = LocalDate.of(2023, 1, 1).atStartOfDay(),
                startdato = LocalDate.of(2023, 2, 1),
                dagerPerUke = 2.5f,
                innsoktDato = LocalDate.of(2023, 1, 15),
                bestillingstekst = "Tror deltakeren vil ha nytte av dette",
                navKontor = "Nav Oslo",
                navVeilederId = UUID.randomUUID(),
                navVeilederNavn = "Veileder Veiledersen",
                navVeilederTelefon = "56565656",
                navVeilederEpost = "epost@nav.no",
                vurderingerFraArrangor = getVurderinger(deltakerId, gyldigFra),
                historikk = expectedHistorikk,
            )
            deltakerRepository.insertOrUpdateDeltaker(deltaker)

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = ansattId,
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller =
                        listOf(
                            AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
                ),
            )

            val response = getWithAuthResponse<List<DeltakerHistorikkResponse>>(
                "/tiltaksarrangor/deltaker/$deltakerId/historikk",
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body.shouldNotBeNull().size shouldBe 1

            assertSoftly(
                response.body
                    .shouldNotBeNull()
                    .first()
                    .shouldBeInstanceOf<EndringFraArrangorResponse>(),
            ) {
                id shouldBe endringId
                arrangorNavn shouldBe "Orgnavn"
            }
        }
    }

    @Nested
    inner class RegistrerVurderingTests {
        val requestBody = RegistrerVurderingRequest(
            vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            begrunnelse = null,
        )

        @Test
        fun `registrerVurdering - ikke autentisert - returnerer 401`() {
            val response = restTemplate.postForEntity<String>(
                "/tiltaksarrangor/deltaker/${UUID.randomUUID()}/vurdering",
                requestBody,
            )

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        @Test
        fun `registrerVurdering - autentisert - returnerer 200`() {
            val deltakerId = UUID.fromString("27446cc8-30ad-4030-94e3-de438c2af3c6")
            val arrangorId = UUID.randomUUID()
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorDbo(
                    id = arrangorId,
                    navn = "Orgnavn",
                    organisasjonsnummer = "orgnummer",
                    overordnetArrangorId = null,
                ),
            )
            val deltakerliste = getDeltakerliste(arrangorId).copy(
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                startDato = LocalDate.of(2023, 2, 1),
            )
            deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
            val opprinneligVurdering = Vurdering(
                id = UUID.randomUUID(),
                deltakerId = deltakerId,
                vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
                begrunnelse = "Mangler førerkort",
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                opprettet = LocalDateTime.now().minusWeeks(1),
            )
            val deltaker = getDeltaker(deltakerId, deltakerliste.id)
                .copy(
                    personident = "10987654321",
                    status = DeltakerStatus.Type.VURDERES,
                    statusOpprettetDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
                ).copy(vurderingerFraArrangor = listOf(opprinneligVurdering))
            deltakerRepository.insertOrUpdateDeltaker(deltaker)
            val ansattId = UUID.randomUUID()
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = ansattId,
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller =
                        listOf(
                            AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
                ),
            )

            val response = postWithAuth(
                "/tiltaksarrangor/deltaker/$deltakerId/vurdering",
                requestBody,
            )

            response.statusCode shouldBe HttpStatus.OK
            val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId).shouldNotBeNull()
            deltakerFraDb.vurderingerFraArrangor.shouldNotBeNull().size shouldBe 2
        }
    }

    @Nested
    inner class FjernDeltakerTests {
        @Test
        fun `fjernDeltaker - ikke autentisert - returnerer 401`() {
            val response = restTemplate.exchange<String>(
                "/tiltaksarrangor/deltaker/${UUID.randomUUID()}",
                HttpMethod.DELETE,
            )

            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        @Test
        fun `fjernDeltaker - autentisert - returnerer 200`() {
            val deltakerId = UUID.fromString("27446cc8-30ad-4030-94e3-de438c2af3c6")
            val arrangorId = UUID.randomUUID()
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorDbo(
                    id = arrangorId,
                    navn = "Orgnavn",
                    organisasjonsnummer = "orgnummer",
                    overordnetArrangorId = null,
                ),
            )
            val deltakerliste = getDeltakerliste(arrangorId).copy(
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                startDato = LocalDate.of(2023, 2, 1),
            )
            deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
            val deltaker =
                getDeltaker(deltakerId, deltakerliste.id).copy(
                    personident = PERSONIDENT_IN_TEST,
                    status = DeltakerStatus.Type.HAR_SLUTTET,
                    statusOpprettetDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
                )
            deltakerRepository.insertOrUpdateDeltaker(deltaker)
            val ansattId = UUID.randomUUID()
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = ansattId,
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller =
                        listOf(
                            AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
                        ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
                ),
            )

            val response = deleteWithAuth(
                "/tiltaksarrangor/deltaker/$deltakerId",
            )

            response.statusCode shouldBe HttpStatus.OK

            val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId).shouldNotBeNull()
            deltakerFraDb.skjultAvAnsattId shouldBe ansattId
            deltakerFraDb.skjultDato shouldNotBe null
        }
    }

    private inline fun <reified T : Any> getWithAuthResponse(url: String) = restTemplate.exchange<T>(
        url,
        HttpMethod.GET,
        HttpEntity(
            null,
            HttpHeaders().apply {
                set(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
            },
        ),
    )

    private fun postWithAuth(
        url: String,
        body: Any,
    ) = restTemplate.exchange<String>(
        url = url,
        method = HttpMethod.POST,
        requestEntity = HttpEntity(
            body,
            HttpHeaders().apply {
                set(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
            },
        ),
    )

    private fun deleteWithAuth(url: String) = restTemplate.exchange<String>(
        url,
        HttpMethod.DELETE,
        HttpEntity<String>(
            null,
            HttpHeaders().apply {
                set(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
            },
        ),
    )

    companion object {
        private const val PERSONIDENT_IN_TEST = "12345678910"

        private fun getEndringsmeldinger(deltakerId: UUID): List<EndringsmeldingDbo> = listOf(
            EndringsmeldingDbo(
                id = UUID.fromString("27446cc8-30ad-4030-94e3-de438c2af3c6"),
                deltakerId = deltakerId,
                innhold =
                    Innhold.AvsluttDeltakelseInnhold(
                        sluttdato = LocalDate.of(2023, 3, 30),
                        aarsak =
                            DeltakerStatusAarsakJsonDboDto(
                                type = DeltakerStatus.Aarsak.Type.SYK,
                                beskrivelse = "har blitt syk",
                            ),
                    ),
                type = EndringsmeldingType.AVSLUTT_DELTAKELSE,
                status = Endringsmelding.Status.AKTIV,
                sendt = LocalDate.of(2023, 3, 30).atStartOfDay(),
            ),
            EndringsmeldingDbo(
                id = UUID.fromString("362c7fdd-04e7-4f43-9e56-0939585856eb"),
                deltakerId = deltakerId,
                innhold =
                    Innhold.EndreSluttdatoInnhold(
                        sluttdato = LocalDate.of(2023, 5, 3),
                    ),
                type = EndringsmeldingType.ENDRE_SLUTTDATO,
                status = Endringsmelding.Status.AKTIV,
                sendt = LocalDate.of(2023, 4, 3).atStartOfDay(),
            ),
            EndringsmeldingDbo(
                id = UUID.fromString("ab4d67a5-2556-4f63-b27a-ced04a231d0e"),
                deltakerId = deltakerId,
                innhold =
                    Innhold.LeggTilOppstartsdatoInnhold(
                        oppstartsdato = LocalDate.of(2022, 5, 3),
                    ),
                type = EndringsmeldingType.LEGG_TIL_OPPSTARTSDATO,
                status = Endringsmelding.Status.UTFORT,
                sendt = LocalDate.of(2022, 1, 1).atStartOfDay(),
            ),
        )
    }
}
