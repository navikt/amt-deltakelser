package no.nav.amt.deltaker.bff.commonresponse

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ARBEIDSMARKEDSOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ARBEIDSRETTET_REHABILITERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.AVKLARING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.FAG_OG_YRKESOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.HOYERE_UTDANNING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.JOBBKLUBB
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.OPPFOLGING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.STUDIESPESIALISERING
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET

data class TiltakskodeResponse(
    val kode: Tiltakskode,
    val visningsnavn: String = when (kode) {
        ARBEIDSFORBEREDENDE_TRENING -> "Arbeidsforberedende trening"
        ARBEIDSRETTET_REHABILITERING -> "Arbeidsrettet rehabilitering"
        AVKLARING -> "Avklaring"
        OPPFOLGING -> "Oppfølging"
        VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Varig tilrettelagt arbeid"
        DIGITALT_OPPFOLGINGSTILTAK -> "Digitalt jobbsøkerkurs"
        GRUPPE_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
        GRUPPE_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
        JOBBKLUBB -> "Jobbsøkerkurs"
        ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring (enkeltplass)"
        ENKELTPLASS_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring (enkeltplass)"
        HOYERE_UTDANNING -> "Høyere utdanning"
        ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
        NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> "Norskopplæring, grunnleggende ferdigheter og FOV"
        STUDIESPESIALISERING -> "Studiespesialisering"
        FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
        HOYERE_YRKESFAGLIG_UTDANNING -> "Høyere yrkesfaglig utdanning"
        TILRETTELAGT_ARBEID_ORDINAER -> "Tilrettelagt arbeid i ordinær virksomhet"
    },
)
