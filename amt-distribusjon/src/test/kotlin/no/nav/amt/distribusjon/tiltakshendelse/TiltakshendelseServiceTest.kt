package no.nav.amt.distribusjon.tiltakshendelse

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.IntegrationTestBase
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.distribusjon.utils.data.DeltakerData
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakshendelseServiceTest : IntegrationTestBase() {
    @Nested
    inner class HandleHendelseTests {
        @Test
        fun `handleHendelse - nytt utkast - oppretter aktiv tiltakshendelse`() = runTest {
            // Arrange
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())

            // Act
            Database.transaction {
                tiltakshendelseService.handleHendelse(hendelse)
            }

            // Assert
            val tiltakshendelse = tiltakshendelseRepository.getByHendelseId(hendelse.id).shouldBeSuccess()
            assertSoftly(tiltakshendelse) {
                aktiv shouldBe true
                deltakerId shouldBe hendelse.deltaker.id
                forslagId shouldBe null
                personident shouldBe hendelse.deltaker.personident
                hendelser shouldBe listOf(hendelse.id)
                type shouldBe Tiltakshendelse.Type.UTKAST
                tekst shouldBe TiltakshendelseService.UTKAST_TIL_PAMELDING_TEKST
                opprettet shouldBeCloseTo hendelse.opprettet
                tiltakskode shouldBe hendelse.deltaker.deltakerliste.tiltak.tiltakskode
            }

            verify {
                outboxService.insertRecord(
                    key = tiltakshendelse.id,
                    value = any(),
                    topic = Environment.TILTAKSHENDELSE_TOPIC,
                    suppressOutsideTxWarning = any(),
                )
            }
        }

        @Test
        fun `handleHendelse - utkast godkjent av nav - inaktiverer tiltakshendelse`() {
            testInaktiveringAvTiltakshendelse(HendelseTypeData.navGodkjennUtkast())
        }

        @Test
        fun `handleHendelse - utkast godkjent av innbygger - inaktiverer tiltakshendelse`() {
            testInaktiveringAvTiltakshendelse(HendelseTypeData.innbyggerGodkjennUtkast())
        }

        @Test
        fun `handleHendelse - utkast avbrutt - inaktiverer tiltakshendelse`() {
            testInaktiveringAvTiltakshendelse(HendelseTypeData.avbrytUtkast())
        }

        @Test
        fun `handleHendelse - utkast er håndtert - håndterer ikke på nytt`() {
            // Arrange
            val opprettHendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
            tiltakshendelseRepository.upsert(opprettHendelse.toTiltakshendelse().copy(aktiv = false))

            // Act
            tiltakshendelseService.handleHendelse(opprettHendelse)

            // Assert
            val tiltakshendelse = tiltakshendelseRepository.getByHendelseId(opprettHendelse.id).shouldBeSuccess()
            tiltakshendelse.aktiv shouldBe false

            verify(exactly = 0) { outboxService.insertRecord(tiltakshendelse.id, any(), any(), any()) }
        }

        @Nested
        inner class HandleForslagTests {
            @Test
            fun `ny ForlengDeltakelse venter på svar - oppretter ny tiltakshendelse`() = runTest {
                // Arrange
                val deltakerResponse = DeltakerData.lagDeltakerResponse()
                val forslag = Forslag(
                    id = UUID.randomUUID(),
                    deltakerId = deltakerResponse.id,
                    opprettetAvArrangorAnsattId = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    begrunnelse = "begrunnelse",
                    endring = Forslag.ForlengDeltakelse(LocalDate.now()),
                    status = Forslag.Status.VenterPaSvar,
                )

                coEvery { amtDeltakerClient.getDeltaker(deltakerResponse.id) } returns deltakerResponse

                // Act
                tiltakshendelseService.handleForslag(forslag)

                // Assert
                assertSoftly(tiltakshendelseRepository.getForslagHendelse(forslag.id).shouldBeSuccess()) {
                    hendelser shouldBe emptyList()
                    tekst shouldBe "Forslag: Forleng deltakelse"
                    tiltakskode shouldBe Tiltakskode.ARBEIDSFORBEREDENDE_TRENING
                    aktiv shouldBe true
                }
            }

            @Test
            fun `reprosessering av samme forslag ignorerer duplikat VenterPaSvar`() = runTest {
                val deltakerResponse = DeltakerData.lagDeltakerResponse()
                val forslag = Forslag(
                    id = UUID.randomUUID(),
                    deltakerId = deltakerResponse.id,
                    opprettetAvArrangorAnsattId = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    begrunnelse = "begrunnelse",
                    endring = Forslag.ForlengDeltakelse(LocalDate.now()),
                    status = Forslag.Status.VenterPaSvar,
                )

                coEvery { amtDeltakerClient.getDeltaker(deltakerResponse.id) } returns deltakerResponse

                tiltakshendelseService.handleForslag(forslag)
                val lagretTiltakshendelse = tiltakshendelseRepository.getForslagHendelse(forslag.id).shouldBeSuccess()

                tiltakshendelseService.handleForslag(forslag)
                val lagretTiltakshendelseEtterReprosessering = tiltakshendelseRepository.getForslagHendelse(forslag.id).shouldBeSuccess()

                lagretTiltakshendelseEtterReprosessering.id shouldBe lagretTiltakshendelse.id

                verify(exactly = 1) {
                    outboxService.insertRecord(
                        key = lagretTiltakshendelse.id,
                        value = any(),
                        topic = Environment.TILTAKSHENDELSE_TOPIC,
                        suppressOutsideTxWarning = any(),
                    )
                }
            }

            @Test
            fun `ny ForlengDeltakelse godkjennes - oppretter ny tiltakshendelse'`() = runTest {
                // Arrange
                val deltakerResponse = DeltakerData.lagDeltakerResponse()
                val forslag = Forslag(
                    id = UUID.randomUUID(),
                    deltakerId = deltakerResponse.id,
                    opprettetAvArrangorAnsattId = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    begrunnelse = "begrunnelse",
                    endring = Forslag.ForlengDeltakelse(LocalDate.now()),
                    status = Forslag.Status.VenterPaSvar,
                )
                val godkjentForslag = forslag.copy(
                    status = Forslag.Status.Godkjent(
                        godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                        godkjent = LocalDateTime.now(),
                    ),
                )

                coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltakerResponse

                // Act
                tiltakshendelseService.handleForslag(forslag)
                tiltakshendelseService.handleForslag(godkjentForslag)

                // Assert
                val tiltakhendelseFerdig = tiltakshendelseRepository.getForslagHendelse(forslag.id).shouldBeSuccess()
                tiltakhendelseFerdig.aktiv shouldBe false
            }

            @Test
            fun `flere hendelser på samme bruker - oppretter nye tiltakshendelse`() = runTest {
                // Arrange
                val deltakerResponse = DeltakerData.lagDeltakerResponse()
                val forslag1 = Forslag(
                    id = UUID.randomUUID(),
                    deltakerId = deltakerResponse.id,
                    opprettetAvArrangorAnsattId = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    begrunnelse = "begrunnelse",
                    endring = Forslag.ForlengDeltakelse(LocalDate.now()),
                    status = Forslag.Status.VenterPaSvar,
                )

                val forslag2 = Forslag(
                    id = UUID.randomUUID(),
                    deltakerId = deltakerResponse.id,
                    opprettetAvArrangorAnsattId = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    begrunnelse = "begrunnelse",
                    endring = Forslag.AvsluttDeltakelse(LocalDate.now(), EndringAarsak.FattJobb, null, null),
                    status = Forslag.Status.VenterPaSvar,
                )

                coEvery { amtDeltakerClient.getDeltaker(any()) } returns deltakerResponse

                // Act
                tiltakshendelseService.handleForslag(forslag1)
                tiltakshendelseService.handleForslag(forslag2)

                // Assert
                val tiltakshendelse1 = tiltakshendelseRepository.getForslagHendelse(forslag1.id).shouldBeSuccess()
                tiltakshendelse1.forslagId shouldBe forslag1.id

                val tiltakshendelse2 = tiltakshendelseRepository.getForslagHendelse(forslag2.id).shouldBeSuccess()
                tiltakshendelse2.forslagId shouldBe forslag2.id

                // Arrange
                val forslag1Godkjent = forslag1.copy(
                    status = Forslag.Status.Godkjent(
                        godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                        godkjent = LocalDateTime.now(),
                    ),
                )

                // Act
                tiltakshendelseService.handleForslag(forslag1Godkjent)

                // Assert
                val tiltakshendelse1Godkjent = tiltakshendelseRepository.getForslagHendelse(forslag1.id).shouldBeSuccess()
                tiltakshendelse1Godkjent.aktiv shouldBe false

                val tiltakshendelse2IkkeGodkjent = tiltakshendelseRepository.getForslagHendelse(forslag2.id).shouldBeSuccess()
                tiltakshendelse2IkkeGodkjent.aktiv shouldBe true
            }
        }
    }

    private fun testInaktiveringAvTiltakshendelse(hendelseType: HendelseType) = runTest {
        // Arrange
        val opprettHendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
        tiltakshendelseRepository.upsert(opprettHendelse.toTiltakshendelse())

        val godkjennHendelse = Hendelsesdata.hendelse(hendelseType, deltaker = opprettHendelse.deltaker)

        // Act
        Database.transaction {
            tiltakshendelseService.handleHendelse(godkjennHendelse)
        }

        // Assert
        val tiltakshendelse = tiltakshendelseRepository.getByHendelseId(godkjennHendelse.id).shouldBeSuccess()
        tiltakshendelse.aktiv shouldBe false

        verify {
            outboxService.insertRecord(
                key = tiltakshendelse.id,
                value = any(),
                topic = Environment.TILTAKSHENDELSE_TOPIC,
                suppressOutsideTxWarning = any(),
            )
        }
    }
}
