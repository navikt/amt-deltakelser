package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.repository.GjennomforingRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakerResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorDeltakereResponse
import no.nav.amt.internapi.deltaker.response.TiltakskoordinatorNavBrukerResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.toTitleCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Bygger spisset respons for tiltakskoordinator-lista (`GET /tiltakskoordinator/deltakere/{gjennomforingId}`).
 * Optimalisert for kall med mange deltakere (kan være >2000 per request).
 *
 * Henter data i **to SQL-spørringer**:
 *   1. [TiltakskoordinatorViewRepository.getGjennomforing] — deltakerliste/tiltakstype/arrangør (1 rad).
 *   2. [TiltakskoordinatorViewRepository.getDeltakere] — alle deltakere med berikede felt (N rader).
 *
 * Deltakerliste-kolonnene gjentas ikke for hver deltaker — sparer båndbredde ved store lister.
 *
 * Eneste HTTP-fallback er for `digital_bruker_cache`-entries som er utdaterte eller mangler.
 */
class TiltakskoordinatorResponseBuilder(
    private val viewRepository: TiltakskoordinatorViewRepository,
    private val digitalBrukerService: DigitalBrukerService,
) {
    /**
     * Henter gjennomføring og deltakere for en gjennomføring-id.
     *
     * En person kan ha flere deltakelser på samme gjennomføring (f.eks. en gammel avsluttet
     * + en ny aktiv). Vi returnerer kun den nyeste — eldre deltakelser er uinteressante for frontend.
     */
    suspend fun buildResponse(gjennomforingId: UUID): TiltakskoordinatorDeltakereResponse {
        val gjennomforingRow = viewRepository.getGjennomforing(gjennomforingId)
            ?: return TiltakskoordinatorDeltakereResponse(gjennomforing = null, deltakere = emptyList())

        val rows = viewRepository.getDeltakere(gjennomforingId)

        val gjennomforingResponse = buildGjennomforingResponse(gjennomforingRow)

        // Behold kun den nyeste deltakelsen per person (eldre er "låst" og uinteressant)
        val nyesteDeltakelsePerPerson = velgNyesteDeltakelsePerPerson(rows)

        // Hent digital-status for deltakere uten fersk cache-entry
        val erDigitalFallback = hentManglendeDigitalStatus(nyesteDeltakelsePerPerson)

        return TiltakskoordinatorDeltakereResponse(
            gjennomforing = gjennomforingResponse,
            deltakere = nyesteDeltakelsePerPerson.map { row ->
                buildDeltakerResponse(
                    row = row,
                    prisinformasjon = gjennomforingRow.deltakerliste.prisinformasjon,
                    erDigitalFallback = erDigitalFallback,
                )
            },
        )
    }

    private fun buildGjennomforingResponse(gjennomforingRow: GjennomforingRow): GjennomforingResponse {
        val deltakerliste = gjennomforingRow.deltakerliste
        val arrangor = deltakerliste.arrangor

        return GjennomforingResponse(
            id = deltakerliste.id,
            tiltakstype = deltakerliste.tiltakstype,
            navn = deltakerliste.navn,
            status = deltakerliste.status,
            startDato = deltakerliste.startDato,
            sluttDato = deltakerliste.sluttDato,
            antallPlasser = deltakerliste.antallPlasser,
            oppstart = deltakerliste.oppstart,
            apentForPamelding = deltakerliste.apentForPamelding,
            oppmoteSted = deltakerliste.oppmoteSted,
            arrangor = arrangor?.let {
                val navn = if (deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass) {
                    it.navn
                } else {
                    gjennomforingRow.overordnetArrangorNavn ?: it.navn
                }
                ArrangorResponse(
                    navn = navn.toTitleCase(),
                    organisasjonsnummer = it.organisasjonsnummer,
                )
            },
            pameldingstype = deltakerliste.pameldingstype,
            type = deltakerliste.gjennomforingstype,
            kodeverkValg = emptySet(),
            sertifiseringValg = emptySet(),
        )
    }

    private fun buildDeltakerResponse(
        row: TiltakskoordinatorDeltakerRow,
        prisinformasjon: String?,
        erDigitalFallback: Map<String, Boolean>,
    ): TiltakskoordinatorDeltakerResponse {
        val navVeileder = row.navVeilederId?.let {
            NavVeilederResponse(
                navn = row.navVeilederNavn,
                epost = row.navVeilederEpost,
                telefonnummer = row.navVeilederTelefon,
            )
        }

        val erDigital = row.erDigitalCached ?: erDigitalFallback[row.personident] ?: false

        return TiltakskoordinatorDeltakerResponse(
            id = row.id,
            status = row.status,
            navBruker = TiltakskoordinatorNavBrukerResponse(
                personident = row.personident,
                fornavn = row.fornavn,
                mellomnavn = row.mellomnavn,
                etternavn = row.etternavn,
                erSkjermet = row.erSkjermet,
                adresse = row.adresse,
                adressebeskyttelse = row.adressebeskyttelse,
                navVeileder = navVeileder,
                navEnhet = row.navEnhetNavn,
                erDigital = erDigital,
            ),
            startdato = row.startdato,
            sluttdato = row.sluttdato,
            soktInnDato = row.soktInnDato,
            erManueltDeltMedArrangor = row.erManueltDeltMedArrangor,
            // Vi returnerer kun nyeste deltakelse per person — den kan alltid endres
            erLaastForEndringer = false,
            harAktivtForslag = row.harAktivtForslag,
            sisteVurderingstype = row.sisteVurderingstype,
            sistEndret = row.sistEndret,
            kilde = row.kilde,
            opprettet = row.opprettet,
            prisinformasjon = prisinformasjon,
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
                    paameldtTidspunkt(it.vedtakFattet, it.innsoektDatoArena)
                }.thenByDescending { it.status.gyldigFra },
            )

            sortert.firstOrNull { it.status.type in AKTIVE_STATUSER } ?: sortert.first()
        }

    private fun paameldtTidspunkt(
        vedtakFattet: LocalDateTime?,
        innsoektDatoFraArena: LocalDate?,
    ): LocalDateTime? = listOfNotNull(
        vedtakFattet,
        innsoektDatoFraArena?.atStartOfDay(),
    ).maxOrNull()

    /**
     * Henter digital-status via HTTP for deltakere uten fersk cache-entry i `digital_bruker_cache`.
     */
    private suspend fun hentManglendeDigitalStatus(rows: List<TiltakskoordinatorDeltakerRow>): Map<String, Boolean> {
        val manglendePersonidenter = rows
            .filter { it.erDigitalCached == null }
            .map { it.personident }
            .toSet()

        if (manglendePersonidenter.isEmpty()) return emptyMap()

        return digitalBrukerService.hentErDigitalForPersonidenter(manglendePersonidenter)
    }
}
