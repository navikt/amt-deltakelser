package no.nav.amt.distribusjon.journalforing.job

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.journalforing.JournalforingService
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.utils.job.JobManager
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class EndringsvedtakJobTest {
    @Test
    fun `init - kaster exception ved negativ initialDelay`() {
        val exception = shouldThrow<IllegalArgumentException> {
            lagJob(initialDelay = Duration.ofMinutes(-1))
        }

        exception.message shouldBe "Initial delay for endringsvedtak-jobb må være større enn 0"
    }

    @Test
    fun `init - kaster exception ved null eller negativ jobPeriod`() {
        val exceptionVedNullPeriode = shouldThrow<IllegalArgumentException> {
            lagJob(jobPeriod = Duration.ZERO)
        }
        exceptionVedNullPeriode.message shouldBe "Jobbperiode for endringsvedtak-jobb må være større enn 0"

        val exceptionVedNegativPeriode = shouldThrow<IllegalArgumentException> {
            lagJob(jobPeriod = Duration.ofMinutes(-1))
        }
        exceptionVedNegativPeriode.message shouldBe "Jobbperiode for endringsvedtak-jobb må være større enn 0"
    }

    @Test
    fun `init - kaster exception ved negativ gracePeriod`() {
        val exception = shouldThrow<IllegalArgumentException> {
            lagJob(gracePeriod = Duration.ofMinutes(-1))
        }

        exception.message shouldBe "Grace-periode for endringsvedtak-jobb må være større enn 0"
    }

    @Test
    fun `journalforEndringsvedtak - journalforer og distribuerer endringsvedtak naar nyeste hendelse er eldre enn graceperiode`() =
        runTest {
            val deltakerIdA = UUID.randomUUID()
            val deltakerIdB = UUID.randomUUID()

            val hendelser = listOf(
                // Deltaker A: to endringsvedtak, begge eldre enn graceperiode => skal behandles samlet
                hendelseMedStatus(deltakerId = deltakerIdA, opprettet = LocalDateTime.now().minusMinutes(60)),
                hendelseMedStatus(deltakerId = deltakerIdA, opprettet = LocalDateTime.now().minusMinutes(40)),
                // Deltaker B: endringsvedtak men nyeste er innenfor graceperiode => skal ikke behandles
                hendelseMedStatus(deltakerId = deltakerIdB, opprettet = LocalDateTime.now().minusMinutes(10)),
                // Ikke-endringsvedtak (utkast) skal filtreres bort
                hendelseMedStatus(
                    deltakerId = deltakerIdA,
                    opprettet = LocalDateTime.now().minusMinutes(70),
                    payload = HendelseTypeData.opprettUtkast(),
                ),
            )

            val test = testSetup(hendelser)

            test.job.journalforEndringsvedtak()

            coVerify(exactly = 1) {
                test.journalforingService.journalforOgDistribuerEndringsvedtak(
                    match { liste ->
                        liste.size == 2 && liste.all { it.hendelse.deltaker.id == deltakerIdA }
                    },
                )
            }
        }

    @Test
    fun `journalforEndringsvedtak - behandler ikke hendelser innenfor graceperiode`() = runTest {
        val deltakerId = UUID.randomUUID()

        val hendelser = listOf(
            hendelseMedStatus(deltakerId = deltakerId, opprettet = LocalDateTime.now().minusMinutes(5)),
        )

        val test = testSetup(hendelser)

        test.job.journalforEndringsvedtak()

        coVerify(exactly = 0) { test.journalforingService.journalforOgDistribuerEndringsvedtak(any()) }
    }

    @Test
    fun `journalforEndringsvedtak - fortsetter med neste deltaker hvis journalforing feiler for en deltaker`() = runTest {
        val deltakerIdA = UUID.randomUUID()
        val deltakerIdB = UUID.randomUUID()

        val hendelser = listOf(
            hendelseMedStatus(deltakerId = deltakerIdA, opprettet = LocalDateTime.now().minusMinutes(60)),
            hendelseMedStatus(deltakerId = deltakerIdB, opprettet = LocalDateTime.now().minusMinutes(60)),
        )

        val test = testSetup(hendelser)

        coEvery {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match {
                    it
                        .first()
                        .hendelse.deltaker.id == deltakerIdA
                },
            )
        } throws RuntimeException("Simulert feil")
        coEvery {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match {
                    it
                        .first()
                        .hendelse.deltaker.id == deltakerIdB
                },
            )
        } returns Unit

        test.job.journalforEndringsvedtak()

        // Verifiserer at begge deltakere faktisk blir forsøkt
        coVerify(exactly = 1) {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match {
                    it
                        .first()
                        .hendelse.deltaker.id == deltakerIdA
                },
            )
        }
        coVerify(exactly = 1) {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match {
                    it
                        .first()
                        .hendelse.deltaker.id == deltakerIdB
                },
            )
        }
    }

    @Test
    fun `startJob - starter jobb med forventet initialDelay og period`() {
        val jobManager = mockk<JobManager>(relaxUnitFun = true)
        val hendelseRepository = mockk<HendelseRepository>()
        val journalforingService = mockk<JournalforingService>()

        val initialDelay = Duration.ofMinutes(5)
        val period = Duration.ofMinutes(10)

        EndringsvedtakJob(
            jobManager,
            hendelseRepository,
            journalforingService,
            initialDelay,
            period,
            Duration.ofHours(1),
        ).startJob()

        verify(exactly = 1) {
            jobManager.startJob(
                name = "EndringsvedtakJob",
                initialDelay = initialDelay,
                period = period,
                job = any(),
            )
        }
    }

    private fun lagJob(
        initialDelay: Duration = Duration.ofMinutes(5),
        jobPeriod: Duration = Duration.ofMinutes(10),
        gracePeriod: Duration = Duration.ofMinutes(30),
    ) = EndringsvedtakJob(
        jobManager = mockk(relaxUnitFun = true),
        hendelseRepository = mockk(),
        journalforingService = mockk(),
        initialDelay = initialDelay,
        jobPeriod = jobPeriod,
        gracePeriod = gracePeriod,
    )

    private fun testSetup(hendelser: List<HendelseMedJournalforingstatus>): TestSetup {
        val jobManager = mockk<JobManager>(relaxUnitFun = true)
        val hendelseRepository = mockk<HendelseRepository>()
        val journalforingService = mockk<JournalforingService>()

        every { hendelseRepository.hentIkkeJournalforteHendelser() } returns hendelser
        every { hendelseRepository.hentHendelserSomSkalDistribueresSomBrev() } returns emptyList()

        return TestSetup(
            job = EndringsvedtakJob(
                jobManager,
                hendelseRepository,
                journalforingService,
                initialDelay = Duration.ofMinutes(5),
                jobPeriod = Duration.ofMinutes(10),
                gracePeriod = Duration.ofMinutes(30),
            ),
            journalforingService = journalforingService,
        )
    }

    private data class TestSetup(
        val job: EndringsvedtakJob,
        val journalforingService: JournalforingService,
    )

    private fun hendelseMedStatus(
        deltakerId: UUID,
        opprettet: LocalDateTime,
        payload: HendelseType = HendelseTypeData.endreStartdato(),
    ): HendelseMedJournalforingstatus {
        val deltaker = Hendelsesdata.lagDeltaker(id = deltakerId)
        val hendelse = Hendelsesdata.hendelse(payload = payload, deltaker = deltaker, opprettet = opprettet)

        return HendelseMedJournalforingstatus(
            hendelse = hendelse,
            journalforingstatus = Journalforingstatus(
                hendelseId = hendelse.id,
                journalpostId = null,
                bestillingsId = null,
                kanIkkeDistribueres = false,
                kanIkkeJournalfores = false,
            ),
        )
    }
}
