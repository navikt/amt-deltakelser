package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.deltaker.bff.veileder.api.response.TilgjengeligInnholdResponse
import no.nav.amt.lib.ktor.clients.kodeverk.OpplaringKategoriseringResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteResponse(
    val deltakerlisteId: UUID,
    val deltakerlisteNavn: String,
    val tiltakskode: Tiltakskode,
    val arrangorNavn: String, // skal fjernes
    val arrangor: ArrangorResponse?,
    val oppstartstype: Oppstartstype?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val status: GjennomforingStatusType?,
    val tilgjengeligInnhold: TilgjengeligInnholdResponse?,
    val erEnkeltplass: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
    val kodeverk: UtflatetKodeverk? = null,
    val prisinformasjon: Prisinformasjon? = null,
) {
    data class ArrangorResponse(
        val navn: String,
        val organisasjonsnummer: String,
    )

    data class UtflatetKodeverk(
        val valgteKategoriseringer: Set<ValgteFelt>,
        val valgteSertifiseringer: Set<SertifiseringValg>,
    ) {
        data class ValgteFelt(
            val representerer: OpplaringKategoriseringResponse.Representerer,
            val valg: Map<UUID, String>,
        )
    }

    companion object {
        fun fromModel(
            gjennomforingModel: GjennomforingModel,
            kodeverk: UtflatetKodeverk? = null,
        ) = with(gjennomforingModel) {
            DeltakerlisteResponse(
                deltakerlisteId = id,
                deltakerlisteNavn = navn,
                tiltakskode = tiltak.tiltakskode,
                arrangorNavn = arrangor?.navn ?: "Ukjent arrangør", // skal fjernes
                arrangor = arrangor?.let {
                    ArrangorResponse(
                        navn = it.navn,
                        organisasjonsnummer = it.organisasjonsnummer,
                    )
                },
                oppstartstype = oppstart,
                startdato = startDato,
                sluttdato = sluttDato,
                status = status,
                tilgjengeligInnhold = TilgjengeligInnholdResponse.fromDeltakerRegistreringInnhold(
                    innhold = tiltak.innhold,
                    tiltakstype = tiltak.tiltakskode,
                ),
                erEnkeltplass = erEnkeltplass,
                oppmoteSted = oppmoteSted,
                pameldingstype = pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
                kodeverk = kodeverk,
                prisinformasjon = prisinformasjon,
            )
        }
    }
}
