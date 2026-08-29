package no.nav.amt.distribusjon.varsel

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.IntegrationTestBase
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Varselsdata
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class VarselServiceTest : IntegrationTestBase() {
    private fun setupMocks(hendelse: Hendelse) {
        coEvery {
            dokdistkanalClient.bestemDistribusjonskanal(hendelse.deltaker.personident, hendelse.deltaker.id)
        } returns Distribusjonskanal.DITT_NAV

        coEvery { veilarboppfolgingClient.erUnderManuellOppfolging(hendelse.deltaker.personident) } returns false
    }

    @Nested
    inner class HandleHendelseTests {
        @Test
        fun `navGodkjennUtkast - innbyggers distribusjonskanal er ikke digital - oppretter ikke varsel`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.navGodkjennUtkast(),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )

            setupMocks(hendelse)

            // Act
            varselService.handleHendelse(hendelse)

            // Assert
            varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.BESKJED,
                ).shouldBeFailure()
        }

        @Test
        fun `navGodkjennUtkast - nnbyggers er under manuell oppfolging - oppretter ikke varsel`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                HendelseTypeData.navGodkjennUtkast(),
                distribusjonskanal = Distribusjonskanal.SDP,
                manuellOppfolging = true,
            )

            setupMocks(hendelse)

            // Act
            varselService.handleHendelse(hendelse)

            // Assert
            varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.BESKJED,
                ).shouldBeFailure()
        }

        //  stopper revarsling av tidligere varsel
        @Test
        fun `avsluttDeltakelse - nytt varsel med ekstern varsling, tidligere varsel skal revarsles`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.avsluttDeltakelse(),
                deltaker = Hendelsesdata.lagDeltaker(deltakerId),
            )

            val forrigeVarsel = Varselsdata.beskjed(
                status = Varsel.Status.INAKTIVERT,
                deltakerId = deltakerId,
                aktivFra = nowUTC().minusDays(6),
                aktivTil = nowUTC().plusDays(3),
                revarsles = nowUTC().plusDays(1),
            )
            varselRepository.upsert(forrigeVarsel)

            setupMocks(hendelse)

            // Act
            varselService.handleHendelse(hendelse)

            // Assert
            varselRepository
                .get(forrigeVarsel.id)
                .shouldBeSuccess()
                .revarsles shouldBe null
        }

        @Test
        fun `enkeltplassEndreOpplaringKategorisering - inaktiverer oppgave og oppretter ekstern beskjed`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val oppgave = Varselsdata.varsel(
                type = Varsel.Type.OPPGAVE,
                status = Varsel.Status.AKTIV,
                deltakerId = deltakerId,
            )
            varselRepository.upsert(oppgave)

            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.enkeltplassEndreOpplaringKategorisering(),
                deltaker = Hendelsesdata.lagDeltaker(deltakerId),
            )

            setupMocks(hendelse)

            // Act
            varselService.handleHendelse(hendelse)

            // Assert
            assertSoftly(varselRepository.getSisteVarsel(deltakerId, Varsel.Type.OPPGAVE).shouldBeSuccess()) {
                status shouldBe Varsel.Status.INAKTIVERT
            }

            assertSoftly(varselRepository.getSisteVarsel(deltakerId, Varsel.Type.BESKJED).shouldBeSuccess()) {
                type shouldBe Varsel.Type.BESKJED
                status shouldBe Varsel.Status.AKTIV
                erEksterntVarsel shouldBe true
                revarsles.shouldNotBeNull()
            }

            verify { outboxService.insertRecord(oppgave.id, any(), any()) }
        }

        @Test
        fun `deltakerSistBesokt - siste besøk er før beskjed var sendt - inaktiverer ikke`() {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.sistBesokt(sistBesokt = ZonedDateTime.now().minusMinutes(10)),
            )
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.AKTIV,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC(),
                aktivTil = nowUTC().plus(Varsel.beskjedAktivLengde),
            )
            varselRepository.upsert(varsel)

            setupMocks(hendelse)

            // Act
            varselService.handleHendelse(hendelse)

            // Assert
            assertSoftly(varselRepository.get(varsel.id).shouldBeSuccess()) {
                status shouldBe Varsel.Status.AKTIV
                aktivFra shouldBeCloseTo varsel.aktivFra

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo varsel.aktivTil
            }
        }
    }

    @Nested
    inner class SendVentendeVarslerTests {
        @Test
        fun `sendVentendeVarsler - varsler er klare for sending - sender`() = runTest {
            // Arrange
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                aktivFra = nowUTC().minusMinutes(5),
            )
            varselRepository.upsert(varsel)

            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.endreDeltakelsesmengde(),
                id = varsel.hendelser.first(),
            )
            hendelseRepository.insert(hendelse)

            // val forventetUrl = innbyggerDeltakerUrl(varsel.deltakerId, true)

            // Act
            varselService.sendVentendeVarsler()

            // Assert
            val oppdatertVarsel = varselRepository.get(varsel.id).shouldBeSuccess()
            oppdatertVarsel.aktivFra shouldBeCloseTo nowUTC()

            verify { outboxService.insertRecord(varsel.id, any(), any(), any()) }
        }

        @Test
        fun `sendVentendeVarsler - varsler er ikke klare for sending - sender ikke`() = runTest {
            // Arrange
            val varsel = Varselsdata.varsel(
                Varsel.Type.BESKJED,
                Varsel.Status.VENTER_PA_UTSENDELSE,
                aktivFra = nowUTC().plusMinutes(5),
            )
            varselRepository.upsert(varsel)

            // Act
            varselService.sendVentendeVarsler()

            // Assert
            val oppdatertVarsel = varselRepository.get(varsel.id).shouldBeSuccess()
            oppdatertVarsel.aktivFra shouldBeCloseTo varsel.aktivFra
        }

        @Test
        fun `sendVentendeVarsler - varsler klar for sending, det finnes ett aktivt varsel fra før - inaktiverer og sender nytt`() =
            runTest {
                // Arrange
                val deltakerId = UUID.randomUUID()
                val aktivtVarsel = Varselsdata.varsel(
                    type = Varsel.Type.BESKJED,
                    status = Varsel.Status.AKTIV,
                    deltakerId = deltakerId,
                    aktivFra = nowUTC().minusMinutes(35),
                )
                varselRepository.upsert(aktivtVarsel)

                val nyttVarsel = Varselsdata.varsel(
                    type = Varsel.Type.BESKJED,
                    status = Varsel.Status.VENTER_PA_UTSENDELSE,
                    deltakerId = deltakerId,
                    aktivFra = nowUTC().minusMinutes(5),
                )
                varselRepository.upsert(nyttVarsel)

                val hendelse = Hendelsesdata.hendelse(
                    payload = HendelseTypeData.endreInnhold(),
                    id = nyttVarsel.hendelser.first(),
                )
                hendelseRepository.insert(hendelse)

                // val forventetUrl = innbyggerDeltakerUrl(nyttVarsel.deltakerId, true)

                // Act
                varselService.sendVentendeVarsler()

                // Assert
                varselRepository
                    .get(aktivtVarsel.id)
                    .shouldBeSuccess()
                    .erAktiv shouldBe false

                val oppdatertVarsel = varselRepository.get(nyttVarsel.id).shouldBeSuccess()
                oppdatertVarsel.aktivFra shouldBeCloseTo nowUTC()

                verify { outboxService.insertRecord(aktivtVarsel.id, any(), any(), any()) }
                verify { outboxService.insertRecord(nyttVarsel.id, any(), any(), any()) }
            }

        @Nested
        inner class SendRevarslerTests {
            @Test
            fun `sendRevarsler - inaktivert beskjed skal revarsles - oppretter og sender revarsel`() = runTest {
                // Arrange
                val skalRevarsles = Varselsdata.beskjed(
                    status = Varsel.Status.INAKTIVERT,
                    aktivFra = nowUTC().minusDays(7).plusMinutes(1),
                    revarsles = nowUTC().minusMinutes(1),
                )
                varselRepository.upsert(skalRevarsles)

                val hendelse = Hendelsesdata.hendelse(
                    payload = HendelseTypeData.navGodkjennUtkast(),
                    id = skalRevarsles.hendelser.first(),
                )
                hendelseRepository.insert(hendelse)

                // val forventetUrl = innbyggerDeltakerUrl(skalRevarsles.deltakerId, false)

                // Act
                varselService.sendRevarsler()

                // Assert
                varselRepository
                    .get(skalRevarsles.id)
                    .shouldBeSuccess()
                    .revarsles shouldBe null

                val revarsel = varselRepository.getAktivt(skalRevarsles.deltakerId).shouldBeSuccess()
                assertSoftly(revarsel) {
                    erRevarsel shouldBe true
                    kanRevarsles shouldBe false
                    aktivFra shouldBeCloseTo nowUTC()
                    aktivTil.shouldNotBeNull() shouldBeCloseTo nowUTC().plus(Varsel.beskjedAktivLengde)
                    revarselForVarsel shouldBe skalRevarsles.id
                }

                verify { outboxService.insertRecord(revarsel.id, any(), any(), any()) }
            }

            @Test
            fun `sendRevarsler - aktiv beskjed skal revarsles - inaktiverer beskjed, oppretter og sender revarsel`() = runTest {
                // Arrange
                val skalRevarsles = Varselsdata.beskjed(
                    status = Varsel.Status.AKTIV,
                    aktivFra = nowUTC().minusDays(7).plusMinutes(1),
                    revarsles = nowUTC().minusMinutes(1),
                )
                varselRepository.upsert(skalRevarsles)

                val hendelse = Hendelsesdata.hendelse(
                    payload = HendelseTypeData.navGodkjennUtkast(),
                    id = skalRevarsles.hendelser.first(),
                )
                hendelseRepository.insert(hendelse)

                // val forventetUrl = innbyggerDeltakerUrl(skalRevarsles.deltakerId, false)

                // Act
                varselService.sendRevarsler()

                // Assert
                val oppdatertVarsel = varselRepository.get(skalRevarsles.id).shouldBeSuccess()
                assertSoftly(oppdatertVarsel) {
                    revarsles shouldBe null
                    status shouldBe Varsel.Status.INAKTIVERT
                    aktivTil.shouldNotBeNull() shouldBeCloseTo nowUTC()
                }

                verify { outboxService.insertRecord(oppdatertVarsel.id, any(), any()) }

                val revarsel = varselRepository.getAktivt(skalRevarsles.deltakerId).shouldBeSuccess()
                assertSoftly(revarsel) {
                    erRevarsel shouldBe true
                    kanRevarsles shouldBe false
                    aktivFra shouldBeCloseTo nowUTC()
                    aktivTil.shouldNotBeNull() shouldBeCloseTo nowUTC().plus(Varsel.beskjedAktivLengde)
                    revarselForVarsel shouldBe skalRevarsles.id
                }

                verify { outboxService.insertRecord(revarsel.id, any(), any(), any()) }
            }

            @Test
            fun `sendRevarsler - aktiv beskjed skal ikke revarsles enda - endrer ingenting`() = runTest {
                // Arrange
                val skalIkkeRevarsles = Varselsdata.beskjed(
                    Varsel.Status.AKTIV,
                    aktivFra = nowUTC().minusDays(6).plusMinutes(1),
                    revarsles = nowUTC().plusDays(1),
                )
                varselRepository.upsert(skalIkkeRevarsles)

                // Act
                varselService.sendRevarsler()

                // Assert
                val ikkeOppdatertVarsel = varselRepository.get(skalIkkeRevarsles.id).shouldBeSuccess()
                assertSoftly(ikkeOppdatertVarsel) {
                    revarsles.shouldNotBeNull() shouldBeCloseTo skalIkkeRevarsles.revarsles.shouldNotBeNull()
                    status shouldBe skalIkkeRevarsles.status
                    aktivTil.shouldNotBeNull() shouldBeCloseTo skalIkkeRevarsles.aktivTil
                }
            }
        }

        @Nested
        inner class UtlopBeskjedTests {
            @Test
            fun `utlopBeskjed - varsler kan ikke utløpes - feiler`() {
                // Arrange
                val ugyldigeVarsler = listOf(
                    Varselsdata.varsel(Varsel.Type.OPPGAVE),
                    Varselsdata.beskjed(status = Varsel.Status.INAKTIVERT),
                    Varselsdata.beskjed(status = Varsel.Status.AKTIV, aktivTil = nowUTC().plusMinutes(1)),
                )

                // Act & Assert
                ugyldigeVarsler.forEach {
                    shouldThrow<IllegalArgumentException> {
                        varselService.utlopBeskjed(it)
                    }
                }
            }

            @Test
            fun `utlopBeskjed - varsel er utløpt - utløper`() {
                // Arrange
                val utloptBeskjed = Varselsdata.beskjed(
                    Varsel.Status.AKTIV,
                    aktivFra = nowUTC().minusDays(21),
                    aktivTil = nowUTC().minusMinutes(1),
                )

                // Act
                varselService.utlopBeskjed(utloptBeskjed)

                // Assert
                varselRepository
                    .get(utloptBeskjed.id)
                    .shouldBeSuccess()
                    .status shouldBe Varsel.Status.UTLOPT
            }
        }
    }
}
