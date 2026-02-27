package no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils

import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Vedtak
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object TestData {
    fun lagVedtak(
        deltakelsesprosent: Float? = 100F,
        dagerPerUke: Float? = 5F,
        fattet: LocalDateTime? = LocalDateTime.now(),
        opprettet: LocalDateTime = fattet ?: LocalDateTime.now(),
        deltakerId: UUID = UUID.randomUUID(),
    ) = Vedtak(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        fattet = fattet,
        gyldigTil = null,
        deltakerVedVedtak = DeltakerVedVedtak(
            id = deltakerId,
            startdato = null,
            sluttdato = null,
            deltakelsesprosent = deltakelsesprosent,
            dagerPerUke = dagerPerUke,
            bakgrunnsinformasjon = null,
            deltakelsesinnhold = null,
            status = lagDeltakerStatus(),
        ),
        fattetAvNav = true,
        opprettet = opprettet,
        opprettetAv = UUID.randomUUID(),
        opprettetAvEnhet = UUID.randomUUID(),
        sistEndret = opprettet,
        sistEndretAv = UUID.randomUUID(),
        sistEndretAvEnhet = UUID.randomUUID(),
    )

    fun lagDeltakerStatus() = DeltakerStatus(
        id = UUID.randomUUID(),
        type = DeltakerStatus.Type.VENTER_PA_OPPSTART,
        aarsak = null,
        gyldigFra = LocalDateTime.now(),
        gyldigTil = null,
        opprettet = LocalDateTime.now(),
    )

    fun lagImportertFraArena(
        deltakelsesprosent: Float? = 100F,
        dagerPerUke: Float? = 5F,
        innsoktDato: LocalDate = LocalDate.now(),
        opprettet: LocalDateTime = innsoktDato.atStartOfDay(),
        deltakerId: UUID = UUID.randomUUID(),
    ) = ImportertFraArena(
        deltakerId = deltakerId,
        importertDato = opprettet,
        deltakerVedImport = DeltakerVedImport(
            deltakerId = deltakerId,
            innsoktDato = innsoktDato,
            startdato = null,
            sluttdato = null,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            status = lagDeltakerStatus(),
        ),
    )

    fun lagDeltakerHistorikk(
        vedtak: List<Vedtak> = emptyList(),
        endringer: List<DeltakerEndring> = emptyList(),
        forslag: List<Forslag> = emptyList(),
        importertFraArena: List<ImportertFraArena> = emptyList(),
        endringerFraArrangor: List<EndringFraArrangor> = emptyList(),
    ) = emptyList<DeltakerHistorikk>()
        .asSequence()
        .plus(vedtak.map { DeltakerHistorikk.Vedtak(it) })
        .plus(endringer.map { DeltakerHistorikk.Endring(it) })
        .plus(forslag.map { DeltakerHistorikk.Forslag(it) })
        .plus(importertFraArena.map { DeltakerHistorikk.ImportertFraArena(it) })
        .plus(endringerFraArrangor.map { DeltakerHistorikk.EndringFraArrangor(it) })
        .toList()

    fun lagEndreDeltakelsesmengde(
        deltakelsesprosent: Int?,
        gyldigFra: LocalDate,
        opprettet: LocalDateTime,
        dagerPerUke: Int? = null,
        deltakerId: UUID = UUID.randomUUID(),
    ) = lagDeltakerEndring(
        deltakerId = deltakerId,
        endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
            deltakelsesprosent = deltakelsesprosent?.toFloat(),
            dagerPerUke = dagerPerUke?.toFloat(),
            gyldigFra = gyldigFra,
            begrunnelse = null,
        ),
        endret = opprettet,
    )

    fun lagLeggTilOppstartsdato(
        startdato: LocalDate = LocalDate.now(),
        sluttdato: LocalDate = startdato.plusMonths(3),
        opprettet: LocalDateTime = LocalDateTime.now(),
        deltakerId: UUID = UUID.randomUUID(),
    ) = EndringFraArrangor(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = UUID.randomUUID(),
        opprettet = opprettet,
        endring = EndringFraArrangor.LeggTilOppstartsdato(startdato, sluttdato),
    )

    fun lagEndreStartdato(
        startdato: LocalDate,
        opprettet: LocalDateTime,
        deltakerId: UUID = UUID.randomUUID(),
    ) = lagDeltakerEndring(
        deltakerId = deltakerId,
        endring = DeltakerEndring.Endring.EndreStartdato(
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            begrunnelse = null,
        ),
        endret = opprettet,
    )

    private fun lagDeltakerEndring(
        id: UUID = UUID.randomUUID(),
        deltakerId: UUID = UUID.randomUUID(),
        endring: DeltakerEndring.Endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("Oppdatert bakgrunnsinformasjon"),
        endretAv: UUID = UUID.randomUUID(),
        endretAvEnhet: UUID = UUID.randomUUID(),
        endret: LocalDateTime = LocalDateTime.now(),
        forslag: Forslag? = null,
    ) = DeltakerEndring(id, deltakerId, endring, endretAv, endretAvEnhet, endret, forslag)
}
