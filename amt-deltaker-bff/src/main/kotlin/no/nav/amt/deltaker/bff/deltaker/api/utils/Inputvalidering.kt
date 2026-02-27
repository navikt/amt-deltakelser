package no.nav.amt.deltaker.bff.deltaker.api.utils

import no.nav.amt.deltaker.bff.deltaker.api.model.InnholdRequest
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.annetInnholdselement
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.getInnholdselementer
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.skalKunHaAnnetBeskrivelse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val MAX_BAKGRUNNSINFORMASJON_LENGDE = 1000
const val MAX_ANNET_INNHOLD_LENGDE = 250
const val MAX_AARSAK_BESKRIVELSE_LENGDE = 40
const val MIN_DAGER_PER_UKE = 1
const val MAX_DAGER_PER_UKE = 5
const val MIN_DELTAKELSESPROSENT = 1
const val MAX_DELTAKELSESPROSENT = 100
const val MAX_BEGRUNNELSE_LENGDE = 200

fun validerBakgrunnsinformasjon(tekst: String?) = tekst?.let {
    require(it.length <= MAX_BAKGRUNNSINFORMASJON_LENGDE) {
        "Bakgrunnsinformasjon kan ikke være lengre enn $MAX_BAKGRUNNSINFORMASJON_LENGDE"
    }
}

fun validerAnnetInnhold(tekst: String?, tiltakstype: Tiltakskode) {
    if (!tiltakstype.skalKunHaAnnetBeskrivelse()) {
        require(tekst != null && tekst != "") {
            "Innhold med innholdskode: ${annetInnholdselement.innholdskode} må ha en beskrivelse"
        }
    }
    tekst?.let {
        require(it.length <= MAX_ANNET_INNHOLD_LENGDE) {
            "Annet Beskrivelse kan ikke være lengre enn $MAX_ANNET_INNHOLD_LENGDE"
        }
    }
}

fun validerAarsaksBeskrivelse(tekst: String?) = tekst?.let {
    require(tekst.length <= MAX_AARSAK_BESKRIVELSE_LENGDE) {
        "Beskrivelse kan ikke være lengre enn $MAX_AARSAK_BESKRIVELSE_LENGDE"
    }
}

fun validerDagerPerUke(n: Int?) = n?.let {
    require(n in MIN_DAGER_PER_UKE..MAX_DAGER_PER_UKE) {
        "Dager per uke kan ikke være mindre enn $MIN_DAGER_PER_UKE eller større enn $MAX_DAGER_PER_UKE"
    }
}

fun validerDeltakelsesProsent(n: Int?) = n?.let {
    require(n in MIN_DELTAKELSESPROSENT..MAX_DELTAKELSESPROSENT) {
        "Deltakelsesprosent kan ikke være mindre enn $MIN_DELTAKELSESPROSENT eller større enn $MAX_DELTAKELSESPROSENT"
    }
}

fun validerDeltakelsesmengde(
    nyProsent: Int?,
    nyDagerPerUke: Int?,
    gyldigFra: LocalDate,
    deltaker: Deltaker,
) {
    require(
        deltaker.deltakelsesmengder.validerNyDeltakelsesmengde(
            Deltakelsesmengde(
                deltakelsesprosent = nyProsent?.toFloat() ?: 100F,
                dagerPerUke = nyDagerPerUke?.toFloat(),
                gyldigFra = gyldigFra,
                opprettet = LocalDateTime.now(),
            ),
        ),
    ) {
        "Deltakelsesmengdeendringen er ikke en reel endring"
    }
}

fun validerDeltakerKanReaktiveres(opprinneligDeltaker: Deltaker) {
    require(opprinneligDeltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL) {
        "Kan ikke reaktivere deltaker som har annen status enn ikke aktuell"
    }
    validerDeltakerKanEndres(opprinneligDeltaker)
}

fun validerDeltakerKanEndres(opprinneligDeltaker: Deltaker) {
    require(opprinneligDeltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
        "Kan ikke endre feilregistrert deltaker"
    }
    if (opprinneligDeltaker.harSluttet()) {
        require(opprinneligDeltaker.kanEndres) {
            "Kan ikke endre avsluttet deltakelse når det finnes aktiv deltakelse på samme tiltak"
        }
        require(opprinneligDeltaker.harSluttetForMindreEnnToMndSiden()) {
            "Kan ikke endre deltaker som fikk avsluttende status for mer enn to måneder siden"
        }
    }
}

fun statusForMindreEnn15DagerSiden(opprinneligDeltaker: Deltaker): Boolean = opprinneligDeltaker.status.gyldigFra
    .toLocalDate()
    .isAfter(LocalDate.now().minusDays(15))

fun validerForslagEllerBegrunnelse(forslagId: UUID?, begrunnelse: String?) {
    require(forslagId != null || !begrunnelse.isNullOrEmpty()) {
        "Må ha begrunnelse hvis ikke det er et godkjent forslag"
    }
}

fun validerBegrunnelse(begrunnelse: String?) {
    require(begrunnelse === null || begrunnelse.length <= MAX_BEGRUNNELSE_LENGDE) {
        "Begrunnelse kan ikke være lengre enn $MAX_BEGRUNNELSE_LENGDE"
    }
}

fun validerSluttdatoForDeltaker(
    sluttdato: LocalDate,
    startdato: LocalDate?,
    opprinneligDeltaker: Deltaker,
) {
    require(opprinneligDeltaker.deltakerliste.sluttDato == null || !sluttdato.isAfter(opprinneligDeltaker.deltakerliste.sluttDato)) {
        "Sluttdato kan ikke være senere enn deltakerlistens sluttdato"
    }
    require(startdato == null || !sluttdato.isBefore(startdato)) {
        "Sluttdato må være etter startdato"
    }

    startdato?.let { validerVarighet(it, sluttdato, opprinneligDeltaker) }
}

fun validerDeltakelsesinnhold(
    valgteInnholdselementer: List<InnholdRequest>,
    tiltaksinnhold: DeltakerRegistreringInnhold?,
    tiltakstype: Tiltakskode,
) {
    validerInnhold(tiltakstype, valgteInnholdselementer, tiltaksinnhold) { innholdskoder ->
        if (!tiltakstype.skalKunHaAnnetBeskrivelse()) {
            require(valgteInnholdselementer.isNotEmpty()) { "For et tiltak med innholdselementer må det velges minst ett" }
        }
        valgteInnholdselementer.forEach {
            require(it.innholdskode in innholdskoder) { "Ugyldig innholdskode: ${it.innholdskode}" }

            if (it.innholdskode == annetInnholdselement.innholdskode) {
                validerAnnetInnhold(it.beskrivelse, tiltakstype)
            } else {
                require(it.beskrivelse == null) {
                    "Innhold med innholdskode: ${it.innholdskode} kan ikke ha en beskrivelse"
                }
            }
        }
    }
}

fun harEndretSluttaarsak(
    opprinneligDeltakerStatusAarsak: DeltakerStatus.Aarsak?,
    nyDeltakerStatusAarsak: DeltakerEndring.Aarsak?,
): Boolean = nyDeltakerStatusAarsak?.toDeltakerStatusAarsak() != opprinneligDeltakerStatusAarsak

private fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    DeltakerStatus.Aarsak.Type.valueOf(type.name),
    beskrivelse,
)

fun validerKladdInnhold(
    innhold: List<InnholdRequest>,
    tiltaksinnhold: DeltakerRegistreringInnhold?,
    tiltakstype: Tiltakskode,
) {
    validerInnhold(tiltakstype, innhold, tiltaksinnhold) { innholdskoder ->
        innhold.forEach {
            require(it.innholdskode in innholdskoder) { "Ugyldig innholdskode: ${it.innholdskode}" }

            if (it.innholdskode != annetInnholdselement.innholdskode) {
                require(it.beskrivelse == null) {
                    "Innhold med innholdskode: ${it.innholdskode} kan ikke ha en beskrivelse"
                }
            }
        }
    }
}

private fun validerVarighet(
    startdato: LocalDate,
    sluttdato: LocalDate,
    deltaker: Deltaker,
) {
    val maxVarighet = deltaker.maxVarighet ?: return

    val senesteSluttdato = startdato.plusDays(maxVarighet.toDays())

    if (deltaker.sluttdato != null && senesteSluttdato.isBefore(deltaker.sluttdato)) {
        require(!sluttdato.isAfter(deltaker.sluttdato))
    } else {
        require(!sluttdato.isAfter(senesteSluttdato)) {
            "Sluttdato $sluttdato er etter seneste mulige sluttdato $senesteSluttdato"
        }
    }
}

private fun validerInnhold(
    tiltakstype: Tiltakskode,
    valgteInnholdselementer: List<InnholdRequest>,
    tiltaksinnhold: DeltakerRegistreringInnhold?,
    valider: (innholdskoder: List<String>) -> Unit,
) {
    val muligeInnholdskoderForTiltak = getInnholdselementer(tiltaksinnhold?.innholdselementer, tiltakstype)
        .map { it.innholdskode }

    if (muligeInnholdskoderForTiltak.isEmpty()) {
        require(valgteInnholdselementer.isEmpty()) { "Et tiltak uten innholdselementer kan ikke ha noe innhold" }
    } else {
        valider(muligeInnholdskoderForTiltak)
    }
}
