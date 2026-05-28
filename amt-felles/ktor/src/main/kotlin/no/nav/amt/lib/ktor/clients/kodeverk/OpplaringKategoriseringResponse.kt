package no.nav.amt.lib.ktor.clients.kodeverk

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

/**
 * Representerer kodeverket knyttet til en bestemt [no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode].
 *
 * Kodeverket består av et hierarki av [Alternativ]-er som beskriver de relaterte
 * valgene som er aktuelle for tiltaket — for eksempel bransjer, førerkortklasser
 * eller sertifiseringer.
 *
 * Hierarkiet kan inneholde flere nivåer: rene grupperingsnoder ([Alternativ.UtdanningGruppe])
 * for å organisere innholdet, og innerste valgbare grupper ([Alternativ.Verdigruppe])
 * som inneholder de konkrete [Alternativ.Verdi]-ene brukeren kan velge mellom.
 *
 * @property tiltakskode Tiltakskoden kodeverket gjelder for.
 * @property alternativer Toppnivå-containere i kodeverket — enten [Alternativ.UtdanningGruppe]
 *   eller [Alternativ.Verdigruppe]. Kan ikke inneholde [Alternativ.Verdi] direkte.
 * @property sertifiseringValg Sertifiseringer valgt for en enkeltplass-gjennomføring.
 *   Verdiene kommer fra et eksternt søk ([Alternativ.VerdigruppeSok]) og lagres separat
 *   fra det statiske kodeverket i [alternativer].
 */
data class OpplaringKategoriseringResponse(
    val tiltakskode: Tiltakskode,
    val alternativer: List<Alternativ.Container>,
    val sertifiseringValg: Set<SertifiseringValg> = emptySet(),
) {
    /**
     * Returnerer en kopi der [Alternativ.Verdi.valgt] er satt til `true` for alle
     * verdier med `id` i [kodeverkValg], og `false` for alle øvrige.
     * [sertifiseringValg] erstatter eventuelle eksisterende sertifiseringer i responsen.
     *
     * Synkroniserer hele treet — kildedataens initiale `valgt`-verdier overskrives
     * alltid, slik at resultatet kun reflekterer [kodeverkValg] og [sertifiseringValg].
     */
    fun settValgt(
        kodeverkValg: Set<UUID>,
        sertifiseringValg: Set<SertifiseringValg>,
    ): OpplaringKategoriseringResponse = copy(
        alternativer = alternativer.map { it.settValgt(kodeverkValg) },
        sertifiseringValg = sertifiseringValg,
    )

    private fun Alternativ.Container.settValgt(kodeverkValg: Set<UUID>): Alternativ.Container = when (this) {
        is Alternativ.Verdigruppe -> copy(alternativer = alternativer.map { it.settValgt(kodeverkValg) })
        is Alternativ.VerdigruppeSok -> this
        is Alternativ.UtdanningGruppe -> copy(
            utdanninger = utdanninger.map {
                it.copy(
                    larefag = it.larefag.copy(
                        alternativer = it.larefag.alternativer.map { verdi ->
                            verdi.settValgt(kodeverkValg)
                        },
                    ),
                )
            },
        )
    }

    private fun Alternativ.Verdi.settValgt(kodeverkValg: Set<UUID>): Alternativ.Verdi = copy(valgt = id in kodeverkValg)

    /**
     * Angir hvordan brukeren kan velge blant verdiene i en [Alternativ.Verdigruppe].
     */
    enum class Seleksjonstype {
        /** Brukeren kan velge nøyaktig én verdi. */
        ENKELTVALG,

        /** Brukeren kan velge flere verdier samtidig. */
        FLERVALG,
    }

    enum class Representerer {
        KURSTYPE_ID,
        BRANSJE_ID,
        SERTIFISERINGER,
        FORERKORT,
        INNHOLDSELEMENTER,
        NORSKPROVE,
        UTDANNINGSPROGRAM_ID,
        LAREFAG,
    }

    /**
     * Et element i kodeverk-hierarkiet.
     *
     * Et alternativ er enten en [Container] som inneholder andre alternativer
     * ([UtdanningGruppe] eller [Verdigruppe]), eller en konkret valgbar [Verdi].
     *
     * @property id Unik identifikator for alternativet.
     * @property visningsnavn Navnet som vises i UI.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Alternativ.UtdanningGruppe::class, name = "UtdanningGruppe"),
        JsonSubTypes.Type(value = Alternativ.Verdigruppe::class, name = "Verdigruppe"),
        JsonSubTypes.Type(value = Alternativ.VerdigruppeSok::class, name = "VerdigruppeSok"),
        JsonSubTypes.Type(value = Alternativ.Verdi::class, name = "Verdi"),
    )
    sealed interface Alternativ {
        val id: UUID?
        val visningsnavn: String

        /**
         * Et alternativ som kan inneholde andre alternativer — enten en ren
         * grupperingsnode ([UtdanningGruppe]) eller en valgbar gruppe med [Verdi]-er
         * ([Verdigruppe]).
         *
         * En [Verdi] er ikke en [Container] og kan derfor ikke inneholde andre
         * alternativer.
         */
        sealed interface Container : Alternativ

        /**
         * Gruppering for utdanningsprogram og lærefag
         *
         * Muliggjør at valg av program, gir andre muligheter for lærefag
         */
        data class UtdanningGruppe(
            override val id: UUID? = null,
            override val visningsnavn: String,
            val representerer: Representerer,
            val pakrevd: Boolean,
            val utdanninger: List<UtdanningValg>,
        ) : Container {
            data class UtdanningValg(
                val id: UUID,
                val visningsnavn: String,
                val larefag: Verdigruppe,
            )
        }

        /**
         * En valgbar gruppe — det innerste nivået i hierarkiet som inneholder
         * direkte valgbare [Verdi]-er.
         *
         * Eksempler på verdigrupper:
         * - "Bransje" med verdier "Bygg og anlegg", "Helse og omsorg"
         * - "Førerkortklasse" med verdier "B", "C1", "CE"
         *
         * @property id Unik identifikator for verdigruppen.
         * @property visningsnavn Navnet som vises i UI (f.eks. "Bransje").
         * @property seleksjonstype Hvordan brukeren kan velge blant verdiene
         *   (ett enkelt valg eller flere samtidig).
         * @property alternativer Verdiene brukeren kan velge mellom.
         */
        @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
        data class Verdigruppe(
            override val id: UUID?,
            override val visningsnavn: String,
            val pakrevd: Boolean,
            val representerer: Representerer,
            val seleksjonstype: Seleksjonstype,
            val alternativer: List<Verdi>,
        ) : Container

        /**
         * Representerer en Verdigruppe, hvor verdiene må søkes etter i en gitt kilde.
         * Siden søk er mer omfattende, og har sin egen responsstruktur, dekkes ikke
         * integrasjonsdetaljene her.
         *
         * Eksempler på VerdigruppeSok:
         * - Janzz sertifisering
         *
         * @property id Unik identifikator for verdigruppen.
         * @property visningsnavn Navnet som vises i UI (f.eks. "Sertifiseringer").
         * @property representerer Hva verdigruppen representerer.
         * @property seleksjonstype Hvordan brukeren kan velge blant verdiene
         *   (ett enkelt valg eller flere samtidig).
         * @property kilde Opphavet til verdiene som kan velges.
         */
        data class VerdigruppeSok(
            override val id: UUID?,
            override val visningsnavn: String,
            val representerer: Representerer? = null,
            val seleksjonstype: Seleksjonstype,
            val kilde: Kilde,
        ) : Container {
            enum class Kilde {
                JANZZ_SERTIFISERING,
            }
        }

        /**
         * En konkret valgbar verdi i en [Verdigruppe].
         *
         * Eksempler på verdier:
         * - Bransje: "Bygg og anlegg", "Helse og omsorg"
         * - Førerkortklasse: "B", "C1", "CE"
         * - Sertifisering: "Truckførerbevis", "Varmt arbeid"
         *
         * @property id Unik identifikator for verdien.
         * @property visningsnavn Navnet som vises i UI.
         * @property valgt Om verdien er aktivert for denne gjennomføringen. Dette feltet
         *   er internt hos Komet og er ikke en del av selve kodeverket.
         */
        @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
        data class Verdi(
            override val id: UUID,
            override val visningsnavn: String,
            val valgt: Boolean = false, // kun internt hos Komet, ikke i kodeverket
        ) : Alternativ
    }
}
