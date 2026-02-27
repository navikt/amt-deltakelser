package no.nav.amt.deltaker.bff.innbygger.model

import no.nav.amt.deltaker.bff.deltaker.api.model.ForslagResponse
import no.nav.amt.deltaker.bff.deltaker.api.model.ImportertFraArenaDto
import no.nav.amt.deltaker.bff.deltaker.api.model.getArrangorNavn
import no.nav.amt.deltaker.bff.deltaker.api.model.toResponse
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class InnbyggerDeltakerResponse(
    val deltakerId: UUID,
    val deltakerliste: DeltakerlisteDto,
    val status: DeltakerStatus,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: DeltakelsesinnholdDto?,
    val vedtaksinformasjon: VedtaksinformasjonDto?,
    val adresseDelesMedArrangor: Boolean,
    val forslag: List<ForslagResponse>,
    val importertFraArena: ImportertFraArenaDto?,
    val deltakelsesmengder: DeltakelsesmengderDto,
    val erManueltDeltMedArrangor: Boolean,
) {
    data class VedtaksinformasjonDto(
        val fattet: LocalDateTime?,
        val fattetAvNav: Boolean,
        val opprettet: LocalDateTime,
        val opprettetAv: String,
        val sistEndret: LocalDateTime,
        val sistEndretAv: String,
        val sistEndretAvEnhet: String?,
    )

    data class DeltakelsesinnholdDto(
        val ledetekst: String?,
        val innhold: List<Innhold>,
    )

    data class DeltakerlisteDto(
        val deltakerlisteId: UUID,
        val deltakerlisteNavn: String,
        val tiltakskode: Tiltakskode,
        val arrangorNavn: String,
        val oppstartstype: Oppstartstype?,
        val startdato: LocalDate?,
        val sluttdato: LocalDate?,
        val erEnkeltplassUtenRammeavtale: Boolean,
        val oppmoteSted: String?,
        val pameldingstype: GjennomforingPameldingType,
    )

    data class DeltakelsesmengderDto(
        val nesteDeltakelsesmengde: DeltakelsesmengdeDto?,
        val sisteDeltakelsesmengde: DeltakelsesmengdeDto?,
    )

    data class DeltakelsesmengdeDto(
        val deltakelsesprosent: Float,
        val dagerPerUke: Float?,
        val gyldigFra: LocalDate,
    )
}

fun Deltaker.toInnbyggerDeltakerResponse(
    ansatte: Map<UUID, NavAnsatt>,
    vedtakSistEndretAvEnhet: NavEnhet?,
    forslag: List<Forslag>,
): InnbyggerDeltakerResponse = InnbyggerDeltakerResponse(
    deltakerId = id,
    deltakerliste = InnbyggerDeltakerResponse.DeltakerlisteDto(
        deltakerlisteId = deltakerliste.id,
        deltakerlisteNavn = deltakerliste.navn,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        arrangorNavn = deltakerliste.arrangor.getArrangorNavn(),
        oppstartstype = deltakerliste.oppstart,
        startdato = deltakerliste.startDato,
        sluttdato = deltakerliste.sluttDato,
        // midlertidig løsning inntil vi vet ner om det foreligger rammeavtale eller ikke
        erEnkeltplassUtenRammeavtale = deltakerliste.tiltak.tiltakskode.erEnkeltplass(),
        oppmoteSted = deltakerliste.oppmoteSted,
        pameldingstype = deltakerliste.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
    ),
    status = status,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    deltakelsesinnhold = InnbyggerDeltakerResponse.DeltakelsesinnholdDto(
        ledetekst = deltakelsesinnhold?.ledetekst,
        innhold = deltakelsesinnhold?.innhold ?: emptyList(),
    ),
    vedtaksinformasjon = vedtaksinformasjon?.toDto(ansatte, vedtakSistEndretAvEnhet),
    adresseDelesMedArrangor = adresseDelesMedArrangor(),
    forslag = forslag.map { it.toResponse(deltakerliste.arrangor.getArrangorNavn()) },
    importertFraArena = ImportertFraArenaDto.fromDeltaker(this),
    deltakelsesmengder = InnbyggerDeltakerResponse.DeltakelsesmengderDto(
        nesteDeltakelsesmengde = deltakelsesmengder.nesteGjeldende?.toDto(),
        sisteDeltakelsesmengde = deltakelsesmengder.lastOrNull()?.toDto(),
    ),
    erManueltDeltMedArrangor = erManueltDeltMedArrangor,
)

private fun Vedtak.toDto(ansatte: Map<UUID, NavAnsatt>, vedtakSistEndretEnhet: NavEnhet?) = InnbyggerDeltakerResponse.VedtaksinformasjonDto(
    fattet = fattet,
    fattetAvNav = fattetAvNav,
    opprettet = opprettet,
    opprettetAv = ansatte[opprettetAv]?.navn ?: opprettetAv.toString(),
    sistEndret = sistEndret,
    sistEndretAv = ansatte[sistEndretAv]?.navn ?: sistEndretAv.toString(),
    sistEndretAvEnhet = vedtakSistEndretEnhet?.navn ?: sistEndretAvEnhet.toString(),
)

private fun Deltakelsesmengde.toDto() = InnbyggerDeltakerResponse.DeltakelsesmengdeDto(
    deltakelsesprosent,
    dagerPerUke,
    gyldigFra,
)
