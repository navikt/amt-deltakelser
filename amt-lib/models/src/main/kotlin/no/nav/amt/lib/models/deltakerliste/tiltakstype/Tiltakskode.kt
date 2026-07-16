package no.nav.amt.lib.models.deltakerliste.tiltakstype

enum class Tiltakskode {
    /*
        Individuelle tiltak som alltid har løpende oppstart og direktegodkjent
     */
    ARBEIDSFORBEREDENDE_TRENING,
    ARBEIDSRETTET_REHABILITERING,
    AVKLARING,
    OPPFOLGING,
    VARIG_TILRETTELAGT_ARBEID_SKJERMET,
    DIGITALT_OPPFOLGINGSTILTAK, // Digitalt jobbsøkerkurs

    /*
        Kurstiltak som ofte har oppstartstype felles men kan også i tilfeller ha oppstartstype løpende.
        Bruk av denne tiltakstypen med oppstartstype løpende kan tyde på at det er arbeidsmarkedsopplæring med rammeavtale.
        Oppstartstype FELLES skal gi KREVER_GODKJENNING
        Oppstartstype LØPENDE skal gi DIREKTE_GODKJENT
     */
    GRUPPE_ARBEIDSMARKEDSOPPLAERING,
    GRUPPE_FAG_OG_YRKESOPPLAERING,
    JOBBKLUBB, // Jobbsøkerkurs

    /*
        Enkeltplasstiltak som skal fases ut. Kan kun kan registreres i arena.
        Disse har 1-1 med gjennomføring.
     */
    ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
    ENKELTPLASS_FAG_OG_YRKESOPPLAERING,

    /*
        Enkeltplasstiltak som potensielt skal videreføres fra arena
     */
    HOYERE_UTDANNING,

    /*
        Tiltak etter ny forskrift som skal erstatte bruk av
        ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING, ENKELTPLASS_FAG_OG_YRKESOPPLAERING, GRUPPE_ARBEIDSMARKEDSOPPLAERING, GRUPPE_FAG_OG_YRKESOPPLAERING
        Kan ha oppstartstype Felles/Løpende
        De med Løpende oppstart kan ha enten DIREKTE_VEDTAK eller KREVER_GODKJENNING
        De med Felles oppstart har alltid KREVER_GODKJENNING
     */
    ARBEIDSMARKEDSOPPLAERING, // Tidligere GruppeAMO/EnkelAMO
    NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV, // Tidligere GruppeAMO/EnkelAMO
    STUDIESPESIALISERING, // Tidligere GruppeAMO/EnkelAMO
    FAG_OG_YRKESOPPLAERING, // Tidligere GruppeFagYrk/EnkelFagYrk
    HOYERE_YRKESFAGLIG_UTDANNING, // Tidligere GruppeFagYrk/EnkelFagYrk

    /*
        TILRETTELAGT_ARBEID_ORDINAER)
        tidligere Varig tilrettelagt arbeid i ordinær virksomhet (VTA-O som eies av Team Tiltak).
        Tilpasset jobbstotte er et nytt tiltak etter ny forskrift som eies av Team Komet
        og fungerer som et tillegg til VARIG_TILRETTELAGT_ARBEID_SKJERMET.
     */
    TILRETTELAGT_ARBEID_ORDINAER,
    ;

    // Ved lansering av ny forskrift/påmelding av nye typer må vi bruke type feltet GRUPPE/ENKELPLASS istedet for tiltakskode
    fun erArenaEnkeltplass() = this in setOf(HOYERE_UTDANNING, ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING, ENKELTPLASS_FAG_OG_YRKESOPPLAERING)

    val visningsnavn: String
        get() = when (this) {
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
        }

    fun erOpplaeringstiltak() = this in
        setOf(
            ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            HOYERE_UTDANNING,
            GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            GRUPPE_FAG_OG_YRKESOPPLAERING,
            ARBEIDSMARKEDSOPPLAERING,
            NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            STUDIESPESIALISERING,
            FAG_OG_YRKESOPPLAERING,
            HOYERE_YRKESFAGLIG_UTDANNING,
        )

    @Deprecated("Denne skal antakelig erstattes av lokalt tilpassede versjoner")
    fun toArenaKode() = when (this) {
        ARBEIDSFORBEREDENDE_TRENING -> ArenaKode.ARBFORB
        ARBEIDSRETTET_REHABILITERING -> ArenaKode.ARBRRHDAG
        AVKLARING -> ArenaKode.AVKLARAG
        DIGITALT_OPPFOLGINGSTILTAK -> ArenaKode.DIGIOPPARB
        GRUPPE_ARBEIDSMARKEDSOPPLAERING -> ArenaKode.GRUPPEAMO
        GRUPPE_FAG_OG_YRKESOPPLAERING -> ArenaKode.GRUFAGYRKE
        JOBBKLUBB -> ArenaKode.JOBBK
        OPPFOLGING -> ArenaKode.INDOPPFAG
        VARIG_TILRETTELAGT_ARBEID_SKJERMET -> ArenaKode.VASV
        ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING -> ArenaKode.ENKELAMO
        ENKELTPLASS_FAG_OG_YRKESOPPLAERING -> ArenaKode.ENKFAGYRKE
        HOYERE_UTDANNING -> ArenaKode.HOYEREUTD
        else -> throw IllegalArgumentException("Ukjent tiltakskode: $this")
    }
}

data class TiltakskodeResponse(
    val kode: Tiltakskode,
    val visningsnavn: String = kode.visningsnavn,
)
