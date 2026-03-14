package no.nav.tiltaksarrangor.consumer.jobs

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.consumer.jobs.leaderelection.LeaderElection
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RyddejobbTest(
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val ansattRepository: AnsattRepository,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val ryddejobb: Ryddejobb,
	@MockkBean private val leaderElection: LeaderElection,
) : IntegrationTest() {
	@BeforeEach
	fun setUp() {
		every { leaderElection.isLeader() } returns true
	}

	@Test
	fun `slettUtdaterteDeltakerlisterOgDeltakere - deltakerliste avsluttet for 42 dager siden - sletter deltakerliste og deltaker`() {
		with(DeltakerContext(applicationContext)) {
			deltakerlisteRepository.insertOrUpdateDeltakerliste(
				deltakerliste.copy(
					status = GjennomforingStatusType.AVSLUTTET,
					sluttDato = LocalDate.now().minusDays(42),
				),
			)
			ryddejobb.slettUtdaterteDeltakerlisterOgDeltakere()

			deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldBe null
			deltakerRepository.getDeltaker(deltaker.id) shouldBe null
			ansattRepository.getKoordinatorDeltakerlisteDboListe(koordinator.id).size shouldBe 0
			ansattRepository.getVeilederDeltakerDboListe(veileder.id).size shouldBe 0
		}
	}

	@Test
	fun `slettUtdaterteDeltakerlisterOgDeltakere - deltakerliste avsluttet for 38 dager siden - sletter ikke deltakerliste`() {
		val deltakerliste = getDeltakerliste(UUID.randomUUID()).copy(
			status = GjennomforingStatusType.AVSLUTTET,
			sluttDato = LocalDate.now().minusDays(38),
		)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)

		ryddejobb.slettUtdaterteDeltakerlisterOgDeltakere()

		deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
	}

	@Test
	fun `slettUtdaterteDeltakerlisterOgDeltakere - deltaker har sluttet for 42 dager siden - sletter deltaker`() {
		with(DeltakerContext(applicationContext)) {
			medStatus(DeltakerStatus.Type.HAR_SLUTTET, 42)
			medEndringsmelding()

			ryddejobb.slettUtdaterteDeltakerlisterOgDeltakere()

			deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
			deltakerRepository.getDeltaker(deltaker.id) shouldBe null
			ansattRepository.getKoordinatorDeltakerlisteDboListe(koordinator.id).size shouldBe 1
			ansattRepository.getVeilederDeltakerDboListe(veileder.id).size shouldBe 0
			endringsmeldingRepository.getEndringsmeldingerForDeltaker(deltaker.id) shouldBe emptyList()
		}
	}

	@Test
	fun `slettUtdaterteDeltakerlisterOgDeltakere - deltaker har sluttet for 38 dager siden - sletter ikke deltaker`() {
		with(DeltakerContext(applicationContext)) {
			medStatus(DeltakerStatus.Type.HAR_SLUTTET, 38)
			medEndringsmelding()

			ryddejobb.slettUtdaterteDeltakerlisterOgDeltakere()

			deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
			deltakerRepository.getDeltaker(deltaker.id) shouldNotBe null
		}
	}

	@Test
	fun `slettUtdaterteDeltakerlisterOgDeltakere - ingenting skal slettes - sletter ingenting`() {
		with(DeltakerContext(applicationContext)) {
			ryddejobb.slettUtdaterteDeltakerlisterOgDeltakere()

			deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
			deltakerRepository.getDeltaker(deltaker.id) shouldNotBe null
		}
	}
}
