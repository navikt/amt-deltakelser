package no.nav.amt.distribusjon.hendelse

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.IntegrationTestBase
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.HendelseDto
import no.nav.amt.distribusjon.hendelse.model.toModel
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Persondata.lagNavBruker
import no.nav.amt.distribusjon.utils.data.Varselsdata
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.distribusjon.varsel.model.beskjedTekst
import no.nav.amt.distribusjon.varsel.model.oppgaveTekst
import no.nav.amt.distribusjon.varsel.nowUTC
import no.nav.amt.distribusjon.varsel.skalVarslesEksternt
import no.nav.amt.distribusjon.veilarboppfolging.Sak
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class HendelseConsumerTest : IntegrationTestBase() {
    @BeforeEach
    fun setupMocks() {
        coEvery { amtPersonClient.hentNavBruker(any()) } returns lagNavBruker()
        coEvery { pdfgenClient.genererHovedvedtakForIndividuellOppfolging(any()) } returns "pdf".toByteArray()
        coEvery { veilarboppfolgingClient.opprettEllerHentSak(any()) } returns Sak(
            oppfolgingsperiodeId = UUID.randomUUID(),
            sakId = 42L,
            fagsaksystem = "~fagsaksystem~",
        )
        coEvery {
            dokarkivClient.opprettJournalpost(any(), any(), any(), any(), any(), any())
        } returns "journalpostId"
    }

    @Nested
    inner class OpprettUtkastTests {
        @Test
        fun `opprettUtkast - oppretter nytt varsel og produserer`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.opprettUtkast())

            // Act
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            // Assert
            val varsel = varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.OPPGAVE,
                ).shouldBeSuccess()

            assertSoftly(varsel) {
                aktivTil shouldBe null
                tekst shouldBe oppgaveTekst(hendelse.toModel(Distribusjonskanal.DITT_NAV, false))
                aktivFra shouldBeCloseTo nowUTC()
                deltakerId shouldBe hendelse.deltaker.id
                personident shouldBe hendelse.deltaker.personident
                erEksterntVarsel shouldBe hendelse.skalVarslesEksternt()
            }
        }

        @Test
        fun `opprettUtkast - hendelsen er håndtert tidligere - sender ikke nytt varsel`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.opprettUtkast())
            val forrigeVarsel = Varselsdata.varsel(
                Varsel.Type.OPPGAVE,
                hendelser = listOf(hendelse.id),
                aktivFra = nowUTC().minusMinutes(30),
                aktivTil = nowUTC().minusMinutes(20),
                deltakerId = hendelse.deltaker.id,
            )
            varselRepository.upsert(forrigeVarsel)

            // Act
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            // Assert
            val varsel = varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.OPPGAVE,
                ).shouldBeSuccess()

            varsel.erAktiv shouldBe false

            verify(exactly = 0) { outboxService.insertRecord(varsel.id, any(), any(), any()) }
        }
    }

    @Nested
    inner class NavGodkjennUtkastTests {
        @Test
        fun `navGodkjennUtkast - ingen tidligere varsel - oppretter beskjed`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.navGodkjennUtkast())

            // Act
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            assertNyBeskjed(hendelse, nowUTC())
        }

        @Test
        fun `navGodkjennUtkast - tidligere varsel - inaktiverer varsel og oppretter beskjed`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.navGodkjennUtkast())

            val forrigeVarsel = Varselsdata.varsel(
                type = Varsel.Type.OPPGAVE,
                status = Varsel.Status.AKTIV,
                aktivFra = nowUTC().minusDays(1),
                deltakerId = hendelse.deltaker.id,
            )
            varselRepository.upsert(forrigeVarsel)

            // Act
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            // Assert
            assertNyBeskjed(hendelse, nowUTC())

            val inaktivertVarsel = varselRepository.get(forrigeVarsel.id).shouldBeSuccess()
            assertSoftly(inaktivertVarsel) {
                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }

            verify { outboxService.insertRecord(inaktivertVarsel.id, any(), any(), any()) }
        }
    }

    @Test
    fun `avbrytUtkast - varsel er aktivt - inaktiverer varsel og produserer`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.avbrytUtkast())
        val forrigeVarsel = Varselsdata.varsel(
            Varsel.Type.OPPGAVE,
            Varsel.Status.AKTIV,
            aktivFra = nowUTC().minusDays(1),
            deltakerId = hendelse.deltaker.id,
        )
        varselRepository.upsert(forrigeVarsel)

        // Act*
        hendelseConsumer.consume(
            key = hendelse.id,
            value = objectMapper.writeValueAsString(hendelse),
        )

        // Assert
        val varsel = varselRepository
            .getSisteVarsel(
                deltakerId = hendelse.deltaker.id,
                type = Varsel.Type.OPPGAVE,
            ).shouldBeSuccess()

        assertSoftly(varsel) {
            id shouldBe forrigeVarsel.id

            aktivTil.shouldNotBeNull()
            aktivTil shouldBeCloseTo nowUTC()
        }

        verify { outboxService.insertRecord(forrigeVarsel.id, any(), any(), any()) }
    }

    @Test
    fun `innbyggerGodkjennerUtkast - inaktiverer varsel`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.innbyggerGodkjennUtkast())
        val forrigeVarsel = Varselsdata.varsel(
            type = Varsel.Type.OPPGAVE,
            status = Varsel.Status.AKTIV,
            aktivFra = nowUTC().minusDays(1),
            deltakerId = hendelse.deltaker.id,
        )
        varselRepository.upsert(forrigeVarsel)

        // Act*
        hendelseConsumer.consume(
            key = hendelse.id,
            value = objectMapper.writeValueAsString(hendelse),
        )

        // Assert
        val varsel = varselRepository
            .getSisteVarsel(
                deltakerId = hendelse.deltaker.id,
                type = Varsel.Type.OPPGAVE,
            ).shouldBeSuccess()

        assertSoftly(varsel) {
            id shouldBe forrigeVarsel.id

            aktivTil.shouldNotBeNull()
            aktivTil shouldBeCloseTo nowUTC()
        }

        verify { outboxService.insertRecord(varsel.id, any(), any(), any()) }
    }

    @Test
    fun `endreSluttdato - ingen tidligere varsel - oppretter forsinket varsel`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.endreSluttdato())

        // Act*
        hendelseConsumer.consume(
            key = hendelse.id,
            value = objectMapper.writeValueAsString(hendelse),
        )

        // Assert
        assertNyBeskjed(
            hendelse = hendelse,
            aktivFra = Varsel.nesteUtsendingstidspunkt(),
        )
    }

    @Test
    fun `endreStartdato - ingen tidligere varsel - oppretter forsinket varsel`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.endreStartdato())

        // Act*
        hendelseConsumer.consume(
            key = hendelse.id,
            value = objectMapper.writeValueAsString(hendelse),
        )

        // Assert
        assertNyBeskjed(hendelse, Varsel.nesteUtsendingstidspunkt())
    }

    @Nested
    inner class DeltakerSistBesoktTests {
        @Test
        fun `deltakerSistBesokt - aktiv beskjed - inaktiverer`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.sistBesokt())
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.AKTIV,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().minusMinutes(1),
            )
            varselRepository.upsert(varsel)

            // Act*
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            // Assert
            val oppdatertVarsel = varselRepository.get(varsel.id).shouldBeSuccess()
            assertSoftly(oppdatertVarsel) {
                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }

            verify { outboxService.insertRecord(varsel.id, any(), any(), any()) }
        }

        @Test
        fun `deltakerSistBesokt - beskjed venter på å bli sendt - inaktiverer`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.sistBesokt())
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().plusMinutes(10),
            )
            varselRepository.upsert(varsel)

            // Act*
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            // Assert
            val oppdatertVarsel = varselRepository.get(varsel.id).shouldBeSuccess()
            assertSoftly(oppdatertVarsel) {
                status shouldBe Varsel.Status.UTFORT
                aktivFra shouldBeCloseTo nowUTC()

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }
        }

        @Test
        fun `deltakerSistBesokt - to beskjeder, en aktiv og en venter - inaktiverer begge`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.sistBesokt())
            val aktivtVarsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.AKTIV,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().minusMinutes(1),
            )
            varselRepository.upsert(aktivtVarsel)

            val ventendeVarsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().plusMinutes(10),
            )
            varselRepository.upsert(ventendeVarsel)

            // Act
            hendelseConsumer.consume(
                key = hendelse.id,
                value = objectMapper.writeValueAsString(hendelse),
            )

            // Assert
            assertSoftly(varselRepository.get(aktivtVarsel.id).shouldBeSuccess()) {
                status shouldBe Varsel.Status.UTFORT
                aktivFra shouldBeCloseTo aktivtVarsel.aktivFra

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }

            assertSoftly(varselRepository.get(ventendeVarsel.id).shouldBeSuccess()) {
                status shouldBe Varsel.Status.UTFORT
                aktivFra shouldBeCloseTo nowUTC()

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }

            verify { outboxService.insertRecord(aktivtVarsel.id, any(), any(), any()) }
        }
    }

    private fun assertNyBeskjed(
        hendelse: HendelseDto,
        aktivFra: ZonedDateTime,
    ) {
        val varsel = varselRepository
            .getSisteVarsel(
                deltakerId = hendelse.deltaker.id,
                type = Varsel.Type.BESKJED,
            ).shouldBeSuccess()

        assertSoftly(varsel) {
            aktivTil.shouldNotBeNull()
            aktivTil shouldBeCloseTo Varsel.nesteUtsendingstidspunkt().plus(Varsel.beskjedAktivLengde)

            tekst shouldBe beskjedTekst(hendelse.toModel(Distribusjonskanal.DITT_NAV, false))
            it.aktivFra shouldBeCloseTo aktivFra
            deltakerId shouldBe hendelse.deltaker.id
            personident shouldBe hendelse.deltaker.personident

            erEksterntVarsel shouldBe hendelse.skalVarslesEksternt()

            if (erAktiv) {
                verify {
                    outboxService.insertRecord(varsel.id, any(), any(), any())
                }
            }
        }
    }

    companion object {
        fun HendelseDto.skalVarslesEksternt() = this.toModel(Distribusjonskanal.DITT_NAV, false).skalVarslesEksternt()
    }
}
