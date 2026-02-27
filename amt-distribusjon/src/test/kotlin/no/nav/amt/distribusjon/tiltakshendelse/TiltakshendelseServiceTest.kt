package no.nav.amt.distribusjon.tiltakshendelse

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.haveOutboxRecord
import no.nav.amt.distribusjon.integrationTest
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.distribusjon.utils.MockResponseHandler
import no.nav.amt.distribusjon.utils.data.DeltakerData
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakshendelseServiceTest {
    @Nested
    inner class HandleHendelseTests {
        @Test
        fun `handleHendelse - nytt utkast - oppretter aktiv tiltakshendelse`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())

            Database.transaction {
                app.tiltakshendelseService.handleHendelse(hendelse)
            }

            val tiltakshendelse = app.tiltakshendelseRepository.getByHendelseId(hendelse.id).shouldBeSuccess()
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

            app should haveOutboxRecord(tiltakshendelse.id, Environment.TILTAKSHENDELSE_TOPIC)
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
        fun `handleHendelse - utkast er håndtert - håndterer ikke på nytt`() = integrationTest { app, _ ->
            val opprettHendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
            app.tiltakshendelseRepository.upsert(opprettHendelse.toTiltakshendelse().copy(aktiv = false))

            app.tiltakshendelseService.handleHendelse(opprettHendelse)

            val tiltakshendelse = app.tiltakshendelseRepository.getByHendelseId(opprettHendelse.id).shouldBeSuccess()

            tiltakshendelse.aktiv shouldBe false
            app shouldNot haveOutboxRecord(tiltakshendelse.id, Environment.TILTAKSHENDELSE_TOPIC)
        }

        @Test
        fun `handleHendelse - ny ForlengDeltakelse venter på svar - oppretter ny tiltakshendelse`() = integrationTest { app, _ ->
            val forslag = Forslag(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now(),
                "begrunnelse",
                Forslag.ForlengDeltakelse(LocalDate.now()),
                Forslag.Status.VenterPaSvar,
            )

            app.tiltakshendelseService.handleForslag(forslag)

            val tiltakshendelse = app.tiltakshendelseRepository.getForslagHendelse(forslag.id).shouldBeSuccess()
            assertSoftly(tiltakshendelse) {
                hendelser shouldBe emptyList()
                tekst shouldBe "Forslag: Forleng deltakelse"
                tiltakskode shouldBe Tiltakskode.ARBEIDSFORBEREDENDE_TRENING
                aktiv shouldBe true
            }
        }

        @Test
        fun `handleHendelse - ny ForlengDeltakelse godkjennes - oppretter ny tiltakshendelse'`() = integrationTest { app, _ ->
            val deltaker = DeltakerData.lagDeltaker()
            val forslag = Forslag(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                opprettet = LocalDateTime.now(),
                begrunnelse = "begrunnelse",
                endring = Forslag.ForlengDeltakelse(LocalDate.now()),
                status = Forslag.Status.VenterPaSvar,
            )

            MockResponseHandler.addDeltakerResponse(deltaker)

            app.tiltakshendelseService.handleForslag(forslag)

            val godkjentForslag = forslag.copy(
                status = Forslag.Status.Godkjent(Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()), LocalDateTime.now()),
            )

            app.tiltakshendelseService.handleForslag(godkjentForslag)

            val tiltakhendelseFerdig = app.tiltakshendelseRepository.getForslagHendelse(forslag.id).shouldBeSuccess()

            tiltakhendelseFerdig.aktiv shouldBe false
        }

        @Test
        fun `handleHendelse - Flere hendelser på samme bruker - oppretter nye tiltakshendelse`() = integrationTest { app, _ ->
            val deltaker = DeltakerData.lagDeltaker()
            val forslag1 = Forslag(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                opprettet = LocalDateTime.now(),
                begrunnelse = "begrunnelse",
                endring = Forslag.ForlengDeltakelse(LocalDate.now()),
                status = Forslag.Status.VenterPaSvar,
            )

            val forslag2 = Forslag(
                id = UUID.randomUUID(),
                deltakerId = deltaker.id,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                opprettet = LocalDateTime.now(),
                begrunnelse = "begrunnelse",
                endring = Forslag.AvsluttDeltakelse(LocalDate.now(), EndringAarsak.FattJobb, null, null),
                status = Forslag.Status.VenterPaSvar,
            )

            MockResponseHandler.addDeltakerResponse(deltaker)

            app.tiltakshendelseService.handleForslag(forslag1)
            app.tiltakshendelseService.handleForslag(forslag2)

            val tiltakshendelse1 = app.tiltakshendelseRepository.getForslagHendelse(forslag1.id).shouldBeSuccess()
            val tiltakshendelse2 = app.tiltakshendelseRepository.getForslagHendelse(forslag2.id).shouldBeSuccess()

            tiltakshendelse1.forslagId shouldBe forslag1.id
            tiltakshendelse2.forslagId shouldBe forslag2.id

            val forslag1Godkjent = forslag1.copy(
                status = Forslag.Status.Godkjent(Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()), LocalDateTime.now()),
            )

            app.tiltakshendelseService.handleForslag(forslag1Godkjent)

            val tiltakshendelse1Godkjent = app.tiltakshendelseRepository.getForslagHendelse(forslag1.id).shouldBeSuccess()
            tiltakshendelse1Godkjent.aktiv shouldBe false

            val tiltakshendelse2IkkeGodkjent = app.tiltakshendelseRepository.getForslagHendelse(forslag2.id).shouldBeSuccess()
            tiltakshendelse2IkkeGodkjent.aktiv shouldBe true
        }
    }

    companion object {
        private fun testInaktiveringAvTiltakshendelse(hendelseType: HendelseType) = integrationTest { app, _ ->
            val opprettHendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
            app.tiltakshendelseRepository.upsert(opprettHendelse.toTiltakshendelse())

            val godkjennHendelse = Hendelsesdata.hendelse(hendelseType, deltaker = opprettHendelse.deltaker)

            Database.transaction {
                app.tiltakshendelseService.handleHendelse(godkjennHendelse)
            }

            val tiltakshendelse = app.tiltakshendelseRepository.getByHendelseId(godkjennHendelse.id).shouldBeSuccess()

            tiltakshendelse.aktiv shouldBe false
            app should haveOutboxRecord(tiltakshendelse.id, Environment.TILTAKSHENDELSE_TOPIC)
        }
    }
}
