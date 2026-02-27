package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.getInnsoktDatoFraImportertDeltaker
import no.nav.amt.deltaker.deltaker.extensions.skalInkluderesIHistorikk
import no.nav.amt.deltaker.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
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
) {
    fun getForDeltaker(id: UUID): List<DeltakerHistorikk> {
        val endringer = deltakerEndringRepository.getForDeltaker(id).map { DeltakerHistorikk.Endring(it) }
        val vedtak = vedtakRepository
            .getForDeltaker(id)
            ?.let { listOf(DeltakerHistorikk.Vedtak(it)) }
            ?: emptyList()

        val forslag = forslagRepository.getForDeltaker(id).filter { it.skalInkluderesIHistorikk() }.map { DeltakerHistorikk.Forslag(it) }

        val vurderinger = vurderingRepository
            .getForDeltaker(id)
            .map { DeltakerHistorikk.VurderingFraArrangor(it.toVurderingFraArrangorData()) }

        val endringerFraArrangor = endringFraArrangorRepository
            .getForDeltaker(id)
            .map { DeltakerHistorikk.EndringFraArrangor(it) }

        val importertFraArena = importertFraArenaRepository
            .getForDeltaker(id)
            ?.let { listOf(DeltakerHistorikk.ImportertFraArena(it)) }
            ?: emptyList()

        val endringFraTiltakskoordinator = endringFraTiltakskoordinatorRepository
            .getForDeltaker(id)
            .map { DeltakerHistorikk.EndringFraTiltakskoordinator(it) }

        val innsok = innsokPaaFellesOppstartRepository
            .getForDeltaker(id)
            .getOrNull()
            ?.let { listOf(DeltakerHistorikk.InnsokPaaFellesOppstart(it)) }
            ?: emptyList()

        val historikk = endringer
            .asSequence()
            .plus(vedtak)
            .plus(importertFraArena)
            .plus(innsok)
            .plus(endringFraTiltakskoordinator)
            .plus(forslag)
            .plus(endringerFraArrangor)
            .plus(vurderinger)
            .sortedByDescending {
                when (it) {
                    is DeltakerHistorikk.Endring -> it.endring.endret
                    is DeltakerHistorikk.Vedtak -> it.vedtak.sistEndret
                    is DeltakerHistorikk.Forslag -> it.forslag.sistEndret
                    is DeltakerHistorikk.EndringFraArrangor -> it.endringFraArrangor.opprettet
                    is DeltakerHistorikk.ImportertFraArena -> it.importertFraArena.importertDato
                    is DeltakerHistorikk.VurderingFraArrangor -> it.data.opprettet
                    is DeltakerHistorikk.EndringFraTiltakskoordinator -> it.endringFraTiltakskoordinator.endret
                    is DeltakerHistorikk.InnsokPaaFellesOppstart -> it.data.innsokt
                }
            }.toList()

        return historikk
    }

    fun getForsteVedtakFattet(deltakerId: UUID): LocalDate? {
        val deltakerhistorikk = getForDeltaker(deltakerId)
        deltakerhistorikk.getInnsoktDatoFraImportertDeltaker()?.let { return it }

        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }

        return forsteVedtak?.fattet?.toLocalDate()
    }
}
