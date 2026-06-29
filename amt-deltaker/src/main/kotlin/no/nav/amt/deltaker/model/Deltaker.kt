package no.nav.amt.deltaker.model

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
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
    val erEnkeltplass get(): Boolean = deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass

    fun toDeltakerVedVedtak(opplaringKategorisering: OpplaringKategoriseringValg? = null): DeltakerVedVedtak = DeltakerVedVedtak(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
        opplaringKategorisering = opplaringKategorisering,
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
}
