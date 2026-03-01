package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.TestPostgresContainer
import java.time.LocalDate

data class DeltakerContext(
    val navEnhet: NavEnhet = lagNavEnhet(),
    val veileder: NavAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id),
    var deltaker: Deltaker = lagDeltaker(
        status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
        startdato = LocalDate.now().minusMonths(1),
        sluttdato = LocalDate.now().plusMonths(3),
        deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        ),
        navBruker = lagNavBruker(navVeilederId = veileder.id, navEnhetId = navEnhet.id),
    ),
) {
    var vedtak: Vedtak = lagVedtak(
        deltakerVedVedtak = deltaker,
        fattet = deltaker.sistEndret.minusMonths(3),
        opprettetAv = veileder,
        opprettetAvEnhet = navEnhet,
    )
    val historikk: MutableList<DeltakerHistorikk> = mutableListOf(DeltakerHistorikk.Vedtak(vedtak))

    val vedtakRepository = VedtakRepository()

    init {
        TestPostgresContainer.bootstrap()
        NavEnhetRepository().upsert(navEnhet)
        NavAnsattRepository().upsert(veileder)
    }

    fun withTiltakstype(tiltakskode: Tiltakskode) {
        deltaker = deltaker.copy(
            deltakerliste = lagDeltakerliste(tiltakstype = lagTiltakstype(tiltakskode = tiltakskode)),
        )
    }

    fun medVedtak(fattet: Boolean = true) {
        vedtak = vedtak.copy(fattet = if (fattet) deltaker.sistEndret.minusMonths(3) else null)
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        deltaker = deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
    }
}
