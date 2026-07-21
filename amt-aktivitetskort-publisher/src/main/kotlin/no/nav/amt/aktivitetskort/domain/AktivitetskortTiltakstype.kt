package no.nav.amt.aktivitetskort.domain

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

enum class AktivitetskortTiltakstype {
    ARBFORB,
    ARBRRHDAG,
    AVKLARAG,
    DIGIOPPARB,
    INDOPPFAG,
    GRUFAGYRKE,
    GRUPPEAMO,
    JOBBK,
    VASV,
    ENKELAMO,
    ENKFAGYRKE,
    HOYEREUTD,
    ARBEIDSMARKEDSOPPLAERING,
    NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
    STUDIESPESIALISERING,
    FAG_OG_YRKESOPPLAERING,
    HOYERE_YRKESFAGLIG_UTDANNING,
    TILPASSET_JOBBSTOTTE,
    TILRETTELAGT_ARBEID_ORDINAER,
}

fun Tiltakskode.toAktivitetskortTiltakstype(): AktivitetskortTiltakstype = try {
    AktivitetskortTiltakstype.valueOf(this.toArenaKode().name)
} catch (_: IllegalArgumentException) {
    AktivitetskortTiltakstype.valueOf(this.name)
}
