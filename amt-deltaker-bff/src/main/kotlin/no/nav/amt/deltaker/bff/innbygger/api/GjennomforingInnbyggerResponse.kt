package no.nav.amt.deltaker.bff.innbygger.api

import no.nav.amt.deltaker.bff.model.GjennomforingModel
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.util.UUID

data class GjennomforingInnbyggerResponse(
    val deltakerlisteId: UUID,
    val deltakerlisteNavn: String,
    val tiltakskode: Tiltakskode,
    val arrangorNavn: String,
    val oppstartstype: Oppstartstype?,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val erEnkeltplassUtenRammeavtale: Boolean,
    val erEnkeltplass: Boolean,
    val oppmoteSted: String?,
    val pameldingstype: GjennomforingPameldingType,
) {
    companion object {
        fun fromModel(gjennomforing: GjennomforingModel) = GjennomforingInnbyggerResponse(
            deltakerlisteId = gjennomforing.id,
            deltakerlisteNavn = gjennomforing.navn,
            tiltakskode = gjennomforing.tiltak.tiltakskode,
            // Nå er det amtdeltaker som sender med navnet som er riktig for visningen
            arrangorNavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør",
            oppstartstype = gjennomforing.oppstart,
            startdato = gjennomforing.startDato,
            sluttdato = gjennomforing.sluttDato,
            erEnkeltplassUtenRammeavtale = gjennomforing.erEnkeltplass, // TODO: Denne skal fjernes når frontend er klar
            erEnkeltplass = gjennomforing.erEnkeltplass,
            oppmoteSted = gjennomforing.oppmoteSted,
            pameldingstype = gjennomforing.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
        )
    }
}
