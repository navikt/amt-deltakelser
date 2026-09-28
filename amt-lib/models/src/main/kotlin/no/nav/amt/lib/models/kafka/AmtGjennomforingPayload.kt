package no.nav.amt.lib.models.kafka

import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

/**
 * Payload for topicet `amt.gjennomforing-intern`.
 *
 * amt-deltaker konsumerer `team-mulighetsrommet.siste-tiltaksgjennomforinger-v2` og
 * `team-mulighetsrommet.siste-tiltakstyper-v3`, slår sammen gjennomføring + tiltakstype, og
 * produserer denne payloaden. Målet er at konsumentene (amt-deltaker-bff,
 * amt-tiltaksarrangor-bff, amt-aktivitetskort-publisher) på sikt kan lese ett internt Nav-topic
 * i stedet for mulighetsrommet-topicene direkte.
 *
 * Feltutvalget er unionen av det de tre konsumentene faktisk leser/persisterer i dag.
 * Gruppe-spesifikke felt er nullable fordi de ikke finnes på en Enkeltplass-gjennomføring;
 * bruk [type] for å skille de to variantene.
 */
data class AmtGjennomforingPayload(
    // Felles felt – finnes på både Gruppe og Enkeltplass, lest av alle konsumenter
    val id: UUID,
    val type: GjennomforingType,
    val tiltak: TiltakPayload,
    val arrangor: Arrangor,
    val status: GjennomforingStatusType,
    val oppstart: Oppstartstype,
    val pameldingstype: GjennomforingPameldingType,
    // Gruppe-spesifikke felt – null for Enkeltplass (som bruker tiltak.navn som visningsnavn)
    val navn: String?,
    val lopenummer: String?,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val tilgjengeligForArrangorFraOgMedDato: LocalDate?,
    val apentForPamelding: Boolean?,
    val antallPlasser: Int?,
    val oppmoteSted: String?,
) {
    /**
     * Kun organisasjonsnummer, i tråd med kildepayloaden. Hver konsument slår selv opp sin egen
     * arrangør-id via organisasjonsnummeret (fra amt.arrangor-v1), så vi unngår å binde det nye
     * topicet til arrangør-oppslag.
     */
    data class Arrangor(
        val organisasjonsnummer: String,
    )

    /**
     * Sammenslått tiltakstype fra `siste-tiltakstyper-v3`, embeddet slik at konsumentene slipper
     * å lese tiltakstype-topicet separat.
     */
    data class TiltakPayload(
        val id: UUID,
        val navn: String,
        val tiltakskode: Tiltakskode,
        val innhold: DeltakerRegistreringInnhold?,
    )
}
