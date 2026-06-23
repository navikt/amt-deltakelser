package no.nav.tiltaksarrangor.koordinator.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.util.UUID

@AutoConfigureMockMvc
class DeltakerlisteAdminApiTest(
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val arrangorRepository: ArrangorRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val mockMvc: MockMvc,
) : IntegrationTest() {
    @Nested
    inner class GetAlleDeltakerlisterTests {
        @Test
        fun `getAlleDeltakerlister - ikke autentisert - returnerer 401`() {
            mockMvc
                .get("/tiltaksarrangor/koordinator/admin/deltakerlister")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `getAlleDeltakerlister - autentisert - returnerer 200`() {
            val personIdent = "12345678910"
            val arrangorId = createArrangor()

            val deltakerliste1 = createDeltakerliste(
                arrangorId,
                id = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a"),
                navn = "Gjennomføring 1",
                tiltaksnavn = "Navn på tiltak",
                tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                startDato = LocalDate.of(2023, 2, 1),
            )

            createDeltakerliste(
                arrangorId,
                id = UUID.fromString("fd70758a-44c5-4868-bdcb-b1ddd26cb5e9"),
                navn = "Gjennomføring 2",
                tiltaksnavn = "Navn på tiltak",
                tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
                startDato = LocalDate.of(2023, 3, 1),
            )

            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
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

            mockMvc
                .get("/tiltaksarrangor/koordinator/admin/deltakerlister") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                    jsonPath("$[0].id") { value("9987432c-e336-4b3b-b73e-b7c781a0823a") }
                    jsonPath("$[0].navn") { value("Gjennomføring 1") }
                    jsonPath("$[0].lagtTil") { value(true) }
                    jsonPath("$[1].id") { value("fd70758a-44c5-4868-bdcb-b1ddd26cb5e9") }
                    jsonPath("$[1].navn") { value("Gjennomføring 2") }
                    jsonPath("$[1].lagtTil") { value(false) }
                }
        }
    }

    @Nested
    inner class LeggTilDeltakerlisteTests {
        @Test
        fun `leggTilDeltakerliste - ikke autentisert - returnerer 401`() {
            mockMvc
                .post("/tiltaksarrangor/koordinator/admin/deltakerliste/${UUID.randomUUID()}")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `leggTilDeltakerliste - autentisert - returnerer 200`() {
            val personIdent = "12345678910"
            val deltakerlisteId = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a")
            val arrangorId = createArrangor()

            createDeltakerliste(
                arrangorId,
                id = deltakerlisteId,
                navn = "Gjennomføring 1",
                tiltaksnavn = "Navn på tiltak",
            )

            val ansattId = UUID.randomUUID()
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
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

            every {
                amtArrangorClient.leggTilDeltakerlisteForKoordinator(
                    ansattId = ansattId,
                    arrangorId = arrangorId,
                    deltakerlisteId = deltakerlisteId,
                )
            } just Runs

            mockMvc
                .post("/tiltaksarrangor/koordinator/admin/deltakerliste/$deltakerlisteId") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect { status { isOk() } }

            val ansattFraDb = tiltaksarrangorAnsattRepository.getAnsatt(ansattId)
            ansattFraDb?.deltakerlister?.size shouldBe 1
            ansattFraDb?.deltakerlister?.find { it.deltakerlisteId == deltakerlisteId } shouldNotBe null
        }
    }

    @Nested
    inner class FjernDeltakerlisteTests {
        @Test
        fun `fjernDeltakerliste - ikke autentisert - returnerer 401`() {
            mockMvc
                .delete("/tiltaksarrangor/koordinator/admin/deltakerliste/${UUID.randomUUID()}")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `fjernDeltakerliste - autentisert - returnerer 200`() {
            val personIdent = "12345678910"
            val deltakerlisteId = UUID.fromString("9987432c-e336-4b3b-b73e-b7c781a0823a")
            val arrangorId = createArrangor()

            createDeltakerliste(
                arrangorId,
                id = deltakerlisteId,
                navn = "Gjennomføring 1",
                tiltaksnavn = "Navn på tiltak",
            )

            val ansattId = UUID.randomUUID()
            tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(
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

            every {
                amtArrangorClient.fjernDeltakerlisteForKoordinator(
                    ansattId = ansattId,
                    arrangorId = arrangorId,
                    deltakerlisteId = deltakerlisteId,
                )
            } just Runs

            mockMvc
                .delete("/tiltaksarrangor/koordinator/admin/deltakerliste/$deltakerlisteId") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${getTokenxToken(fnr = personIdent)}")
                }.andExpect { status { isOk() } }

            val ansattFraDb = tiltaksarrangorAnsattRepository.getAnsatt(ansattId)
            ansattFraDb?.deltakerlister?.size shouldBe 0
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
        startDato: LocalDate = LocalDate.of(2023, 1, 1),
        sluttDato: LocalDate? = null,
        tilgjengeligForArrangorFraOgMedDato: LocalDate? = null,
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
            pameldingstype = GjennomforingPameldingType.TRENGER_GODKJENNING,
        )
        deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
        return deltakerliste
    }
}
