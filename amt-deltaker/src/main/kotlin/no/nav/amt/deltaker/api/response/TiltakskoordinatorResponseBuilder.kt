package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.AKTIVE_STATUSER
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
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
 * **Konsolidert til 1 SQL-spørring** via [TiltakskoordinatorViewRepository.getForTiltakskoordinatorView].
 * Erstatter den forrige flyten med 1 hoved-spørring + 6–7 supplerende oppslag.
 *
 * Den ene spørringen henter alle felt som trengs for responsen inkludert:
 *   - nav_ansatt (veileder), nav_enhet, soktInnDato, harAktivtForslag, sisteVurderingstype,
 *     digital_bruker_cache, og felter for låse-sjekk.
 *
 * `nav_ansatt` og `nav_enhet` hentes alltid fra SQL (FK-constraints garanterer at LEFT JOIN finner rad).
 * Eneste HTTP-fallback er for `digital_bruker_cache`-entries som er utdaterte eller mangler.
 */
class TiltakskoordinatorResponseBuilder(
    private val viewRepository: TiltakskoordinatorViewRepository,
    private val digitalBrukerService: DigitalBrukerService,
) {
    /**
     * Henter alle deltakere for en gjennomføring og bygger respons i **én SQL-spørring**.
     *
     * Eventuell HTTP-fallback gjøres kun for digital-status (utdatert/manglende cache-entry).
     */
    suspend fun buildResponse(gjennomforingId: UUID): TiltakskoordinatorDeltakereResponse {
        val rows = viewRepository.getForTiltakskoordinatorView(gjennomforingId)

        if (rows.isEmpty()) {
            return TiltakskoordinatorDeltakereResponse(
                gjennomforing = null,
                deltakere = emptyList(),
            )
        }

        val gjennomforingResponse = buildGjennomforingResponse(rows.first())

        // Beregn erLaastForEndringer fra låse-data i resultatet (gruppert per personident)
        val laaseStatusPerDeltaker = beregnLaaseStatus(rows)

        // Hent digital-status for deltakere uten fersk cache-entry
        val erDigitalFallback = hentManglendeDigitalStatus(rows)

        return TiltakskoordinatorDeltakereResponse(
            gjennomforing = gjennomforingResponse,
            deltakere = rows.map { row ->
                buildDeltakerResponse(
                    row = row,
                    erLaastForEndringer = laaseStatusPerDeltaker[row.id] ?: false,
                    erDigitalFallback = erDigitalFallback,
                )
            },
        )
    }

    private fun buildGjennomforingResponse(row: TiltakskoordinatorDeltakerRow): GjennomforingResponse {
        val deltakerliste = row.deltakerliste
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
                    row.overordnetArrangorNavn ?: it.navn
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
        erLaastForEndringer: Boolean,
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
            erLaastForEndringer = erLaastForEndringer,
            harAktivtForslag = row.harAktivtForslag,
            sisteVurderingstype = row.sisteVurderingstype,
            sistEndret = row.sistEndret,
            kilde = row.kilde,
            opprettet = row.opprettet,
            prisinformasjon = row.prisinformasjon,
        )
    }

    /**
     * Beregner låsestatus for alle deltakere basert på data fra den konsoliderte spørringen.
     * Speiler logikken i `DeltakerLaaseService`, men bruker data som allerede er hentet.
     */
    private fun beregnLaaseStatus(rows: List<TiltakskoordinatorDeltakerRow>): Map<UUID, Boolean> {
        val perPerson = rows.groupBy { it.personident }

        return rows.associate { row ->
            val deltakelserForPerson = perPerson[row.personident].orEmpty()

            // Eneste deltakelse for denne personen → ikke låst
            if (deltakelserForPerson.size <= 1) {
                return@associate row.id to false
            }

            val sortert = deltakelserForPerson.sortedWith(
                compareByDescending<TiltakskoordinatorDeltakerRow> {
                    paameldtTidspunkt(it.vedtakFattet, it.innsoektDatoArena)
                }.thenByDescending { it.status.gyldigFra },
            )

            val nyesteDeltakelse = sortert
                .firstOrNull { it.status.type in AKTIVE_STATUSER }
                ?: sortert.first()

            row.id to (row.id != nyesteDeltakelse.id)
        }
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
