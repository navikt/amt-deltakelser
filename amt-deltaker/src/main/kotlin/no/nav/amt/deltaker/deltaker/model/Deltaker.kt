package no.nav.amt.deltaker.deltaker.model

import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.tiltakskoordinator.Deltakeroppdatering
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.NavBruker
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Deltaker(
    val id: UUID,
    val navBruker: NavBruker,
    val deltakerliste: Deltakerliste,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
    val vedtaksinformasjon: Vedtaksinformasjon?,
    val sistEndret: LocalDateTime,
    val kilde: Kilde,
    val erManueltDeltMedArrangor: Boolean,
    val opprettet: LocalDateTime,
) {
    fun harSluttet(): Boolean = status.type in AVSLUTTENDE_STATUSER

    fun deltarPaKurs(): Boolean = deltakerliste.erFellesOppstart

    val deltarPaOpplaeringstiltak get(): Boolean = deltakerliste.tiltakstype.tiltakskode.erOpplaeringstiltak()

    fun toDeltakerVedVedtak(): DeltakerVedVedtak = DeltakerVedVedtak(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
    )

    fun toDeltakerVedImport(innsoktDato: LocalDate) = DeltakerVedImport(
        deltakerId = id,
        innsoktDato = innsoktDato,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        status = status,
    )

    fun toDeltakerOppdatering(historikk: List<DeltakerHistorikk>): Deltakeroppdatering = Deltakeroppdatering(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
        historikk = historikk,
        sistEndret = sistEndret,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        forcedUpdate = false,
    )
}
