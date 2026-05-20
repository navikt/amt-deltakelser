package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.veileder.DeltakerLaaseService.Companion.paameldtTidspunkt
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakerResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakereResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorNavBrukerResponse
import java.util.UUID

/**
 * Bygger spisset respons for tiltakskoordinator-lista (`GET /tiltakskoordinator/deltakere/{gjennomforingId}`).
 * Optimalisert for kall med mange deltakere (kan være >2000 per request).
 *
 * Henter data i **to SQL-spørringer**:
 *   1. [DeltakerlisteRepository.get] — deltakerliste/tiltakstype/arrangør (1 rad).
 *   2. [TiltakskoordinatorViewRepository.getDeltakere] — alle deltakere med berikede felt (N rader).
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
    /**
     * Henter gjennomføring og deltakere for en gjennomføring-id.
     *
     * En person kan ha flere deltakelser på samme gjennomføring (f.eks. en gammel avsluttet
     * + en ny aktiv). Vi returnerer kun den nyeste — eldre deltakelser er uinteressante for frontend.
     */
    suspend fun buildResponse(gjennomforingId: UUID): TiltakskoordinatorDeltakereResponse {
        val gjennomforing = deltakerlisteRepository.get(gjennomforingId).getOrNull()
            ?: return TiltakskoordinatorDeltakereResponse(gjennomforing = null, deltakere = emptyList())

        val gjennomforingResponse = buildGjennomforingResponse(gjennomforing)

        val rows = viewRepository.getDeltakere(gjennomforingId)

        // Behold kun den nyeste deltakelsen per person (eldre er "låst" og uinteressant)
        val nyesteDeltakelsePerPerson = velgNyesteDeltakelsePerPerson(rows)

        // Hent digital-status for deltakere uten fersk cache-entry
        // HandlingerKnapp i frontend vises kun for GjennomforingPameldingType.TRENGER_GODKJENNING
        val erDigitalFallbackMap = nyesteDeltakelsePerPerson
            .takeIf { gjennomforing.deltakelserMaaGodkjennes }
            ?.let { hentManglendeDigitalStatus(it) }

        return TiltakskoordinatorDeltakereResponse(
            gjennomforing = gjennomforingResponse,
            deltakere = nyesteDeltakelsePerPerson.map
                { row ->
                    buildDeltakerResponse(
                        row = row,
                        erDigitalFallbackMap = erDigitalFallbackMap,
                    )
                },
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
     * Velger den nyeste deltakelsen per person. Speiler logikken i `DeltakerLaaseService`:
     * sortér etter påmeldt-tidspunkt (`vedtak.fattet` eller `innsoektDatoArena`) synkende,
     * deretter `status.gyldigFra` synkende. Foretrekk aktiv status hvis flere kandidater finnes.
     */
    private fun velgNyesteDeltakelsePerPerson(rows: List<TiltakskoordinatorDeltakerRow>): List<TiltakskoordinatorDeltakerRow> = rows
        .groupBy { it.personident }
        .map { (_, deltakelser) ->
            if (deltakelser.size == 1) return@map deltakelser.single()

            val sortert = deltakelser.sortedWith(
                compareByDescending<TiltakskoordinatorDeltakerRow> {
                    paameldtTidspunkt(
                        vedtakFattet = it.vedtakFattet,
                        innsoektDatoFraArena = it.innsoektDatoArena,
                    )
                }.thenByDescending { it.status.gyldigFra },
            )

            sortert.firstOrNull { it.status.type in AKTIVE_STATUSER } ?: sortert.first()
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
