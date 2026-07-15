package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TilgjengeligInnholdResponse(
    val ledetekst: String?,
    val innhold: List<InnholdselementResponse>,
) {
    data class InnholdselementResponse(
        val tekst: String,
        val innholdskode: String,
    ) {
        companion object {
            fun fromModel(model: Innholdselement) = InnholdselementResponse(
                tekst = model.tekst,
                innholdskode = model.innholdskode,
            )
        }
    }

    companion object {
        // Her bør man ikke instansiere objektet hvis det verken er ledetekst eller innholdselementer
        fun fromDeltakerRegistreringInnhold(
            innhold: DeltakerRegistreringInnhold?,
            tiltakstype: Tiltakskode,
        ) = TilgjengeligInnholdResponse(
            ledetekst = innhold?.ledetekst,
            innhold = getInnholdselementer(innhold?.innholdselementer, tiltakstype).map(InnholdselementResponse::fromModel),
        )
    }
}
