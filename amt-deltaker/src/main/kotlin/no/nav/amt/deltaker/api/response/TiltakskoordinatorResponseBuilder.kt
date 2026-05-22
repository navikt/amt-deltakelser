package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorNavBrukerResponse

/**
 * Bygger spisset respons for tiltakskoordinator-lista (`POST /tiltakskoordinator/deltakere/{gjennomforingId}`).
 * Optimalisert for kall med mange deltakere (kan være >2000 per request).
 *
 * Henter data i **to SQL-spørringer**:
 *   1. [DeltakerlisteRepository.get] — deltakerliste/tiltakstype/arrangør (1 rad).
 *   2. [TiltakskoordinatorViewRepository.getDeltakere] — deltakere med berikede felt (N rader).
 *
 * Deltakerliste-kolonnene gjentas ikke for hver deltaker — sparer båndbredde ved store lister.
 *
 * Eneste HTTP-fallback er for `digital_bruker_cache`-entries som er utdaterte eller mangler.
 */
class TiltakskoordinatorResponseBuilder(
    private val viewRepository: TiltakskoordinatorViewRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val digitalBrukerService: DigitalBrukerService,
) {
    /**
     * Henter gjennomføring og deltakere for en gjennomføring-id.
     */
    suspend fun buildResponse(request: TiltaksKoordinatorDeltakerlisteRequest): PaginatedResult<TiltakskoordinatorDeltakerResponse> {
        val gjennomforing = deltakerlisteRepository.get(request.gjennomforingId).getOrNull()
            ?: return PaginatedResult(
                pageSize = 0,
                data = emptyList(),
            )

        val rows = viewRepository.getDeltakere(request = request)

        // Hent digital-status for deltakere uten fersk cache-entry
        // HandlingerKnapp i frontend vises kun for GjennomforingPameldingType.TRENGER_GODKJENNING
        val erDigitalFallbackMap = rows
            .takeIf { gjennomforing.deltakelserMaaGodkjennes }
            ?.let { hentManglendeDigitalStatus(it) }

        return PaginatedResult(
            totalCount = rows.size,
            pageSize = rows.size,
            data = rows.map { row ->
                buildDeltakerResponse(
                    row = row,
                    erDigitalFallbackMap = erDigitalFallbackMap,
                )
            },
        )
    }

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
}
