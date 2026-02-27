package no.nav.amt.lib.models.deltakerliste.tiltakstype

data class DeltakerRegistreringInnhold(
    val innholdselementer: List<Innholdselement>,
    val ledetekst: String,
)
