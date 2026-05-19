package no.nav.amt.deltaker.api.response

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.internapi.deltaker.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakerResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakereResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorNavBrukerResponse
import java.time.Duration

/**
 * Bygger spisset respons for tiltakskoordinator-lista (`POST /tiltakskoordinator/deltakere/{gjennomforingId}`).
 * Optimalisert for kall med mange deltakere (kan være >2000 per request).
 *
 * Henter data i **to SQL-spørringer**:
 *   1. [DeltakerlisteRepository.get] — deltakerliste/tiltakstype/arrangør (1 rad).
 *   2. [TiltakskoordinatorViewRepository.getDeltakerePaged] — deltakere med berikede felt (N rader).
 *
 * Deltakerliste-kolonnene gjentas ikke for hver deltaker — sparer båndbredde ved store lister.
 *
 * Eneste HTTP-fallback er for `digital_bruker_cache`-entries som er utdaterte eller mangler.
 */
class TiltakskoordinatorResponseBuilder(
    private val viewRepository: TiltakskoordinatorViewRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val arrangorService: ArrangorService,
    private val digitalBrukerService: DigitalBrukerService,
) {
    private val itemCountCache: Cache<String, Int> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(CACHE_EXPIRATION_MINUTES))
        .build()

    private fun getItemCount(request: TiltaksKoordinatorDeltakerlisteRequest): Int = itemCountCache.get(request.itemCountCacheKey()) {
        viewRepository.getDeltakereTotalCount(request)
    }

    /**
     * Henter gjennomføring og deltakere for en gjennomføring-id.
     */
    suspend fun buildResponse(request: TiltaksKoordinatorDeltakerlisteRequest): TiltakskoordinatorDeltakereResponse {
        val gjennomforing = deltakerlisteRepository.get(request.gjennomforingId).getOrNull()
            ?: return TiltakskoordinatorDeltakereResponse(
                gjennomforing = null,
                paginatedResult = PaginatedResult(
                    pageSize = request.pageRequest.pageSize,
                    data = emptyList(),
                ),
            )

        val gjennomforingResponse = buildGjennomforingResponse(gjennomforing)

        val rows = viewRepository.getDeltakerePaged(request)

        // Hent digital-status for deltakere uten fersk cache-entry
        // HandlingerKnapp i frontend vises kun for GjennomforingPameldingType.TRENGER_GODKJENNING
        val erDigitalFallbackMap = rows
            .takeIf { gjennomforing.deltakelserMaaGodkjennes }
            ?.let { hentManglendeDigitalStatus(it) }

        return TiltakskoordinatorDeltakereResponse(
            gjennomforing = gjennomforingResponse,
            paginatedResult = PaginatedResult(
                totalCount = getItemCount(request),
                pageSize = request.pageRequest.pageSize,
                data = rows.map { row ->
                    buildDeltakerResponse(
                        row = row,
                        erDigitalFallbackMap = erDigitalFallbackMap,
                    )
                },
            ),
        )
    }

    private fun buildGjennomforingResponse(gjennomforing: Deltakerliste): GjennomforingResponse = GjennomforingResponse(
        id = gjennomforing.id,
        tiltakstype = gjennomforing.tiltakstype,
        navn = gjennomforing.navn,
        status = gjennomforing.status,
        startDato = gjennomforing.startDato,
        sluttDato = gjennomforing.sluttDato,
        antallPlasser = gjennomforing.antallPlasser,
        oppstart = gjennomforing.oppstart,
        apentForPamelding = gjennomforing.apentForPamelding,
        oppmoteSted = gjennomforing.oppmoteSted,
        arrangor = gjennomforing.arrangor?.let {
            ArrangorResponse(
                navn = arrangorService.getArrangorNavn(it, gjennomforing.gjennomforingstype),
                organisasjonsnummer = it.organisasjonsnummer,
            )
        },
        pameldingstype = gjennomforing.pameldingstype,
        type = gjennomforing.gjennomforingstype,
        kodeverkValg = emptySet(),
        sertifiseringValg = emptySet(),
    )

    private fun buildDeltakerResponse(
        row: TiltakskoordinatorDeltakerRow,
        erDigitalFallbackMap: Map<String, Boolean>?,
    ): TiltakskoordinatorDeltakerResponse {
        val erDigital = row.erDigitalCached
            ?: erDigitalFallbackMap?.get(row.personident)
            ?: true

        val ikkeDigitalOgManglerAdresse = !(erDigital || row.harAdresse)

        return TiltakskoordinatorDeltakerResponse(
            id = row.id,
            status = row.status,
            navBruker = TiltakskoordinatorNavBrukerResponse(
                personident = row.personident,
                fornavn = row.fornavn,
                mellomnavn = row.mellomnavn,
                etternavn = row.etternavn,
                erSkjermet = row.erSkjermet,
                adressebeskyttelse = row.adressebeskyttelse,
                navEnhet = row.navEnhetNavn,
                ikkeDigitalOgManglerAdresse = ikkeDigitalOgManglerAdresse,
            ),
            startdato = row.startdato,
            sluttdato = row.sluttdato,
            soktInnDato = row.soktInnDato,
            erManueltDeltMedArrangor = row.erManueltDeltMedArrangor,
            harAktivtForslag = row.harAktivtForslag,
            sisteVurderingstype = row.sisteVurderingstype,
        )
    }

    /**
     * Henter digital-status via HTTP for deltakere uten fersk cache-entry i `digital_bruker_cache`.
     */
    private suspend fun hentManglendeDigitalStatus(rows: List<TiltakskoordinatorDeltakerRow>): Map<String, Boolean> {
        val manglendePersonidenter = rows
            .filterNot { it.harAdresse }
            .filter { it.erDigitalCached == null }
            .map { it.personident }
            .toSet()

        if (manglendePersonidenter.isEmpty()) return emptyMap()

        return digitalBrukerService.hentErDigitalForPersonidenter(manglendePersonidenter)
    }

    companion object {
        private const val CACHE_EXPIRATION_MINUTES = 15L
    }
}
