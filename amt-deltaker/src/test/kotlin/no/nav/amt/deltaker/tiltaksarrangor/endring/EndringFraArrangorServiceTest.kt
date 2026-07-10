package no.nav.amt.deltaker.tiltaksarrangor.endring

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EndringFraArrangorServiceTest : IntegrationTestWithDbBase() {
    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato, dato ikke passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().plusDays(2)
        val sluttdato = LocalDate.now().plusMonths(3)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        // Act
        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe sluttdato
            it.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }

        // Assert
        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        outboxService.assertProducedHendelse<HendelseType.LeggTilOppstartsdato>(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato, dato passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().minusDays(2)
        val sluttdato = LocalDate.now().plusMonths(3)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe sluttdato
            it.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        outboxService.assertProducedHendelse<HendelseType.LeggTilOppstartsdato>(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato uten sluttdato, dato passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().minusDays(2)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = null,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe null
            it.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        outboxService.assertProducedHendelse<HendelseType.LeggTilOppstartsdato>(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato uten sluttdato - fjerner ikke eksisterende sluttdato`() = runTest {
        val gammelsluttdato = LocalDate.now().plusDays(2)
        val deltaker = lagDeltaker(
            startdato = LocalDate.of(2021, 1, 1),
            sluttdato = gammelsluttdato,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.of(2021, 1, 2)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = null,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe gammelsluttdato
            it.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        val endring = endringFraArrangorRepository.getForDeltaker(deltaker.id).first()
        endring.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
        (endring.endring as EndringFraArrangor.LeggTilOppstartsdato).sluttdato shouldBe null

        endring.endring shouldBe endringFraArrangor.endring

        val deltakerEtterEndring = deltakerRepository.get(deltaker.id).getOrThrow()

        deltakerEtterEndring.sluttdato shouldBe gammelsluttdato

        outboxService.assertProducedHendelse<HendelseType.LeggTilOppstartsdato>(deltaker.id)
    }

    @Test
    fun `upsertEndretDeltaker - legg til oppstartsdato, start- og sluttdato passert - inserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            startdato = null,
            sluttdato = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now(),
        )
        vedtakRepository.upsert(vedtak)

        val startdato = LocalDate.now().minusMonths(2)
        val sluttdato = LocalDate.now().minusDays(5)
        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                startdato = startdato,
                sluttdato = sluttdato,
            ),
        )

        val oppdatertDeltaker = endringFraArrangorService.upsertEndretDeltaker(endringFraArrangor)
        assertSoftly(oppdatertDeltaker) {
            it.startdato shouldBe startdato
            it.sluttdato shouldBe sluttdato
            it.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        }

        assertSoftly(endringFraArrangorRepository.getForDeltaker(deltaker.id).first()) {
            it.opprettetAvArrangorAnsattId shouldBe endringFraArrangor.opprettetAvArrangorAnsattId
            it.endring shouldBe endringFraArrangor.endring
        }

        outboxService.assertProducedHendelse<HendelseType.LeggTilOppstartsdato>(deltaker.id)
    }
}
