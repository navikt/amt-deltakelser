package no.nav.amt.deltaker.bff.deltaker.model

import no.nav.amt.deltaker.bff.utils.FERIETILLEGG
import no.nav.amt.deltaker.bff.utils.months
import no.nav.amt.deltaker.bff.utils.weeks
import no.nav.amt.deltaker.bff.utils.years
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerModel(
    val id: UUID,
    val navBruker: NavBrukerModel,
    val gjennomforing: GjennomforingModel,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val vedtaksinformasjon: VedtaksinformasjonModel?,
    val status: DeltakerStatus,
    val kanEndres: Boolean,
    val sistEndret: LocalDateTime,
    val erManueltDeltMedArrangor: Boolean,
    val historikk: List<DeltakerHistorikk>,
    val erLaastForEndringer: Boolean,
    val endringsforslagFraArrangor: List<Forslag>,
) {
    val deltakelsesmengder: Deltakelsesmengder
        get() = startdato?.let { historikk.toDeltakelsesmengder().periode(it, sluttdato) } ?: historikk.toDeltakelsesmengder()

    val adresseDelesMedArrangor = this.navBruker.adressebeskyttelse == null &&
        this.gjennomforing.tiltak.adresseKanDelesMedArrangor

    fun harSluttet(): Boolean = status.type in AVSLUTTENDE_STATUSER

    fun harSluttetForMindreEnnToMndSiden(): Boolean {
        if (!harSluttet()) return false

        val nyesteDato = listOfNotNull(sluttdato, status.gyldigFra.toLocalDate())
            .maxOrNull() ?: return false

        val toMndSiden = LocalDate.now().minusMonths(2)
        return nyesteDato.isAfter(toMndSiden)
    }

    /**
     Noen tiltak har en max varighet som kan overgås ved visse omstendigheter,
     softMaxVarighet er den ordinære varigheten for disse tiltakene,
     mens maxVarighet er det absolutte max for alle tiltak.

     https://confluence.adeo.no/pages/viewpage.action?pageId=583122378
     https://lovdata.no/forskrift/2015-12-11-1598
     */
    val softMaxVarighet: Duration?
        get() = when (gjennomforing.tiltak.tiltakskode) {
            Tiltakskode.ARBEIDSFORBEREDENDE_TRENING -> years(2)

            Tiltakskode.OPPFOLGING -> when (navBruker.innsatsgruppe) {
                Innsatsgruppe.SPESIELT_TILPASSET_INNSATS,
                Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS,
                Innsatsgruppe.VARIG_TILPASSET_INNSATS,
                -> years(3)

                Innsatsgruppe.SITUASJONSBESTEMT_INNSATS,
                Innsatsgruppe.STANDARD_INNSATS,
                null,
                -> null
            }

            Tiltakskode.ARBEIDSRETTET_REHABILITERING,
            Tiltakskode.AVKLARING,
            -> weeks(12)

            Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> weeks(8)

            Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.JOBBKLUBB,
            Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.HOYERE_UTDANNING,
            Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            // nye tiltakskoder
            Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            Tiltakskode.STUDIESPESIALISERING,
            Tiltakskode.FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
            -> null
        }

    /**
     Noen tiltak har en max varighet som kan overgås ved visse omstendigheter,
     softMaxVarighet er den ordinære varigheten for disse tiltakene,
     mens maxVarighet er det absolutte max for alle tiltak.

     https://confluence.adeo.no/pages/viewpage.action?pageId=583122378
     https://lovdata.no/forskrift/2015-12-11-1598
     */
    val maxVarighet: Duration?
        get() = when (gjennomforing.tiltak.tiltakskode) {
            Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            -> years(3)

            Tiltakskode.ARBEIDSRETTET_REHABILITERING,
            Tiltakskode.AVKLARING,
            -> weeks(12 + FERIETILLEGG)

            Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK -> weeks(8 + FERIETILLEGG)

            Tiltakskode.HOYERE_UTDANNING,
            Tiltakskode.STUDIESPESIALISERING,
            Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
            Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            -> years(4)

            Tiltakskode.FAG_OG_YRKESOPPLAERING,
            -> years(5)

            Tiltakskode.OPPFOLGING -> when (navBruker.innsatsgruppe) {
                Innsatsgruppe.SITUASJONSBESTEMT_INNSATS -> years(1)

                Innsatsgruppe.SPESIELT_TILPASSET_INNSATS,
                Innsatsgruppe.GRADERT_VARIG_TILPASSET_INNSATS,
                Innsatsgruppe.VARIG_TILPASSET_INNSATS,
                -> years(3).plus(months(6))

                else -> null
            }

            Tiltakskode.JOBBKLUBB,
            Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            -> null
        }
}
