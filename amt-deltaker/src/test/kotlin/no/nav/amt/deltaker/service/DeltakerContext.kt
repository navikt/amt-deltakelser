package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.TestPostgresContainer
import no.nav.amt.lib.testing.utils.TestData
import java.time.LocalDate

data class DeltakerContext(
    val navEnhet: NavEnhet = TestData.lagNavEnhet(),
    val veileder: NavAnsatt = TestData.lagNavAnsatt(navEnhetId = navEnhet.id),
    var deltaker: Deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
        status = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
        startdato = LocalDate.now().minusMonths(1),
        sluttdato = LocalDate.now().plusMonths(3),
        deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
            tiltakstype = no.nav.amt.deltaker.utils.data.TestData
                .lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        ),
        navBruker = TestData.lagNavBruker(navVeilederId = veileder.id, navEnhetId = navEnhet.id),
    ),
) {
    var vedtak: Vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
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
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = tiltakskode,
                ),
            ),
        )
    }

    fun medVedtak(fattet: Boolean = true) {
        vedtak = vedtak.copy(fattet = if (fattet) deltaker.sistEndret.minusMonths(3) else null)
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        deltaker = deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
    }
}
