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
import no.nav.amt.distribusjon.journalforing.JournalforingstatusRepository
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
        // Arrange
        val initialDelay = Duration.ofMinutes(-1)

        // Act
        val exception = shouldThrow<IllegalArgumentException> {
            lagJob(initialDelay = initialDelay)
        }

        // Assert
        exception.message shouldBe "Initial delay for endringsvedtak-jobb må være større enn 0"
    }

    @Test
    fun `init - kaster exception ved null eller negativ jobPeriod`() {
        // Arrange
        val nullPeriode = Duration.ZERO
        val negativPeriode = Duration.ofMinutes(-1)

        // Act
        val exceptionVedNullPeriode = shouldThrow<IllegalArgumentException> {
            lagJob(jobPeriod = nullPeriode)
        }
        val exceptionVedNegativPeriode = shouldThrow<IllegalArgumentException> {
            lagJob(jobPeriod = negativPeriode)
        }

        // Assert
        exceptionVedNullPeriode.message shouldBe "Jobbperiode for endringsvedtak-jobb må være større enn 0"
        exceptionVedNegativPeriode.message shouldBe "Jobbperiode for endringsvedtak-jobb må være større enn 0"
    }

    @Test
    fun `init - kaster exception ved negativ gracePeriod`() {
        // Arrange
        val gracePeriod = Duration.ofMinutes(-1)

        // Act
        val exception = shouldThrow<IllegalArgumentException> {
            lagJob(gracePeriod = gracePeriod)
        }

        // Assert
        exception.message shouldBe "Grace-periode for endringsvedtak-jobb må være større enn 0"
    }

    @Test
    fun `journalforEndringsvedtak - journalforer og distribuerer endringsvedtak naar nyeste hendelse er eldre enn graceperiode`() =
        runTest {
            // Arrange
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

            // Act
            test.job.journalforEndringsvedtak()

            // Assert
            coVerify(exactly = 1) {
                test.journalforingService.journalforOgDistribuerEndringsvedtak(
                    match { liste ->
                        liste.size == 2 && liste.all { it.hendelse.deltaker.id == deltakerIdA }
                    },
                )
            }
        }

    @Test
    fun `journalforEndringsvedtak - EnkeltplassEndrePrisinfo eldre enn graceperiode - journalfores som endringsvedtak`() = runTest {
        // Arrange
        val deltakerId = UUID.randomUUID()

        val hendelser = listOf(
            hendelseMedStatus(
                deltakerId = deltakerId,
                opprettet = LocalDateTime.now().minusMinutes(60),
                payload = HendelseTypeData.enkeltplassEndrePrisinfo(kreverGodkjenning = false),
            ),
        )

        val test = testSetup(hendelser)

        // Act
        test.job.journalforEndringsvedtak()

        // Assert
        coVerify(exactly = 1) {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match { liste ->
                    liste.size == 1 && liste.all { it.hendelse.deltaker.id == deltakerId }
                },
            )
        }
    }

    @Test
    fun `journalforEndringsvedtak - godkjent prisendring og vanlig endring eldre enn graceperiode - sendes samlet`() = runTest {
        // Arrange
        val deltakerId = UUID.randomUUID()

        val hendelser = listOf(
            // Godkjent prisendring (medfører endringsvedtak) + vanlig endring for samme deltaker, begge eldre enn grace
            hendelseMedStatus(
                deltakerId = deltakerId,
                opprettet = LocalDateTime.now().minusMinutes(60),
                payload = HendelseTypeData.enkeltplassGodkjennPrisendring(),
            ),
            hendelseMedStatus(
                deltakerId = deltakerId,
                opprettet = LocalDateTime.now().minusMinutes(40),
                payload = HendelseTypeData.endreStartdato(),
            ),
        )

        val test = testSetup(hendelser)

        // Act
        test.job.journalforEndringsvedtak()

        // Assert
        coVerify(exactly = 1) {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match { liste ->
                    liste.size == 2 &&
                        liste.all { it.hendelse.deltaker.id == deltakerId } &&
                        liste.any { it.hendelse.payload is HendelseType.EnkeltplassGodkjennPrisendring }
                },
            )
        }
    }

    @Test
    fun `journalforEndringsvedtak - annullerer endringer dekket av sendt enkeltplass-hovedvedtak`() = runTest {
        // Arrange
        val deltakerId = UUID.randomUUID()
        val fattetDato = LocalDateTime.now().minusMinutes(60)
        val dekketHendelse = hendelseMedStatus(
            deltakerId = deltakerId,
            opprettet = fattetDato.minusMinutes(5),
        )
        val senereHendelse = hendelseMedStatus(
            deltakerId = deltakerId,
            opprettet = fattetDato.plusMinutes(5),
        )
        val hovedvedtak = hovedvedtakMedStatus(deltakerId, fattetDato)

        val test = testSetup(
            hendelser = listOf(dekketHendelse, senereHendelse),
            sendteHovedvedtak = listOf(hovedvedtak),
        )

        // Act
        test.job.journalforEndringsvedtak()

        // Assert
        verify(exactly = 1) {
            test.journalforingstatusRepository.annuller(listOf(dekketHendelse.hendelse.id))
        }
        coVerify(exactly = 1) {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(
                match { liste ->
                    liste.size == 1 && liste.single().hendelse.id == senereHendelse.hendelse.id
                },
            )
        }
    }

    @Test
    fun `journalforEndringsvedtak - godkjent prisendring innenfor graceperiode - venter og sender ikke`() = runTest {
        // Arrange
        val deltakerId = UUID.randomUUID()

        val hendelser = listOf(
            // Vanlig endring er gammel, men godkjent prisendring er nyeste og innenfor grace => hele batchen venter
            hendelseMedStatus(
                deltakerId = deltakerId,
                opprettet = LocalDateTime.now().minusMinutes(60),
                payload = HendelseTypeData.endreStartdato(),
            ),
            hendelseMedStatus(
                deltakerId = deltakerId,
                opprettet = LocalDateTime.now().minusMinutes(5),
                payload = HendelseTypeData.enkeltplassGodkjennPrisendring(),
            ),
        )

        val test = testSetup(hendelser)

        // Act
        test.job.journalforEndringsvedtak()

        // Assert
        coVerify(exactly = 0) {
            test.journalforingService.journalforOgDistribuerEndringsvedtak(any())
        }
    }

    @Test
    fun `journalforEndringsvedtak - behandler ikke hendelser innenfor graceperiode`() = runTest {
        // Arrange
        val deltakerId = UUID.randomUUID()

        val hendelser = listOf(
            hendelseMedStatus(deltakerId = deltakerId, opprettet = LocalDateTime.now().minusMinutes(5)),
        )

        val test = testSetup(hendelser)

        // Act
        test.job.journalforEndringsvedtak()

        // Assert
        coVerify(exactly = 0) { test.journalforingService.journalforOgDistribuerEndringsvedtak(any()) }
    }

    @Test
    fun `journalforEndringsvedtak - fortsetter med neste deltaker hvis journalforing feiler for en deltaker`() = runTest {
        // Arrange
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

        // Act
        test.job.journalforEndringsvedtak()

        // Assert
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
        // Arrange
        val jobManager = mockk<JobManager>(relaxUnitFun = true)
        val hendelseRepository = mockk<HendelseRepository>()
        val journalforingService = mockk<JournalforingService>()

        val initialDelay = Duration.ofMinutes(5)
        val period = Duration.ofMinutes(10)

        val job = EndringsvedtakJob(
            jobManager,
            hendelseRepository,
            mockk(),
            journalforingService,
            initialDelay,
            period,
            Duration.ofHours(1),
        )

        // Act
        job.startJob()

        // Assert
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
        journalforingstatusRepository = mockk(),
        journalforingService = mockk(),
        initialDelay = initialDelay,
        jobPeriod = jobPeriod,
        gracePeriod = gracePeriod,
    )

    private fun testSetup(
        hendelser: List<HendelseMedJournalforingstatus>,
        sendteHovedvedtak: List<HendelseMedJournalforingstatus> = emptyList(),
    ): TestSetup {
        val jobManager = mockk<JobManager>(relaxUnitFun = true)
        val hendelseRepository = mockk<HendelseRepository>()
        val journalforingstatusRepository = mockk<JournalforingstatusRepository>()
        val journalforingService = mockk<JournalforingService>()

        every { hendelseRepository.hentIkkeJournalforteHendelser() } returns hendelser
        every { hendelseRepository.hentHendelserSomSkalDistribueresSomBrev() } returns emptyList()
        every { hendelseRepository.hentDistribuerteEnkeltplassHovedvedtak(any()) } returns sendteHovedvedtak
        every { journalforingstatusRepository.annuller(any()) } answers {
            firstArg<Collection<UUID>>().size
        }

        return TestSetup(
            job = EndringsvedtakJob(
                jobManager,
                hendelseRepository,
                journalforingstatusRepository,
                journalforingService,
                initialDelay = Duration.ofMinutes(5),
                jobPeriod = Duration.ofMinutes(10),
                gracePeriod = Duration.ofMinutes(30),
            ),
            journalforingService = journalforingService,
            journalforingstatusRepository = journalforingstatusRepository,
        )
    }

    private data class TestSetup(
        val job: EndringsvedtakJob,
        val journalforingService: JournalforingService,
        val journalforingstatusRepository: JournalforingstatusRepository,
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

    private fun hovedvedtakMedStatus(
        deltakerId: UUID,
        fattetDato: LocalDateTime,
    ): HendelseMedJournalforingstatus {
        val hendelse = Hendelsesdata.hendelse(
            payload = HendelseType.EnkeltplassOkonomiGodkjennUtkast(
                utkast = HendelseTypeData.utkast(),
                hovedvedtakFattetDato = fattetDato,
            ),
            deltaker = Hendelsesdata.lagDeltaker(id = deltakerId),
        )

        return HendelseMedJournalforingstatus(
            hendelse = hendelse,
            journalforingstatus = Journalforingstatus(
                hendelseId = hendelse.id,
                journalpostId = "test",
                bestillingsId = UUID.randomUUID(),
                kanIkkeDistribueres = false,
                kanIkkeJournalfores = false,
            ),
        )
    }
}
