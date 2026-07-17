package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

fun visningsnavn(tiltakskode: Tiltakskode) = when (tiltakskode) {
    Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> "Arbeidsforberedende trening"
    Tiltakskode.ARBEIDSRETTET_REHABILITERING -> "Arbeidsrettet rehabilitering"
    Tiltakskode.AVKLARING -> "Avklaring"
    Tiltakskode.OPPFOLGING -> "Oppfølging"
    Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Varig tilrettelagt arbeid"
    Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> "Digitalt jobbsøkerkurs"
    Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
    Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
    Tiltakskode.JOBBKLUBB -> "Jobbsøkerkurs"
    Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring (enkeltplass)"
    Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring (enkeltplass)"
    Tiltakskode.HOYERE_UTDANNING -> "Høyere utdanning"
    Tiltakskode.ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
    Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> "Norskopplæring, grunnleggende ferdigheter og FOV"
    Tiltakskode.STUDIESPESIALISERING -> "Studiespesialisering"
    Tiltakskode.FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
    Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING -> "Høyere yrkesfaglig utdanning"
    Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER -> "Tilrettelagt arbeid i ordinær virksomhet"
}
