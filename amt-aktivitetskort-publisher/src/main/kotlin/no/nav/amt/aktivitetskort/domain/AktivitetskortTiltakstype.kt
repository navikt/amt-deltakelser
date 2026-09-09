package no.nav.amt.aktivitetskort.domain

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

/*
    Fra starten av migreringen så opprettet vi aktivitetskort med arenakode isteden
    for nye tiltakskoder.
    AktivitetskortTiltakstype brukes for tekstmappinger i aktivitetsplanen og lagres
    i databasen.
 */
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

/*
    Tiltakstyper som har en arenakode, blir opprettet og lagres hos dab med arenakode
    men noen tiltakstyper finnes ikke i arena og/eller skal differensieres i visning.
 */
fun Tiltakskode.toAktivitetskortTiltakstype(): AktivitetskortTiltakstype = try {
    AktivitetskortTiltakstype.valueOf(this.toArenaKode().name)
} catch (_: IllegalArgumentException) {
    AktivitetskortTiltakstype.valueOf(this.name)
}
