package no.nav.amt.distribusjon.journalforing.pdf

import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig
import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.internapi.hendelse.UtkastDto
import no.nav.amt.internapi.journalforing.pdf.AvsenderDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto.DeltakerDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto.DeltakerlisteDto
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto.EnkeltplassInnhold
import no.nav.amt.internapi.journalforing.pdf.EnkeltplassPdfDto.Prisinformasjon
import no.nav.amt.lib.models.deltaker.Innhold.Companion.INNHOLDSKODE_ANNET
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.toTitleCase
import java.time.LocalDate

object EnkeltplassPdfDtoMapper {
    fun lagEnkeltplassPdfDto(
        deltaker: HendelseDeltaker,
        navBruker: NavBruker,
        veileder: HendelseAnsvarlig.NavVeileder,
        opprettetDato: LocalDate,
        utkast: UtkastDto,
    ) = EnkeltplassPdfDto(
        deltaker = DeltakerDto(
            fornavn = navBruker.fornavn,
            mellomnavn = navBruker.mellomnavn,
            etternavn = navBruker.etternavn,
            personident = deltaker.personident,
        ),
        deltakerliste = DeltakerlisteDto(
            tiltaksnavn = deltaker.deltakerliste.tiltakskodenavn(),
            arrangornavn = deltaker.deltakerliste.arrangor.navn
                .toTitleCase(),
            startdato = deltaker.startdato ?: throw IllegalStateException(
                "Deltaker ${deltaker.id} må ha startdato for å lage enkeltplass innsøkingsbrev",
            ),
            sluttdato = deltaker.sluttdato ?: throw IllegalStateException(
                "Deltaker ${deltaker.id} må ha sluttdato for å lage enkeltplass innsøkingsbrev",
            ),
            oppstartstype = deltaker.deltakerliste.oppstartstype ?: throw IllegalStateException(
                "Deltakerliste ${deltaker.deltakerliste.id} må ha oppstartstype for å lage enkeltplass innsøkingsbrev",
            ),
        ),
        avsender = AvsenderDto(
            navn = veileder.navn,
            enhet = navBruker.navEnhet?.navn ?: "NAV",
        ),
        opprettetDato = opprettetDato,
        innholdFritekst = utkast.innhold
            ?.find { it.innholdskode == INNHOLDSKODE_ANNET }
            ?.beskrivelse
            ?: throw IllegalStateException(
                "Deltakerliste ${deltaker.deltakerliste.id} må ha beskrivelse for å lage enkeltplass innsøkingsbrev",
            ),
        deltakelsesmengdeAntallDager = utkast.dagerPerUke?.toInt(),
        innhold = deltaker.deltakerliste.toInnhold(),
        prisinformasjon = deltaker.deltakerliste.toPrisinformasjon(),
    )

    internal fun PrisinformasjonDto.Tilskudd.Tilskuddstype.visningsnavn(): String = when (this) {
        PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER -> "Skolepenger"
        PrisinformasjonDto.Tilskudd.Tilskuddstype.SEMESTERAVGIFT -> "Semesteravgift"
        PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR -> "Eksamensgebyr"
        PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE -> "Studiereise"
        PrisinformasjonDto.Tilskudd.Tilskuddstype.INTEGRERT_BOTILBUD -> "Integrert botilbud"
    }

    internal fun HendelseDeltaker.Deltakerliste.toPrisinformasjon(): Prisinformasjon {
        val prisinfoFraDeltakerliste = prisinformasjon
            ?: throw IllegalStateException("Deltakerliste ${this.id} må ha prisinformasjon for å lage enkeltplass innsøkingsbrev")

        return when (prisinfoFraDeltakerliste) {
            is PrisinformasjonDto.Anskaffelse -> Prisinformasjon.Anskaffelse(
                pris = prisinfoFraDeltakerliste.pris,
            )

            is PrisinformasjonDto.Tilskudd -> Prisinformasjon.Tilskudd(
                tilskudd = prisinfoFraDeltakerliste.tilskudd
                    .sortedBy { it.type.sortOrder }
                    .map {
                        Prisinformasjon.Tilskudd.TilskuddInfo(
                            type = it.type.visningsnavn(),
                            pris = it.pris,
                        )
                    },
                tilleggsopplysninger = prisinfoFraDeltakerliste.tilleggsopplysninger,
            )

            is PrisinformasjonDto.IngenKostnader -> {
                when (prisinfoFraDeltakerliste.aarsak) {
                    PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI -> Prisinformasjon.IngenKostnader
                    PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT -> Prisinformasjon.Innbyggerfinansiert(
                        tilleggsopplysninger = prisinfoFraDeltakerliste.tilleggsopplysninger ?: throw IllegalStateException(
                            "tilleggsopplysninger må være satt for innbyggerfinansiert prisinformasjon",
                        ),
                    )
                }
            }
        }
    }

    internal fun HendelseDeltaker.Deltakerliste.toInnhold(): EnkeltplassInnhold {
        val opplaringKategoriseringValg = this.opplaringKategoriseringValg
            ?: throw IllegalStateException("Deltakerliste ${this.id} må ha opplæring kategorisering for å lage enkeltplass innsøkingsbrev")

        val representerSet = opplaringKategoriseringValg.hentRepresenterer()

        return when {
            // NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV
            representerSet.contains(OpplaringKategoriseringType.KURSTYPE_ID) -> EnkeltplassInnhold.UtenInnhold

            representerSet.contains(OpplaringKategoriseringType.BRANSJE_ID) -> EnkeltplassInnhold.Arbeidsmarkedsopplaering(
                bransje = opplaringKategoriseringValg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID).single(),
                forerkortOgSertifiseringer = opplaringKategoriseringValg
                    .hentVerdier(
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        throwIfEmpty = false,
                    ).plus(
                        opplaringKategoriseringValg.hentVerdier(
                            representerer = OpplaringKategoriseringType.SERTIFISERINGER,
                            throwIfEmpty = false,
                        ),
                    ),
            )

            representerSet.contains(OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID) -> {
                EnkeltplassInnhold.FagOgYrkesopplaering(
                    utdanningsprogram = opplaringKategoriseringValg
                        .hentVerdier(OpplaringKategoriseringType.UTDANNINGSPROGRAM_ID)
                        .single(),
                    laerefag = opplaringKategoriseringValg
                        .hentVerdier(OpplaringKategoriseringType.LAREFAG),
                )
            }

            else -> throw IllegalArgumentException("Kunne ikke avgjøre type PDF-innhold for ${tiltak.tiltakskode}")
        }
    }

    internal fun HendelseDeltaker.Deltakerliste.tiltakskodenavn(): String = when (tiltak.tiltakskode) {
        // Enkeltplass, ny forskrift:
        Tiltakskode.ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring"
        Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> {
            // NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV avviker fra øvrige tiltakskoder ved at kurstype
            // skal benyttes som tiltaksnavn i heading i brev
            opplaringKategoriseringValg
                ?.hentVerdier(OpplaringKategoriseringType.KURSTYPE_ID)
                ?.firstOrNull()
                ?: throw IllegalStateException("Kunne ikke finne kurstype for enkeltplass")
        }

        Tiltakskode.STUDIESPESIALISERING -> "Studiespesialisering"
        Tiltakskode.FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring"
        Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING -> "Høyere yrkesfaglig utdanning"

        // Enkeltplass, gammel forskrift (importert fra Arena):
        Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING -> "Arbeidsmarkedsopplæring (enkeltplass)"
        Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING -> "Fag- og yrkesopplæring (enkeltplass)"

        // Enkeltplass, ny og gammel forskrift:
        Tiltakskode.HOYERE_UTDANNING -> "Høyere utdanning"

        else -> throw IllegalArgumentException("Ukjent enkeltplass tiltakstype: ${tiltak.tiltakskode}")
    }
}
