package no.nav.amt.deltaker.api.response

import io.ktor.network.sockets.SocketTimeoutException
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.PaginatedResult
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerIListeResponse
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorNavBrukerResponse
import java.net.SocketException
import java.sql.SQLException
import java.util.UUID

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
    private val deltakerLaaseService: DeltakerLaaseService,
) {
    /**
     * Henter gjennomføring og deltakere for en gjennomføring-id.
     */
    suspend fun buildResponse(request: TiltaksKoordinatorDeltakerlisteRequest): PaginatedResult<TiltakskoordinatorDeltakerIListeResponse> {
        val gjennomforing = deltakerlisteRepository.get(request.gjennomforingId).getOrNull()
            ?: return PaginatedResult(
                pageSize = 0,
                data = emptyList(),
            )

        val rows = viewRepository.getDeltakere(request = request)

        // opprett et map med deltaker-id til laase-status for alle deltakere i lista i én spørring
        val deltakerIdToErLaastForEndringerMap = deltakerLaaseService.erLaastForEndringerForDeltakere(
            deltakerIdToPersonIdentMap = rows.associate { it.id to it.personident },
            gjennomforingId = request.gjennomforingId,
        )

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
                    deltaker = row,
                    deltakerIdToErLaastForEndringerMap = deltakerIdToErLaastForEndringerMap,
                    erDigitalFallbackMap = erDigitalFallbackMap,
                )
            },
        )
    }

    suspend fun buildResponse(
        gjennomforingId: UUID,
        deltakerId: UUID,
        exception: Throwable?,
    ) = buildResponse(gjennomforingId, listOf(deltakerId), mapOf(deltakerId to exception)).first()

    suspend fun buildResponse(
        gjennomforingId: UUID,
        deltakerIder: List<UUID>,
        feilkoder: Map<UUID, Throwable?>,
    ): List<DeltakerOppdateringResponse> {
        val deltakere = viewRepository.getDeltakere(deltakerIder)

        // opprett et map med deltaker-id til laase-status for alle deltakere i lista i én spørring
        val deltakerIdToErLaastForEndringerMap = deltakerLaaseService.erLaastForEndringerForDeltakere(
            deltakerIdToPersonIdentMap = deltakere.associate { it.id to it.personident },
            gjennomforingId = gjennomforingId,
        )
        val erDigitalMap = hentManglendeDigitalStatus(deltakere)
        return deltakere.map { deltaker ->
            DeltakerOppdateringResponse(
                feilkode = feilkoder[deltaker.id]?.toOppdateringFeilkode(),
                deltaker = buildDeltakerResponse(
                    deltaker = deltaker,
                    deltakerIdToErLaastForEndringerMap = deltakerIdToErLaastForEndringerMap,
                    erDigitalFallbackMap = erDigitalMap,
                ),
            )
        }
    }

    private fun buildDeltakerResponse(
        deltaker: TiltakskoordinatorDeltakerRow,
        deltakerIdToErLaastForEndringerMap: Map<UUID, Boolean>,
        erDigitalFallbackMap: Map<String, Boolean>?,
    ): TiltakskoordinatorDeltakerIListeResponse {
        val erDigital = deltaker.erDigitalCached
            ?: erDigitalFallbackMap?.get(deltaker.personident)
            ?: true

        val ikkeDigitalOgManglerAdresse = !(erDigital || deltaker.harAdresse)

        val erLaastForEndringer = deltakerIdToErLaastForEndringerMap[deltaker.id]
            ?: throw NoSuchElementException("Fant ikke deltaker-id ${deltaker.id} i map")

        return TiltakskoordinatorDeltakerIListeResponse(
            id = deltaker.id,
            status = deltaker.status,
            navBruker = TiltakskoordinatorNavBrukerResponse(
                personident = deltaker.personident,
                fornavn = deltaker.fornavn,
                mellomnavn = deltaker.mellomnavn,
                etternavn = deltaker.etternavn,
                erSkjermet = deltaker.erSkjermet,
                adressebeskyttelse = deltaker.adressebeskyttelse,
                navEnhet = deltaker.navEnhetNavn,
                ikkeDigitalOgManglerAdresse = ikkeDigitalOgManglerAdresse,
            ),
            startdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            soktInnDato = deltaker.soktInnDato,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            harAktivtForslag = deltaker.harAktivtForslag,
            sisteVurderingstype = deltaker.sisteVurderingstype,
            kanEndres = !erLaastForEndringer,
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

    private fun Throwable.toOppdateringFeilkode() = when (this) {
        is IllegalStateException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is IllegalArgumentException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is SQLException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is SocketTimeoutException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
        is SocketException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
        is Exception -> null
        else -> null
    }
}
