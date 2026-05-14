package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.extensions.skalInkluderesIHistorikk
import no.nav.amt.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.InnsokPaaFellesOppstartRepository
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
    private val innsokPaaFellesOppstartRepository: InnsokPaaFellesOppstartRepository,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val vurderingRepository: VurderingRepository,
    private val deltakerRepository: DeltakerRepository,
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

    /**
     * Utleder soktInnDato for én deltaker. Delegerer til [getSoktInnDatoer] som henter
     * datoen i **ett** spisset SQL-oppslag (`COALESCE` på arena-import, innsøk på felles
     * oppstart og første vedtak).
     *
     * Prioriterer: ImportertFraArena → InnsokPaaFellesOppstart → Vedtak.opprettet
     */
    fun getSoktInnDato(deltakerId: UUID): LocalDate? = getSoktInnDatoer(setOf(deltakerId))[deltakerId]

    /**
     * Bulk-variant av [getSoktInnDato]. Henter "søkt inn"-dato for alle [deltakerIder] i
     * **ett** spisset SQL-oppslag som bruker `COALESCE` på arena-import, innsøk på felles
     * oppstart og første vedtak. Erstatter tidligere implementasjon som gjorde opptil 3
     * sekvensielle DB-oppslag per deltaker. Egnet for store kall som tiltakskoordinator-lista.
     *
     * @return Map fra deltaker-id til søkt-inn-dato (`null` for deltakere uten Arena-import,
     * innsøk på felles oppstart eller vedtak).
     */
    fun getSoktInnDatoer(deltakerIder: Set<UUID>): Map<UUID, LocalDate?> = deltakerRepository.getSoktInnDatoer(deltakerIder)

    private val kjernehistorikkProviders = listOf<(UUID) -> List<DeltakerHistorikk>?>(
        { deltakerId -> deltakerEndringRepository.getForDeltaker(deltakerId).map { DeltakerHistorikk.Endring(it) } },
        { deltakerId -> vedtakRepository.getForDeltaker(deltakerId)?.let { listOf(DeltakerHistorikk.Vedtak(it)) } },
        { deltakerId ->
            importertFraArenaRepository
                .getForDeltaker(deltakerId)
                ?.let { listOf(DeltakerHistorikk.ImportertFraArena(it)) }
        },
        { deltakerId ->
            innsokPaaFellesOppstartRepository
                .getForDeltaker(deltakerId)
                .getOrNull()
                ?.let { listOf(DeltakerHistorikk.InnsokPaaFellesOppstart(it)) }
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
            endringFraArrangorRepository
                .getForDeltaker(deltakerId)
                .map { DeltakerHistorikk.EndringFraArrangor(it) }
        },
        { deltakerId ->
            endringFraTiltakskoordinatorRepository
                .getForDeltaker(deltakerId)
                .map { DeltakerHistorikk.EndringFraTiltakskoordinator(it) }
        },
    )
}
