package no.nav.tiltaksarrangor.koordinator.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.consumer.model.AdresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.koordinator.model.LeggTilVeiledereRequest
import no.nav.tiltaksarrangor.koordinator.model.VeilederRequest
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.testutils.getAdresse
import no.nav.tiltaksarrangor.testutils.getDeltaker
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@AutoConfigureMockMvc
class KoordinatorApiTest(
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val arrangorRepository: ArrangorRepository,
    private val mockMvc: MockMvc,
) : IntegrationTestBase() {
    @Nested
    inner class GetMineDeltakerlistersTests {
        @Test
        fun `getMineDeltakerlister - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/koordinator/mine-deltakerlister")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getMineDeltakerlister - autentisert - returnerer 200`() {
            val personIdent = "12345678910"
            val arrangorId = UUID.randomUUID()
            val deltakerliste = createDeltakerliste(
                arrangorId,
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                navn = "Gjennomføring 1",
                tiltaksnavn = "Tiltaksnavnet",
                startDato = LocalDate.of(2024, 1, 3),
            )

            val deltakerId1 = UUID.randomUUID()
            val deltakerId2 = UUID.randomUUID()
            val deltakerId3 = UUID.randomUUID()
            val deltakerId4 = UUID.randomUUID()
            val deltakerId5 = UUID.randomUUID()
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId1, deltakerliste.id))
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId2, deltakerliste.id))
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId3, deltakerliste.id))
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId4, deltakerliste.id))
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId5, deltakerliste.id))

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personIdent = personIdent,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR), AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER)),
                    deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
                    veilederDeltakere = listOf(
                        VeilederDeltakerDbo(deltakerId1, Veiledertype.VEILEDER),
                        VeilederDeltakerDbo(deltakerId2, Veiledertype.MEDVEILEDER),
                        VeilederDeltakerDbo(deltakerId3, Veiledertype.MEDVEILEDER),
                        VeilederDeltakerDbo(deltakerId4, Veiledertype.VEILEDER),
                        VeilederDeltakerDbo(deltakerId5, Veiledertype.MEDVEILEDER),
                    ),
                ),
            )

            mockMvc
                .get("/tiltaksarrangor/koordinator/mine-deltakerlister") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.veilederFor.veilederFor") { value(2) }
                    jsonPath("$.veilederFor.medveilederFor") { value(3) }
                    jsonPath("$.koordinatorFor.deltakerlister.length()") { value(1) }
                    jsonPath("$.koordinatorFor.deltakerlister[0].id") { value("9987432c-e336-4b3b-b73e-b7c781a0823a") }
                    jsonPath("$.koordinatorFor.deltakerlister[0].navn") { value("Gjennomføring 1") }
                }
        }
    }

    @Nested
    inner class GetTilgjengeligeVeiledereTests {
        @Test
        fun `getTilgjengeligeVeiledere - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/koordinator/${UUID.randomUUID()}/veiledere")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getTilgjengeligeVeiledere - autentisert - returnerer 200`() {
            val personIdent = "12345678910"
            val arrangorId = UUID.randomUUID()
            val deltakerliste = createDeltakerliste(
                arrangorId,
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                navn = "Gjennomføring 1",
                tiltaksnavn = "Tiltaksnavnet",
            )

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personIdent = personIdent,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
                    deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
                    veilederDeltakere = emptyList(),
                ),
            )

            createAnsatt(
                arrangorId,
                id = UUID.fromString("29bf6799-bb56-4a86-857b-99b529b3dfc4"),
                fornavn = "Fornavn1",
                etternavn = "Etternavn1",
                rolle = AnsattRolle.VEILEDER,
            )

            createAnsatt(
                arrangorId,
                id = UUID.fromString("e824dbfe-5317-491b-82ed-03b870eed963"),
                fornavn = "Fornavn2",
                etternavn = "Etternavn2",
                rolle = AnsattRolle.VEILEDER,
                veilederDeltakere = listOf(VeilederDeltakerDbo(UUID.randomUUID(), Veiledertype.MEDVEILEDER)),
            )

            mockMvc
                .get("/tiltaksarrangor/koordinator/${deltakerliste.id}/veiledere") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                    jsonPath("$[0].ansattId") { value("29bf6799-bb56-4a86-857b-99b529b3dfc4") }
                    jsonPath("$[0].fornavn") { value("Fornavn1") }
                    jsonPath("$[1].ansattId") { value("e824dbfe-5317-491b-82ed-03b870eed963") }
                    jsonPath("$[1].fornavn") { value("Fornavn2") }
                }
        }
    }

    @Nested
    inner class TildelVeiledereForDeltakerTests {
        @Test
        fun `tildelVeiledereForDeltaker - ikke autentisert - returnerer 401`() {
            mockMvc
                .post("/tiltaksarrangor/koordinator/veiledere?deltakerId=${UUID.randomUUID()}") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `tildelVeiledereForDeltaker - autentisert, tildeler veiledere - returnerer 200`() {
            val personIdent = "12345678910"
            val arrangorId = UUID.randomUUID()
            val deltakerliste = createDeltakerliste(arrangorId, navn = "Gjennomføring 1")
            val deltakerId = UUID.fromString("da4c9568-cea2-42e3-95a3-42f6b809ad08")
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id))

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personIdent = personIdent,
                    fornavn = "Fornavn",
                    mellomnavn = null,
                    etternavn = "Etternavn",
                    roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
                    deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
                    veilederDeltakere = emptyList(),
                ),
            )

            val veileder1Id = UUID.randomUUID()
            createAnsatt(
                arrangorId,
                id = veileder1Id,
                fornavn = "Fornavn1",
                etternavn = "Etternavn1",
                rolle = AnsattRolle.VEILEDER,
            )

            val veileder2Id = UUID.randomUUID()
            createAnsatt(
                arrangorId,
                id = veileder2Id,
                fornavn = "Fornavn2",
                etternavn = "Etternavn2",
                rolle = AnsattRolle.VEILEDER,
            )

            every {
                amtArrangorClient.oppdaterVeilederForDeltaker(
                    deltakerId = any(),
                    oppdaterVeiledereForDeltakerRequest = any(),
                )
            } just Runs

            val requestBody = LeggTilVeiledereRequest(
                listOf(
                    VeilederRequest(ansattId = veileder1Id, erMedveileder = false),
                    VeilederRequest(ansattId = veileder2Id, erMedveileder = true),
                ),
            )

            mockMvc
                .post("/tiltaksarrangor/koordinator/veiledere?deltakerId=$deltakerId") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(requestBody)
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect { status { isOk() } }

            val veileder1 = tiltaksarrangorAnsattRepository.getAnsatt(veileder1Id)
            veileder1?.veilederDeltakere?.size shouldBe 1
            veileder1?.veilederDeltakere?.find { it.deltakerId == deltakerId && it.veilederType == Veiledertype.VEILEDER } shouldNotBe null

            val veileder2 = tiltaksarrangorAnsattRepository.getAnsatt(veileder2Id)
            veileder2?.veilederDeltakere?.size shouldBe 1
            veileder2?.veilederDeltakere?.find { it.deltakerId == deltakerId && it.veilederType == Veiledertype.MEDVEILEDER } shouldNotBe
                null
        }
    }

    @Nested
    inner class GetDeltakerlisteTests {
        @Test
        fun `getDeltakerliste - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/koordinator/deltakerliste/${UUID.randomUUID()}")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getDeltakerliste - autentisert - returnerer 200`() {
            val personIdent = "12345678910"
            val deltakerlisteId = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a")
            val arrangorId = createArrangor()

            createDeltakerliste(
                arrangorId,
                id = deltakerlisteId,
                navn = "Gjennomføring 1",
                tiltaksnavn = "Navn på tiltak",
                startDato = LocalDate.of(2023, 2, 1),
                tilgjengeligForArrangorFraOgMedDato = LocalDate.of(2023, 1, 1),
                pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
            )

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
                AnsattDbo(
                    id = UUID.randomUUID(),
                    personIdent = personIdent,
                    fornavn = "Fornavn1",
                    mellomnavn = null,
                    etternavn = "Etternavn1",
                    roller = listOf(AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR)),
                    deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerlisteId)),
                    veilederDeltakere = emptyList(),
                ),
            )

            createAnsatt(
                arrangorId,
                fornavn = "Fornavn2",
                etternavn = "Etternavn2",
                rolle = AnsattRolle.KOORDINATOR,
                deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerlisteId)),
            )

            val deltaker = DeltakerDbo(
                id = UUID.fromString("252428ac-37a6-4341-bb17-5bad412c9409"),
                deltakerlisteId = deltakerlisteId,
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
                startdato = LocalDate.of(2023, 2, 1),
                sluttdato = null,
                innsoktDato = LocalDate.of(2023, 1, 15),
                bestillingstekst = "tekst",
                navKontor = "NAV Testheim",
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
                sistEndret = LocalDateTime.now(),
                forsteVedtakFattet = LocalDate.of(2023, 1, 15),
                erManueltDeltMedArrangor = false,
            )
            deltakerRepository.insertOrUpdateDeltaker(deltaker)

            mockMvc
                .get("/tiltaksarrangor/koordinator/deltakerliste/$deltakerlisteId") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.id") { value("9987432c-e336-4b3b-b73e-b7c781a0823a") }
                    jsonPath("$.navn") { value("Gjennomføring 1") }
                    jsonPath("$.tiltaksnavn") { value("Navn på tiltak") }
                    jsonPath("$.koordinatorer.length()") { value(2) }
                    jsonPath("$.deltakere.length()") { value(1) }
                    jsonPath("$.deltakere[0].id") { value("252428ac-37a6-4341-bb17-5bad412c9409") }
                    jsonPath("$.deltakere[0].fornavn") { value("Fornavn") }
                }
        }
    }

    // Helper functions for test setup
    private fun createArrangor(
        id: UUID = UUID.randomUUID(),
        navn: String = "Arrangør AS",
        organisasjonsnummer: String = "88888888",
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
        navn: String = "Gjennomføring",
        tiltaksnavn: String = "Tiltak",
        tiltakskode: Tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
        startDato: LocalDate = LocalDate.now(),
        sluttDato: LocalDate? = null,
        tilgjengeligForArrangorFraOgMedDato: LocalDate? = null,
        pameldingstype: GjennomforingPameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
    ): DeltakerlisteDbo {
        val deltakerliste = DeltakerlisteDbo(
            id = id,
            lopenummer = "2026-001",
            navn = navn,
            gjennomforingstype = GjennomforingType.Gruppe,
            status = GjennomforingStatusType.GJENNOMFORES,
            arrangorId = arrangorId,
            tiltaksnavn = tiltaksnavn,
            tiltakskode = tiltakskode,
            startDato = startDato,
            sluttDato = sluttDato,
            erKurs = false,
            oppstartstype = Oppstartstype.LOPENDE,
            tilgjengeligForArrangorFraOgMedDato = tilgjengeligForArrangorFraOgMedDato,
            pameldingstype = pameldingstype,
        )
        deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
        return deltakerliste
    }

    private fun createAnsatt(
        arrangorId: UUID,
        id: UUID = UUID.randomUUID(),
        fornavn: String = "Fornavn",
        mellomnavn: String? = null,
        etternavn: String = "Etternavn",
        rolle: AnsattRolle = AnsattRolle.VEILEDER,
        deltakerlister: List<KoordinatorDeltakerlisteDbo> = emptyList(),
        veilederDeltakere: List<VeilederDeltakerDbo> = emptyList(),
    ): AnsattDbo {
        val ansatt = AnsattDbo(
            id = id,
            personIdent = UUID.randomUUID().toString(),
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            roller = listOf(AnsattRolleDbo(arrangorId, rolle)),
            deltakerlister = deltakerlister,
            veilederDeltakere = veilederDeltakere,
        )
        tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(ansatt)
        return ansatt
    }
}
