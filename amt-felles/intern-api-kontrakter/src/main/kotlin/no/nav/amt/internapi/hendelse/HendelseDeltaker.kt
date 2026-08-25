package no.nav.amt.internapi.hendelse

import no.nav.amt.internapi.hendelse.HendelseDeltaker.Deltakerliste.Arrangor.Companion.UKJENT_VIRKSOMHET
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.toTitleCase
import java.time.LocalDate
import java.util.UUID

data class HendelseDeltaker(
    val id: UUID,
    val personident: String,
    val deltakerliste: Deltakerliste,
    val forsteVedtakFattet: LocalDate?,
    val opprettetDato: LocalDate?,
    val startdato: LocalDate? = null,
    val sluttdato: LocalDate? = null,
) {
    data class Deltakerliste(
        val id: UUID,
        val navn: String,
        val arrangor: Arrangor,
        val tiltak: Tiltak,
        val startdato: LocalDate? = null, // Må være nullable fordi de benyttes som dbo i amt-distribusjon
        val sluttdato: LocalDate? = null,
        val oppstartstype: Oppstartstype? = null, // Må være nullable fordi de benyttes som dbo i amt-distribusjon
        val pameldingstype: GjennomforingPameldingType? = null,
        val oppmoteSted: String? = null,
        val erEnkeltplass: Boolean? = null,
        val opplaringKategoriseringValg: OpplaringKategoriseringValg? = null,
        val prisinformasjon: PrisinformasjonDto? = null,
    ) {
        data class Arrangor(
            val id: UUID,
            val organisasjonsnummer: String,
            val navn: String,
            val overordnetArrangor: Arrangor?,
        ) {
            companion object {
                const val UKJENT_VIRKSOMHET = "Ukjent Virksomhet"
            }
        }

        data class Tiltak(
            val navn: String,
            val ledetekst: String?,
            val tiltakskode: Tiltakskode,
        )

        /**
         * Henter det faktiske arrangørnavnet som skal vises for deltakerlisten.
         *
         * For enkeltplasser brukes arrangørens navn. For øvrige deltakerlister brukes
         * navnet til den overordnede arrangøren dersom denne finnes og har et kjent navn.
         * Hvis overordnet arrangør mangler eller har navnet "Ukjent Virksomhet", brukes
         * arrangørens navn.
         *
         * @return det faktiske arrangørnavnet i visningsformat.
         */
        fun arrangorVisningsnavn(): String {
            val faktiskArrangornavn = if (erEnkeltplass == true) {
                arrangor.navn
            } else {
                val overordnetArrangorNavn = arrangor.overordnetArrangor
                    ?.takeUnless { it.navn == UKJENT_VIRKSOMHET }
                    ?.navn

                overordnetArrangorNavn ?: arrangor.navn
            }

            return faktiskArrangornavn.toTitleCase()
        }
    }
}
