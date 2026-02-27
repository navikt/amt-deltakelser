package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_ANNET_INNHOLD_LENGDE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_BAKGRUNNSINFORMASJON_LENGDE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_DAGER_PER_UKE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MAX_DELTAKELSESPROSENT
import no.nav.amt.deltaker.bff.deltaker.api.utils.MIN_DAGER_PER_UKE
import no.nav.amt.deltaker.bff.deltaker.api.utils.MIN_DELTAKELSESPROSENT
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerKladdInnhold
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement

data class KladdRequest(
    val innhold: List<InnholdRequest>,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
) {
    /**
     * Kladd må støtte autolagring, dvs. at vi kan ikke feile selv om påmeldingsskjemaet er uferdig eller inneholder
     * feil. Så vi tillater mer innhold i fritekstfelt enn normalt og avkorter innholdet etter et hvis punkt,
     * og setteer deltakelsemengde til nærmeste gyldige verdi.
     *
     * Disse reglene gjelder kun for kladd.
     */
    fun sanitize() = KladdRequest(
        innhold = innhold.sanitize(),
        bakgrunnsinformasjon = bakgrunnsinformasjon?.sanitize(),
        deltakelsesprosent = deltakelsesprosent?.clamp(MIN_DELTAKELSESPROSENT, MAX_DELTAKELSESPROSENT),
        dagerPerUke = dagerPerUke?.clamp(MIN_DAGER_PER_UKE, MAX_DAGER_PER_UKE),
    )

    fun valider(deltaker: Deltaker) =
        validerKladdInnhold(this.innhold, deltaker.deltakerliste.tiltak.innhold, deltaker.deltakerliste.tiltak.tiltakskode)
}

private fun String.sanitize(): String {
    val gyldigLengde = 0..<MAX_BAKGRUNNSINFORMASJON_LENGDE * 2

    return if (this.length > gyldigLengde.max()) {
        this.slice(gyldigLengde)
    } else {
        this
    }
}

private fun List<InnholdRequest>.sanitize() = this.map {
    val gyldigLengde = 0..<MAX_ANNET_INNHOLD_LENGDE * 2
    if (
        it.innholdskode == annetInnholdselement.innholdskode &&
        it.beskrivelse != null &&
        it.beskrivelse.length > gyldigLengde.max()
    ) {
        it.copy(beskrivelse = it.beskrivelse.slice(gyldigLengde))
    } else {
        it
    }
}

private fun Int.clamp(min: Int, max: Int) = when {
    this < min -> min
    this > max -> max
    else -> this
}
