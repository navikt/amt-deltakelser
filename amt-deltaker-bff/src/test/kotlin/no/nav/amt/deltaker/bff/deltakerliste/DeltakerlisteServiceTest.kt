package no.nav.amt.deltaker.bff.deltakerliste

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteServiceTest {
    private val deltakerlisteService = DeltakerlisteService(DeltakerlisteRepository())

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `get - deltakerliste har felles oppstart - returnere success`() {
        with(DeltakerlisteContext()) {
            deltakerlisteService.get(deltakerliste.id).isSuccess shouldBe true
        }
    }

    @Test
    fun `get - deltakerliste har lopende oppstart - returnere success`() {
        with(DeltakerlisteContext(Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)) {
            val result = deltakerlisteService.get(deltakerliste.id)

            result.isSuccess shouldBe true
        }
    }

    @Test
    fun `verifiserTilgjengeligDeltakerliste - deltakerliste har felles oppstart - kaster ikke exception`() {
        with(DeltakerlisteContext()) {
            deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerliste.id)
        }
    }

    @Test
    fun `verifiserTilgjengeligDeltakerliste - deltakerliste har lopende oppstart - kaster ikke exception`() {
        with(DeltakerlisteContext(Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)) {
            deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerliste.id)
        }
    }

    @Test
    fun `verifiserTilgjengeligDeltakerliste - deltakerlistes sluttdato og graceperiode er passert - kaster exception`() {
        with(DeltakerlisteContext()) {
            medAvsluttetDeltakerliste()
            assertThrows<DeltakerlisteStengtException> {
                deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerliste.id)
            }
        }
    }

    @Test
    fun `verifiserTilgjengeligDeltakerliste - deltakerlistes sluttdato er ikke passert - kaster ikke exception`() {
        with(DeltakerlisteContext()) {
            deltakerlisteService.verifiserTilgjengeligDeltakerliste(deltakerliste.id)
        }
    }
}

data class DeltakerlisteContext(
    val tiltak: Tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    var deltakerliste: Deltakerliste = TestData.lagDeltakerliste(
        tiltakstype = TestData.lagTiltakstype(tiltakskode = tiltak),
        oppstart = if (tiltak in setOf(
                Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                Tiltakskode.JOBBKLUBB,
            )
        ) {
            Oppstartstype.FELLES
        } else {
            Oppstartstype.LOPENDE
        },
    ),
) {
    val repository = DeltakerlisteRepository()

    init {
        TestRepository.insert(deltakerliste)
    }

    fun medAvsluttetDeltakerliste() {
        deltakerliste = deltakerliste.copy(
            status = GjennomforingStatusType.AVSLUTTET,
            startDato = LocalDate.now().minusMonths(3),
            sluttDato = LocalDate.now().minus(DeltakerlisteService.tiltakskoordinatorGraceperiode).minusDays(1),
        )

        repository.upsert(deltakerliste)
    }
}
