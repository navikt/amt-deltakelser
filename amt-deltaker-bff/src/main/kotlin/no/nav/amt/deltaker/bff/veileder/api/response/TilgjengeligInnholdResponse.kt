package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

data class TilgjengeligInnholdResponse(
    val ledetekst: String?,
    val innhold: List<InnholdselementResponse>,
) {
    // Her bør man ikke instansiere objektet hvis det verken er ledetekst eller innholdselementer
    constructor(model: DeltakerRegistreringInnhold?, tiltakskode: Tiltakskode) : this(
        ledetekst = model?.ledetekst,
        innhold = getInnholdselementer(model?.innholdselementer, tiltakskode).map(::InnholdselementResponse),
    )

    data class InnholdselementResponse(
        val tekst: String,
        val innholdskode: String,
    ) {
        constructor(model: Innholdselement) : this(
            tekst = model.tekst,
            innholdskode = model.innholdskode,
        )
    }
}
