package no.nav.amt.lib.models.deltakerliste.tiltakstype

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.NullNode

/**
 * Denne filen er kopiert fra no.nav.mulighetsrommet.model 01.09.2026.
 * Kommentarer og metoder i opprinnelig fil er lagt til.
 *
 * Beskrivelser
 * 1. Individuelle tiltak som alltid har løpende oppstart og direktegodkjent
 * 2. Kurstiltak som ofte har oppstartstype felles men kan også i tilfeller ha oppstartstype løpende.
 *    Bruk av denne tiltakstypen med oppstartstype løpende kan tyde på at det er arbeidsmarkedsopplæring med rammeavtale.
 *    Oppstartstype FELLES skal gi KREVER_GODKJENNING
 *    Oppstartstype LØPENDE skal gi DIREKTE_GODKJENT
 * 3. Enkeltplasstiltak som skal fases ut. Kan kun kan registreres i Arena.
 *    Disse har 1-1 med gjennomføring.
 * 4. Tiltak etter ny forskrift som skal erstatte bruk av
 *    ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING, ENKELTPLASS_FAG_OG_YRKESOPPLAERING, GRUPPE_ARBEIDSMARKEDSOPPLAERING, GRUPPE_FAG_OG_YRKESOPPLAERING
 *    Kan ha oppstartstype Felles/Løpende
 *    De med Løpende oppstart kan ha enten DIREKTE_VEDTAK eller KREVER_GODKJENNING
 *    De med Felles oppstart har alltid KREVER_GODKJENNING
 */
enum class Tiltakskode(
    val system: TiltakstypeSystem,
    val arenakode: String?,
    val egenskaper: Set<TiltakstypeEgenskap>,
    val gruppe: Tiltaksgruppe? = null,
) {
    ARBEIDSRETTET_REHABILITERING(
        // Se #1
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "ARBRRHDAG",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
        ),
    ),
    AVKLARING(
        // Se #1
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "AVKLARAG",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
        ),
    ),
    DIGITALT_OPPFOLGINGSTILTAK(
        // Se #1
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "DIGIOPPARB",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
        ),
    ),
    JOBBKLUBB(
        // Aka Jobbsøkerkurs, se #2
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "JOBBK",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
        ),
    ),
    OPPFOLGING(
        // Se #1
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "INDOPPFAG",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
        ),
    ),

    /**
     * Forhåndsgodkjente tiltak
     */
    ARBEIDSFORBEREDENDE_TRENING(
        // Se #1
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "ARBFORB",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
            TiltakstypeEgenskap.STOTTER_TILSKUDD_FOR_INVESTERINGER,
        ),
    ),
    VARIG_TILRETTELAGT_ARBEID_SKJERMET(
        // Se #1
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "VASV",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
            TiltakstypeEgenskap.STOTTER_TILSKUDD_FOR_INVESTERINGER,
        ),
    ),

    /**
     TILRETTELAGT_ARBEID_ORDINAER:
     Tidligere Varig tilrettelagt arbeid i ordinær virksomhet (VTA-O som eies av Team Tiltak).
     Tilpasset jobbstotte er et nytt tiltak etter ny forskrift som eies av Team Komet
     og fungerer som et tillegg til VARIG_TILRETTELAGT_ARBEID_SKJERMET.
     */
    TILRETTELAGT_ARBEID_ORDINAER(
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = null,
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
        ),
    ),

    /**
     * Opplæringstiltak
     */
    ARBEIDSMARKEDSOPPLAERING(
        // Tidligere GruppeAMO/EnkelAMO, se #4
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUPPEAMO",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING(
        // Se #3
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "ENKELAMO",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    ENKELTPLASS_FAG_OG_YRKESOPPLAERING(
        // Se #3
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "ENKFAGYRKE",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    FAG_OG_YRKESOPPLAERING(
        // Tidligere GruppeFagYrk/EnkelFagYrk, se #4
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUFAGYRKE",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    GRUPPE_ARBEIDSMARKEDSOPPLAERING(
        // Se #2
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUPPEAMO",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK_FOR_LOPENDE_OPPSTART,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    GRUPPE_FAG_OG_YRKESOPPLAERING(
        // Se #2
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUFAGYRKE",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.KREVER_DIREKTE_VEDTAK_FOR_LOPENDE_OPPSTART,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),

    /**
     HOYERE_UTDANNING: Enkeltplasstiltak som potensielt skal videreføres fra Arena
     */
    HOYERE_UTDANNING(
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "HOYEREUTD",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    HOYERE_YRKESFAGLIG_UTDANNING(
        // Tidligere GruppeFagYrk/EnkelFagYrk, se #4
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUFAGYRKE",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV(
        // Tidligere GruppeAMO/EnkelAMO, se #4
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUPPEAMO",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    STUDIESPESIALISERING(
        // Tidligere GruppeAMO/EnkelAMO, se #4
        system = TiltakstypeSystem.TILTAKSADMINISTRASJON,
        arenakode = "GRUPPEAMO",
        egenskaper = setOf(
            TiltakstypeEgenskap.STOTTER_AVTALER,
            TiltakstypeEgenskap.STOTTER_ENKELTPLASSER,
            TiltakstypeEgenskap.KREVER_DELTIDSPROSENT,
            TiltakstypeEgenskap.STOTTER_TILTAK_DOKUMENT,
        ),
        gruppe = Tiltaksgruppe.OPPLAERING,
    ),
    ;

    fun harEgenskap(vararg egenskap: TiltakstypeEgenskap): Boolean = egenskaper.containsAll(egenskap.toSet())

    // Ved lansering av ny forskrift/påmelding av nye typer må vi bruke type feltet GRUPPE/ENKELPLASS istedet for tiltakskode
    fun erArenaEnkeltplass() = this in setOf(
        HOYERE_UTDANNING,
        ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
        ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
    )

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

enum class TiltakstypeEgenskap {
    /**
     * Gjør at tiltaket har systemstøtte for avtaler (inkludert gjennomføringer for avtaler).
     * Dette inkluderer bl.a.
     *   - Vises i filter for avtaler og gjennomføringer
     *   - Avtaler, samt gjennomføringer for avtaler, kan opprettes for tiltaket
     */
    STOTTER_AVTALER,

    /**
     * Gjør at tiltaket har systemstøtte for enkeltplass-gjennomføringer.
     * Dette inkluderer bl.a.
     *   - Vises i filter for gjennomføringer
     *   - Enkeltplasser kan opprettes
     */
    STOTTER_ENKELTPLASSER,

    /**
     * Gjør deltidsprosent påkrevd i gjennomføringer
     */
    KREVER_DELTIDSPROSENT,

    /**
     * Krever at innsøksform for deltakere er "direkte vedtak", altså at det er Nav-veileder som gjør vedtak om tiltaksplass.
     * Hvis [KREVER_DIREKTE_VEDTAK] ikke er satt så kan innsøksform bestemmes av administrator for tiltaket.
     */
    KREVER_DIREKTE_VEDTAK,

    /**
     * Krever at innsøksform for deltakere er "direkte vedtak" når oppstartstypen er "løpende".
     * Hvis [KREVER_DIREKTE_VEDTAK_FOR_LOPENDE_OPPSTART] ikke er satt så kan innsøksform bestemmes av administrator for tiltaket.
     */
    KREVER_DIREKTE_VEDTAK_FOR_LOPENDE_OPPSTART,

    /**
     * Indikerer at tiltakstypen støtter tilskudd for investeringer.
     */
    STOTTER_TILSKUDD_FOR_INVESTERINGER,

    /**
     * Gjør at det kan opprettes tiltak dokumenter for tiltaket
     */
    STOTTER_TILTAK_DOKUMENT,
}

enum class Tiltaksgruppe(
    val tittel: String,
) {
    OPPLAERING("Opplæringstiltak"),
}

enum class TiltakstypeSystem {
    TILTAKSADMINISTRASJON,
    ARENA,
    ARBEIDSGIVERTILTAK,
}

object Tiltakskoder {
    /**
     * Tiltakskoder for de forhåndsgodkjente og anskaffede tiltakene, kalt "gruppetilak" (av oss i hvert fall), og som
     * skal migreres fra Arena som del av P4.
     */
    private val TiltakskoderGruppe = listOf(
        Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
        Tiltakskode.ARBEIDSRETTET_REHABILITERING,
        Tiltakskode.AVKLARING,
        Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
        Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
        Tiltakskode.OPPFOLGING,
        Tiltakskode.JOBBKLUBB,
        Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
        Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
        // TODO: disse tiltakskodene er egentlig ikke bare for "gruppetiltak", men foreløpig er det OK.
        //  Vi burde komme oss vekk fra disse tiltaskode-listene og evt. erstatte med egenskaper direkte på Tiltalkskode-enumen
        Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
        Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
        Tiltakskode.STUDIESPESIALISERING,
        Tiltakskode.FAG_OG_YRKESOPPLAERING,
    )

    /**
     * Tiltakskoder for tiltak i egen regi (regi av Nav), og som foreløpig administreres i Sanity ikke i admin-flate.
     */
    private val TiltakskoderEgenRegi = listOf(
        "INDJOBSTOT",
        "IPSUNG",
        "UTVAOONAV",
    )

    private val TiltakskoderEnkeltplasser = listOf(
        Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
        Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
        Tiltakskode.HOYERE_UTDANNING,
    )

    fun isGruppetiltak(arenaKode: String): Boolean = arenaKode in TiltakskoderGruppe.map { it.arenakode }

    fun isEgenRegiTiltak(arenaKode: String): Boolean = arenaKode in TiltakskoderEgenRegi

    fun isEnkeltplassTiltak(arenakode: String): Boolean = arenakode in TiltakskoderEnkeltplasser.map { it.arenakode }

    const val TILTAKSKODE_SYSTEM_KEY = "system"

    fun skalKometLagreTiltakstype(
        tiltakAsJson: String,
        objectMapper: ObjectMapper,
    ): Boolean {
        val systemNode = objectMapper
            .readTree(tiltakAsJson)
            .get(TILTAKSKODE_SYSTEM_KEY)
            ?: return true // gammelt format uten system key

        return systemNode is NullNode || systemNode.asString() == TiltakstypeSystem.TILTAKSADMINISTRASJON.name
    }
}
