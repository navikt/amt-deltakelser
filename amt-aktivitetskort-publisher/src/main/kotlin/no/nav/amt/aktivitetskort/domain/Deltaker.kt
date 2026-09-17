package no.nav.amt.aktivitetskort.domain

import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltaker(
    val id: UUID,
    val personident: String,
    val status: DeltakerStatusModel,
    val gjennomforing: Gjennomforing,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val kilde: Kilde,
) {
    companion object {
        fun fromDeltakerResponse(deltakerResponse: DeltakerResponse) = with(deltakerResponse) {
            Deltaker(
                id = id,
                personident = navBruker.personident,
                status = DeltakerStatusModel(
                    type = status.type,
                    aarsak = status.aarsak?.type,
                    gyldigFra = status.gyldigFra,
                ),
                gjennomforing = Gjennomforing(
                    id = gjennomforing.id,
                    visningsnavn = gjennomforing.visningsnavn.aktivitetskortTittel,
                    tiltakstype = gjennomforing.tiltakstype,
                    arrangor = gjennomforing.arrangor?.let {
                        Arrangor(
                            id = it.id,
                            organisasjonsnummer = it.organisasjonsnummer,
                            navn = it.navn,
                            overordnetArrangorId = null,
                        )
                    } ?: throw IllegalStateException("Arrangør mangler id for deltaker $id"),
                ),
                startdato = startdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                kilde = kilde,
            )
        }
    }
}

data class Gjennomforing(
    val id: UUID,
    val visningsnavn: String,
    val tiltakstype: Tiltakstype,
    val arrangor: Arrangor,
)
