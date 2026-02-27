package no.nav.amt.distribusjon.utils.data

import no.nav.amt.distribusjon.utils.data.Persondata.lagNavBrukerResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.ArrangorResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.GjennomforingResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object DeltakerData {
    fun lagDeltakerResponse() = DeltakerResponse(
        id = UUID.randomUUID(),
        navBruker = lagNavBrukerResponse(),
        gjennomforing = lagGjennomforingResponse(),
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.VENTER_PA_OPPSTART,
            aarsak = null,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        deltakelsesinnhold = null,
        vedtaksinformasjon = null,
        erManueltDeltMedArrangor = false,
        kilde = Kilde.KOMET,
        sistEndret = LocalDateTime.now(),
        opprettet = LocalDateTime.now(),
        historikk = emptyList(),
        erLaastForEndringer = true,
        endringsforslagFraArrangor = emptyList(),
    )

    fun lagGjennomforingResponse() = GjennomforingResponse(
        id = UUID.randomUUID(),
        tiltakstype = lagTiltakstype(),
        navn = "deltakerliste navn",
        status = GjennomforingStatusType.GJENNOMFORES,
        startDato = LocalDate.now(),
        sluttDato = null,
        oppstart = null,
        arrangor = ArrangorResponse(navn = "arrangor", organisasjonsnummer = "123456789"),
        apentForPamelding = true,
        oppmoteSted = "Vet olle",
        pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
    )

    fun lagTiltakstype() = Tiltakstype(
        id = UUID.randomUUID(),
        navn = "Tiltaksnavn",
        tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
        innsatsgrupper = setOf(Innsatsgruppe.SITUASJONSBESTEMT_INNSATS),
        innhold = null,
    )
}
