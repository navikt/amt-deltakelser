package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.extensions.skalInkluderesIHistorikk
import no.nav.amt.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.InnsokRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDatoFraImportertDeltaker
import java.time.LocalDate
import java.time.LocalDateTime
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
    /**
     * Henter historikken for deltakeren.
     *
     * Ved full historikk hentes økonomihistorikk bare når deltakeren er en enkeltplass.
     *
     * @param deltaker Deltakeren historikken gjelder
     * @param inkluderFullHistorikk om tjenesten skal hente utvidet historikk
     * @return Historikk sortert med nyeste oppføring først
     */
    fun getForDeltaker(
        deltaker: Deltaker,
        inkluderFullHistorikk: Boolean = true,
    ): List<DeltakerHistorikk> = hentHistorikk(
        deltakerId = deltaker.id,
        inkluderFullHistorikk = inkluderFullHistorikk,
        inkluderEnkeltplassOkonomiHistorikk = deltaker.erEnkeltplass,
    )

    /**
     * Henter historikken for en deltaker-ID.
     *
     * Ved full historikk ber denne overloaden også om økonomihistorikk. Repositoryet avgrenser
     * prisinfo til enkeltplasser.
     *
     * @param deltakerId ID-en til deltakeren
     * @param inkluderFullHistorikk om tjenesten skal hente utvidet historikk
     * @return Historikk sortert med nyeste oppføring først
     */
    fun getForDeltaker(
        deltakerId: UUID,
        inkluderFullHistorikk: Boolean = true,
    ): List<DeltakerHistorikk> = hentHistorikk(
        deltakerId = deltakerId,
        inkluderFullHistorikk = inkluderFullHistorikk,
        inkluderEnkeltplassOkonomiHistorikk = true,
    )

    /**
     * Henter kjernehistorikken og eventuelt den utvidede historikken for deltakeren.
     *
     * @param deltakerId ID-en til deltakeren
     * @param inkluderFullHistorikk om tjenesten skal hente utvidet historikk
     * @param inkluderEnkeltplassOkonomiHistorikk om tjenesten skal hente godkjent økonomihistorikk
     * @return Samlet historikk sortert med nyeste oppføring først
     */
    private fun hentHistorikk(
        deltakerId: UUID,
        inkluderFullHistorikk: Boolean,
        inkluderEnkeltplassOkonomiHistorikk: Boolean,
    ): List<DeltakerHistorikk> {
        // alltid hent kjernehistorikk
        val kjernehistorikk = kjernehistorikkProviders
            .mapNotNull { it(deltakerId) }
            .flatten()

        val utvidetHistorikk = if (inkluderFullHistorikk) {
            val utvidedeHistorikk = utvidetHistorikkProviders
                .mapNotNull { it(deltakerId) }
                .flatten()

            if (inkluderEnkeltplassOkonomiHistorikk) {
                utvidedeHistorikk + hentEnkeltplassOkonomiHistorikk(
                    deltakerId = deltakerId,
                    vedtakFattetTidspunkt = kjernehistorikk.getVedtakFattet(),
                )
            } else {
                utvidedeHistorikk
            }
        } else {
            emptyList()
        }

        return (kjernehistorikk + utvidetHistorikk)
            .sortedByDescending { it.sorteringsDato }
    }

    /**
     * Finner datoen for deltakerens første vedtak.
     *
     * For deltakere som er importert fra Arena, returnerer metoden søknadsdatoen fra importen.
     * Ellers bruker den fattet-datoen fra vedtaket med tidligst opprettelsestidspunkt.
     *
     * @param deltakerId ID-en til deltakeren
     * @return Datoen for søknaden eller det første vedtaket, eller `null` hvis ingen finnes
     */
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
    )

    /**
     * Henter godkjente prisinfoer og markerer prisinfoen som utløste vedtaket.
     *
     * Den nyeste prisoppføringen som ble endret senest da vedtaket ble fattet, markeres som
     * førstegangsgodkjenning. Hvis flere prisinfoer ble godkjent før eller samtidig med vedtaket,
     * utelates de eldre oppføringene. Hvis vedtaket ikke har fattet-tidspunkt, beholdes alle
     * prisinfoene uten markering.
     *
     * @param deltakerId ID-en til deltakeren
     * @param vedtakFattetTidspunkt tidspunktet vedtaket ble fattet
     * @return Prisinfoer som skal vises i historikken
     */
    private fun hentEnkeltplassOkonomiHistorikk(
        deltakerId: UUID,
        vedtakFattetTidspunkt: LocalDateTime?,
    ): List<DeltakerHistorikk> {
        val godkjentePrisinfoer = PrisinfoRepoAdapter.hentGodkjentPrisinfoForHistorikkEldsteForst(deltakerId)

        // Prisinfoene kommer eldste først, så den siste før vedtaket utløste vedtaket.
        // MERK: Flere godkjente prisinfoer før hovedvedtak er edge-case som krever
        // innsats for å fremprovosere.
        val (prisinfoerForVedtak, prisinfoerEtterVedtak) = godkjentePrisinfoer.partition { prisinfo ->
            vedtakFattetTidspunkt?.let { prisinfo.sistEndret <= it } ?: false
        }

        // Hvis flere godkjenninger ligger før vedtaket, viser vi bare den siste fordi den utløste vedtaket.
        val prisinfoerForHistorikk = if (prisinfoerForVedtak.size > 1) {
            listOf(prisinfoerForVedtak.last()) + prisinfoerEtterVedtak
        } else {
            godkjentePrisinfoer
        }

        // Hvis en prisinfo utløste vedtaket, ligger den først i listen og får markeringen.
        return prisinfoerForHistorikk.mapIndexed { index, prisinformasjon ->
            DeltakerHistorikk.EnkeltplassOkonomiGodkjent(
                prisinformasjon.copy(
                    erForsteGodkjenning = prisinfoerForVedtak.isNotEmpty() && index == 0,
                ),
            )
        }
    }

    /**
     * Henter fattet-tidspunktet fra deltakerens første vedtaksoppføring.
     *
     * @receiver Historikkoppføringene som skal undersøkes
     * @return Fattet-tidspunktet, eller `null` hvis historikken mangler vedtak eller tidspunkt
     */
    private fun List<DeltakerHistorikk>.getVedtakFattet(): LocalDateTime? = filterIsInstance<DeltakerHistorikk.Vedtak>()
        .firstOrNull()
        ?.vedtak
        ?.fattet
}
