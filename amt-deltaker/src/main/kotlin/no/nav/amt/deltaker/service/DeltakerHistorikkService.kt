package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.extensions.skalInkluderesIHistorikk
import no.nav.amt.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.PrisinfoRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.InnsokRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDatoFraImportertDeltaker
import java.time.LocalDate
import java.util.UUID

class DeltakerHistorikkService(
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val vedtakRepository: VedtakRepository,
    private val forslagRepository: ForslagRepository,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val innsokRepository: InnsokRepository,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val vurderingRepository: VurderingRepository,
) {
    fun getForDeltaker(
        id: UUID,
        inkluderFullHistorikk: Boolean = true,
    ): List<DeltakerHistorikk> {
        val historikkProviders = if (inkluderFullHistorikk) {
            kjernehistorikkProviders.plus(utvidetHistorikkProviders)
        } else {
            kjernehistorikkProviders
        }

        return historikkProviders
            .mapNotNull { it(id) }
            .flatten()
            .sortedByDescending { it.sorteringsDato }
    }

    fun getForsteVedtakFattet(deltakerId: UUID): LocalDate? {
        val deltakerhistorikk = getForDeltaker(deltakerId)
        deltakerhistorikk.getInnsoktDatoFraImportertDeltaker()?.let { return it }

        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }

        return forsteVedtak?.fattet?.toLocalDate()
    }

    private val kjernehistorikkProviders = listOf<(UUID) -> List<DeltakerHistorikk>?>(
        { deltakerId -> deltakerEndringRepository.getForDeltaker(deltakerId).map { DeltakerHistorikk.Endring(it) } },
        { deltakerId -> vedtakRepository.getForDeltaker(deltakerId)?.let { listOf(DeltakerHistorikk.Vedtak(it)) } },
        { deltakerId ->
            importertFraArenaRepository
                .getForDeltaker(deltakerId)
                ?.let { listOf(DeltakerHistorikk.ImportertFraArena(it)) }
        },
        { deltakerId ->
            innsokRepository
                .getForDeltaker(deltakerId)
                .getOrNull()
                ?.let { listOf(DeltakerHistorikk.InnsokPaaFellesOppstart(it)) }
        },
        // EndringFraArrangor er en del av kjernehistorikken fordi
        // `LeggTilOppstartsdato` brukes av `toDeltakelsesmengder()` for å avgrense perioden.
        // Uten den kan deltakere med arrangør-satt oppstartsdato få deltakelsesmengder
        // med datoer før faktisk startdato.
        { deltakerId ->
            endringFraArrangorRepository
                .getForDeltaker(deltakerId)
                .map { DeltakerHistorikk.EndringFraArrangor(it) }
        },
    )

    private val utvidetHistorikkProviders = listOf<(UUID) -> List<DeltakerHistorikk>?>(
        { deltakerId ->
            forslagRepository
                .getForDeltaker(
                    deltakerId,
                ).filter { it.skalInkluderesIHistorikk() }
                .map { DeltakerHistorikk.Forslag(it) }
        },
        { deltakerId ->
            vurderingRepository
                .getForDeltaker(deltakerId)
                .map { DeltakerHistorikk.VurderingFraArrangor(it.toVurderingFraArrangorData()) }
        },
        { deltakerId ->
            endringFraTiltakskoordinatorRepository
                .getForDeltaker(deltakerId)
                .map { DeltakerHistorikk.EndringFraTiltakskoordinator(it) }
        },
        { deltakerId ->
            PrisinfoRepository
                .hentPrisinfoListeForHistorikk(deltakerId)
                .map { prisinformasjonForHistorikk ->
                    DeltakerHistorikk.EnkeltplassOkonomiGodkjent(prisinformasjonForHistorikk)
                }
        },
    )
}
