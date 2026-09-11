package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerlisteServiceTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val deltakerlisteService = DeltakerlisteService(deltakerlisteRepository)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
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
            medAvsluttetDeltakerliste(
                sluttDato = LocalDate.of(2026, 2, 28),
            )
            assertThrows<DeltakerlisteStengtException> {
                deltakerlisteService.verifiserTilgjengeligDeltakerliste(
                    id = deltakerliste.id,
                    today = LocalDate.of(2026, 8, 29),
                )
            }
        }
    }

    @Test
    fun `verifiserTilgjengeligDeltakerliste - akkurat ved graceperiode grensen - kaster ikke exception`() {
        with(DeltakerlisteContext()) {
            medAvsluttetDeltakerliste(sluttDato = LocalDate.of(2026, 2, 28))
            deltakerlisteService.verifiserTilgjengeligDeltakerliste(
                id = deltakerliste.id,
                today = LocalDate.of(2026, 8, 28),
            )
        }
    }

    @Test
    fun `verifiserTilgjengeligDeltakerliste - deltakerlistes sluttdato er innenfor graceperiode - kaster ikke exception`() {
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

    fun medAvsluttetDeltakerliste(sluttDato: LocalDate = LocalDate.now().minusDays(1)) {
        deltakerliste = deltakerliste.copy(
            status = GjennomforingStatusType.AVSLUTTET,
            startDato = LocalDate.now().minusMonths(3),
            sluttDato = sluttDato,
        )

        repository.upsert(deltakerliste)
    }
}
