package no.nav.tiltaksarrangor.consumer.jobs

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RyddejobbTest(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val endringsmeldingRepository: EndringsmeldingRepository,
    private val ryddejobb: Ryddejobb,
) : IntegrationTestBase() {
    @Test
    fun `slettUtdaterteDeltakerlisterOgDeltakere - deltakerliste avsluttet for 42 dager siden - sletter deltakerliste og deltaker`() {
        with(DeltakerContext(applicationContext)) {
            deltakerlisteRepository.insertOrUpdateDeltakerliste(
                deltakerliste.copy(
                    status = GjennomforingStatusType.AVSLUTTET,
                    sluttDato = LocalDate.now().minusDays(42),
                ),
            )
            ryddejobb.slettUtdaterteDeltakerlisterOgDeltakereInternal()

            deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldBe null
            deltakerRepository.getDeltaker(deltaker.id) shouldBe null
            tiltaksarrangorAnsattRepository.getKoordinatorDeltakerlisteDboListe(koordinator.id).size shouldBe 0
            tiltaksarrangorAnsattRepository.getVeilederDeltakerDboListe(veileder.id).size shouldBe 0
        }
    }

    @Test
    fun `slettUtdaterteDeltakerlisterOgDeltakere - deltakerliste avsluttet for 38 dager siden - sletter ikke deltakerliste`() {
        val deltakerliste = getDeltakerliste(UUID.randomUUID()).copy(
            status = GjennomforingStatusType.AVSLUTTET,
            sluttDato = LocalDate.now().minusDays(38),
        )
        deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)

        ryddejobb.slettUtdaterteDeltakerlisterOgDeltakereInternal()

        deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
    }

    @Test
    fun `slettUtdaterteDeltakerlisterOgDeltakere - deltaker har sluttet for 42 dager siden - sletter deltaker`() {
        with(DeltakerContext(applicationContext)) {
            medStatus(DeltakerStatus.Type.HAR_SLUTTET, 42)
            medEndringsmelding()

            ryddejobb.slettUtdaterteDeltakerlisterOgDeltakereInternal()

            deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
            deltakerRepository.getDeltaker(deltaker.id) shouldBe null
            tiltaksarrangorAnsattRepository.getKoordinatorDeltakerlisteDboListe(koordinator.id).size shouldBe 1
            tiltaksarrangorAnsattRepository.getVeilederDeltakerDboListe(veileder.id).size shouldBe 0
            endringsmeldingRepository.getEndringsmeldingerForDeltaker(deltaker.id) shouldBe emptyList()
        }
    }

    @Test
    fun `slettUtdaterteDeltakerlisterOgDeltakere - deltaker har sluttet for 38 dager siden - sletter ikke deltaker`() {
        with(DeltakerContext(applicationContext)) {
            medStatus(DeltakerStatus.Type.HAR_SLUTTET, 38)
            medEndringsmelding()

            ryddejobb.slettUtdaterteDeltakerlisterOgDeltakereInternal()

            deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
            deltakerRepository.getDeltaker(deltaker.id) shouldNotBe null
        }
    }

    @Test
    fun `slettUtdaterteDeltakerlisterOgDeltakere - ingenting skal slettes - sletter ingenting`() {
        with(DeltakerContext(applicationContext)) {
            ryddejobb.slettUtdaterteDeltakerlisterOgDeltakereInternal()

            deltakerlisteRepository.getDeltakerliste(deltakerliste.id) shouldNotBe null
            deltakerRepository.getDeltaker(deltaker.id) shouldNotBe null
        }
    }
}
