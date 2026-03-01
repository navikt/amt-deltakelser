package no.nav.amt.deltaker.bff.deltakerliste.tiltakstype

import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

// Annet beskrivelse feltet brukes som fritekst beskrivelse på noen tiltakstype
fun Tiltakskode.skalKunHaAnnetBeskrivelse() = this.erOpplaeringstiltak() ||
    this == Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET

fun getInnholdselementer(innholdselementer: List<Innholdselement>?, tiltakstype: Tiltakskode): List<Innholdselement> {
    if (tiltakstype.skalKunHaAnnetBeskrivelse()) return listOf(annetInnholdselement)

    return if (innholdselementer.isNullOrEmpty()) {
        emptyList()
    } else {
        innholdselementer.plus(annetInnholdselement)
    }
}

fun Innholdselement.toInnhold(valgt: Boolean = false, beskrivelse: String? = null) = Innhold(
    tekst = tekst,
    innholdskode = innholdskode,
    valgt = valgt,
    beskrivelse = beskrivelse,
)

val annetInnholdselement = Innholdselement("Annet", "annet")
