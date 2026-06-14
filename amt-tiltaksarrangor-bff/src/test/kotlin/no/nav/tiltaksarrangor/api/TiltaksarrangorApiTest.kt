package no.nav.tiltaksarrangor.api

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@AutoConfigureMockMvc
class TiltaksarrangorApiTest(
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val endringsmeldingRepository: EndringsmeldingRepository,
    private val arrangorRepository: ArrangorRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val mockMvc: MockMvc,
) : IntegrationTest() {
    @Nested
    inner class GetMineRollerTests {
        @Test
        fun `getMineRoller - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/meg/roller")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getMineRoller - autentisert - returnerer 200`() {
            every {
                amtArrangorClient.getAnsatt()
            } returns getMockAnsatt(personIdent = PERSONIDENT_IN_TEST)

            mockMvc
                .get("/tiltaksarrangor/meg/roller") {
                    headers { set(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}") }
                }.andExpect {
                    status { isOk() }
                    jsonPath("$[0]") { value("KOORDINATOR") }
                    jsonPath("$[1]") { value("VEILEDER") }
                }
        }
    }

    @Nested
    inner class GetDeltakerTests {
        @Test
        fun `getDeltaker - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/deltaker/${UUID.randomUUID()}")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getDeltaker - autentisert, har ikke tilgang - returnerer 403`() {
            val arrangorId = createArrangor()

            val deltakerlisteId = UUID.randomUUID()
            createDeltakerliste(arrangorId, id = deltakerlisteId)

            val deltakerId = UUID.randomUUID()
            createDeltaker(deltakerId, deltakerlisteId)

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personIdent = PERSONIDENT_IN_TEST,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller = listOf(
                        AnsattRolleDbo(
                            arrangorId = UUID.randomUUID(),
                            rolle = AnsattRolle.KOORDINATOR,
                        ),
                    ),
                    deltakerlister = emptyList(),
                    veilederDeltakere = emptyList(),
                ),
            )

            mockMvc
                .get("/tiltaksarrangor/deltaker/$deltakerId") {
                    headers { set(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}") }
                }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `getDeltaker - autentisert, har tilgang - returnerer 200`() {
            val arrangorId = createArrangor()

            val deltakerlisteId = UUID.randomUUID()
            createDeltakerliste(arrangorId, id = deltakerlisteId)

            val deltakerId = UUID.randomUUID()

            val navVeileder = NavAnsatt(
                id = UUID.randomUUID(),
                navident = "Z123456",
                navn = "Veileder Veiledersen",
                epost = "epost@nav.no",
                telefon = "56565656",
            )
            navAnsattRepository.upsert(navVeileder)

            val gyldigFra = LocalDateTime.now()
            val deltaker = getDeltaker(deltakerId, deltakerlisteId).copy(
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

            createVeileder(arrangorId, deltakerId)
            createVeileder(
                arrangorId,
                deltakerId,
                personIdent = UUID.randomUUID().toString(),
                rolle = Veiledertype.MEDVEILEDER,
            )

            mockMvc
                .get("/tiltaksarrangor/deltaker/$deltakerId") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.id") { value(deltaker.id.toString()) }
                    jsonPath("$.fornavn") { value(deltaker.fornavn) }
                    jsonPath("$.etternavn") { value(deltaker.etternavn) }
                    jsonPath("$.fodselsnummer") { value(deltaker.personident) }
                    jsonPath("$.telefonnummer") { value(deltaker.telefonnummer) }
                    jsonPath("$.epost") { value(deltaker.epost) }
                    jsonPath("$.status.type") { value("DELTAR") }
                    jsonPath("$.startDato") { value("2023-02-01") }
                    jsonPath("$.dagerPerUke") { value(2.5) }
                    jsonPath("$.bestillingTekst") { value(deltaker.bestillingstekst) }
                }
        }
    }

    @Nested
    inner class GetDeltakerhistorikkTests {
        @Test
        fun `getDeltakerhistorikk - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/deltaker/${UUID.randomUUID()}/historikk")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getDeltakerhistorikk - har tilgang, deltaker finnes, ingen historikk - returnerer tom liste`() {
            val arrangorId = createArrangor()

            val deltakerlisteId = UUID.randomUUID()
            createDeltakerliste(arrangorId, id = deltakerlisteId)

            val deltakerId = UUID.randomUUID()

            createDeltaker(deltakerId, deltakerlisteId, historikk = emptyList())
            createVeileder(arrangorId, deltakerId)

            mockMvc
                .get("/tiltaksarrangor/deltaker/$deltakerId/historikk") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$") { isArray() }
                }
        }

        @Test
        fun `getDeltakerhistorikk - har tilgang, deltaker finnes, har historikk - returnerer historikk`() {
            val ansattId = UUID.randomUUID()
            val deltakerId = UUID.randomUUID()
            val endringId = UUID.randomUUID()

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

            val arrangorId = createArrangor()
            val deltakerlisteId = UUID.randomUUID()
            createDeltakerliste(arrangorId, id = deltakerlisteId)
            createDeltaker(deltakerId, deltakerlisteId, historikk = expectedHistorikk)
            createVeileder(arrangorId, deltakerId, id = ansattId)

            mockMvc
                .get("/tiltaksarrangor/deltaker/$deltakerId/historikk") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].type") { value("EndringFraArrangor") }
                    jsonPath("$[0].id") { value(endringId.toString()) }
                    jsonPath("$[0].arrangorNavn") { value("Orgnavn") }
                }
        }
    }

    @Nested
    inner class RegistrerVurderingTests {
        @Test
        fun `registrerVurdering - ikke autentisert - returnerer 401`() {
            mockMvc
                .post("/tiltaksarrangor/deltaker/${UUID.randomUUID()}/vurdering") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `registrerVurdering - autentisert - returnerer 200`() {
            val deltakerId = UUID.randomUUID()
            val arrangorId = createArrangor()
            val deltakerlisteId = UUID.randomUUID()
            createDeltakerliste(arrangorId, id = deltakerlisteId)

            val opprinneligVurdering = Vurdering(
                id = UUID.randomUUID(),
                deltakerId = deltakerId,
                vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
                begrunnelse = "Mangler førerkort",
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                opprettet = LocalDateTime.now().minusWeeks(1),
            )

            val deltaker = getDeltaker(deltakerId, deltakerlisteId)
                .copy(
                    personident = "10987654321",
                    status = DeltakerStatus.Type.VURDERES,
                    statusOpprettetDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
                ).copy(vurderingerFraArrangor = listOf(opprinneligVurdering))
            deltakerRepository.insertOrUpdateDeltaker(deltaker)

            createVeileder(arrangorId, deltakerId)

            mockMvc
                .post("/tiltaksarrangor/deltaker/$deltakerId/vurdering") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"vurderingstype":"OPPFYLLER_KRAVENE","begrunnelse":null}"""
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
                }.andExpect { status { isOk() } }

            val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId).shouldNotBeNull()
            deltakerFraDb.vurderingerFraArrangor.shouldNotBeNull().size shouldBe 2
        }
    }

    @Nested
    inner class FjernDeltakerTests {
        @Test
        fun `fjernDeltaker - ikke autentisert - returnerer 401`() {
            mockMvc
                .delete("/tiltaksarrangor/deltaker/${UUID.randomUUID()}")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `fjernDeltaker - autentisert - returnerer 200`() {
            val deltakerId = UUID.randomUUID()
            val arrangorId = createArrangor()
            val deltakerlisteId = UUID.randomUUID()
            createDeltakerliste(arrangorId, id = deltakerlisteId)

            val deltaker = getDeltaker(deltakerId, deltakerlisteId).copy(
                personident = PERSONIDENT_IN_TEST,
                status = DeltakerStatus.Type.HAR_SLUTTET,
                statusOpprettetDato = LocalDate.of(2023, 2, 1).atStartOfDay(),
            )
            deltakerRepository.insertOrUpdateDeltaker(deltaker)

            val ansattId = UUID.randomUUID()
            createVeileder(arrangorId, deltakerId, id = ansattId)

            mockMvc
                .delete("/tiltaksarrangor/deltaker/$deltakerId") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = PERSONIDENT_IN_TEST)}")
                }.andExpect { status { isOk() } }

            val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId).shouldNotBeNull()
            deltakerFraDb.skjultAvAnsattId shouldBe ansattId
            deltakerFraDb.skjultDato shouldNotBe null
        }
    }

    // Helper functions for test setup
    private fun createArrangor(
        id: UUID = UUID.randomUUID(),
        navn: String = "Orgnavn",
        organisasjonsnummer: String = "orgnummer",
    ): UUID {
        arrangorRepository.insertOrUpdateArrangor(
            ArrangorDbo(
                id = id,
                navn = navn,
                organisasjonsnummer = organisasjonsnummer,
                overordnetArrangorId = null,
            ),
        )
        return id
    }

    private fun createDeltakerliste(
        arrangorId: UUID,
        id: UUID = UUID.randomUUID(),
        startDato: LocalDate = LocalDate.of(2023, 2, 1),
    ) {
        val deltakerliste = getDeltakerliste(arrangorId).copy(
            id = id,
            startDato = startDato,
        )
        deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
    }

    private fun createDeltaker(
        deltakerId: UUID,
        deltakerlisteId: UUID,
        personident: String = "10987654321",
        status: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        historikk: List<DeltakerHistorikk> = emptyList(),
    ) {
        val gyldigFra = LocalDateTime.now()
        val deltaker = getDeltaker(deltakerId, deltakerlisteId).copy(
            personident = personident,
            telefonnummer = "90909090",
            epost = "mail@test.no",
            status = status,
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
            historikk = historikk,
        )
        deltakerRepository.insertOrUpdateDeltaker(deltaker)
    }

    private fun createVeileder(
        arrangorId: UUID,
        deltakerId: UUID,
        id: UUID = UUID.randomUUID(),
        personIdent: String = PERSONIDENT_IN_TEST,
        rolle: Veiledertype = Veiledertype.VEILEDER,
    ) {
        tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
            AnsattDbo(
                id = id,
                personIdent = personIdent,
                fornavn = "Fornavn",
                mellomnavn = null,
                etternavn = "Etternavn",
                roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
                deltakerlister = emptyList(),
                veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, rolle)),
            ),
        )
    }

    companion object {
        private const val PERSONIDENT_IN_TEST = "12345678910"

        private fun getEndringsmeldinger(deltakerId: UUID): List<EndringsmeldingDbo> = listOf(
            EndringsmeldingDbo(
                id = UUID.randomUUID(),
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
                id = UUID.randomUUID(),
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
                id = UUID.randomUUID(),
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
