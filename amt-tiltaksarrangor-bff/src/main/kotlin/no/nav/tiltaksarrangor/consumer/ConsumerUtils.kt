package no.nav.tiltaksarrangor.consumer

import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.kafka.AmtGjennomforingPayload
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID

object ConsumerUtils {
    private const val DELTAKERLISTE_KEY = "deltakerliste"
    private const val LISTE_GJENNOMFORINGSTYPE_KEY = "gjennomforingstype"
    private const val FALLBACK_GJENNOMFORINGSTYPE = "UKJENT"

    fun getGjennomforingstypeFromDeltakerJsonPayload(
        messageJson: String,
        objectMapper: ObjectMapper,
    ): String = objectMapper
        .readTree(messageJson)
        .get(DELTAKERLISTE_KEY)
        ?.get(LISTE_GJENNOMFORINGSTYPE_KEY)
        ?.asString()
        ?: FALLBACK_GJENNOMFORINGSTYPE

    private fun mapTiltakstypeNavn(tiltakstypeNavn: String): String = if (tiltakstypeNavn == "Jobbklubb") {
        "Jobbsøkerkurs"
    } else {
        tiltakstypeNavn
    }

    fun AmtGjennomforingPayload.toDeltakerlisteDbo(arrangorId: UUID): DeltakerlisteDbo = DeltakerlisteDbo(
        id = id,
        lopenummer = lopenummer,
        navn = navn ?: tiltak.navn,
        gjennomforingstype = type,
        status = status,
        arrangorId = arrangorId,
        tiltaksnavn = mapTiltakstypeNavn(tiltak.navn),
        tiltakskode = tiltak.tiltakskode,
        startDato = startDato,
        sluttDato = sluttDato,
        erKurs = oppstart == Oppstartstype.FELLES,
        oppstartstype = oppstart,
        tilgjengeligForArrangorFraOgMedDato = tilgjengeligForArrangorFraOgMedDato,
        pameldingstype = pameldingstype,
    )

    fun AmtGjennomforingPayload.skalLagres(): Boolean = when (this.status) {
        GjennomforingStatusType.GJENNOMFORES -> true

        GjennomforingStatusType.AVSLUTTET ->
            sluttDato
                ?.let { LocalDate.now().isBefore(it.plusDays(15)) }
                ?: false

        else -> false
    }
}
