package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

// Burde brukes av både veileder og innbygger
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
    val erEnkeltplassUtenRammeavtale: Boolean,
    val erEnkeltplass: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
    val kodeverk: KodeverkResponse? = null,
) {
    data class ArrangorResponse(
        val navn: String,
        val organisasjonsnummer: String,
    )

    companion object {
        fun fromModel(
            gjennomforingModel: GjennomforingModel,
            kodeverk: KodeverkResponse? = null,
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
                    tiltak.innhold,
                    tiltak.tiltakskode,
                ),
                erEnkeltplassUtenRammeavtale = erEnkeltplass, // TODO: Denne skal fjernes når frontend er klar
                erEnkeltplass = erEnkeltplass,
                oppmoteSted = oppmoteSted,
                pameldingstype = pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
                kodeverk = kodeverk,
            )
        }
    }
}
