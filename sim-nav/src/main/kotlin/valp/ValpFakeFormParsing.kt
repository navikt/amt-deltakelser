package valp

import io.ktor.http.Parameters
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_TIME_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

data class GjennomforingFormInput(
    val id: String,
    val type: String,
    val tiltakskode: String,
    val arrangorOrganisasjonsnummer: String,
    val pameldingType: String,
    val status: String,
    val oppstart: String,
    val opprettetTidspunkt: String,
    val oppdatertTidspunkt: String,
    val prisinformasjon: String?,
    val navn: String?,
    val startDato: String?,
    val sluttDato: String?,
    val tilgjengeligForArrangorFraOgMedDato: String?,
    val apentForPamelding: String?,
    val antallPlasser: String?,
    val deltidsprosent: String?,
    val oppmoteSted: String?,
)

data class TiltakstypeFormInput(
    val id: String,
    val navn: String,
    val tiltakskode: String,
    val innsatsgrupper: String,
)

fun Parameters.toGjennomforingEnkeltplassFormInput(): GjennomforingFormInput {
    return GjennomforingFormInput(
        id = required("id"),
        type = "enkeltplass",
        tiltakskode = required("tiltakskode"),
        arrangorOrganisasjonsnummer = required("arrangorOrganisasjonsnummer"),
        pameldingType = required("pameldingType"),
        status = required("status"),
        oppstart = required("oppstart"),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtcText(),
        oppdatertTidspunkt = required("oppdatertTidspunkt").toOffsetDateTimeUtcText(),
        prisinformasjon = optional("prisinformasjon"),
        navn = null,
        startDato = null,
        sluttDato = null,
        tilgjengeligForArrangorFraOgMedDato = null,
        apentForPamelding = null,
        antallPlasser = null,
        deltidsprosent = null,
        oppmoteSted = null,
    )
}

fun Parameters.toGjennomforingGruppeFormInput(): GjennomforingFormInput {
    return GjennomforingFormInput(
        id = required("id"),
        type = "gruppe",
        tiltakskode = required("tiltakskode"),
        arrangorOrganisasjonsnummer = required("arrangorOrganisasjonsnummer"),
        pameldingType = required("pameldingType"),
        status = required("status"),
        oppstart = required("oppstart"),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtcText(),
        oppdatertTidspunkt = required("oppdatertTidspunkt").toOffsetDateTimeUtcText(),
        prisinformasjon = optional("prisinformasjon"),
        navn = required("navn"),
        startDato = required("startDato"),
        sluttDato = optional("sluttDato"),
        tilgjengeligForArrangorFraOgMedDato = optional("tilgjengeligForArrangorFraOgMedDato"),
        apentForPamelding = required("apentForPamelding"),
        antallPlasser = required("antallPlasser"),
        deltidsprosent = required("deltidsprosent"),
        oppmoteSted = optional("oppmoteSted"),
    )
}

fun Parameters.toTiltakstypeFormInput(): TiltakstypeFormInput {
    return TiltakstypeFormInput(
        id = required("id"),
        navn = required("navn"),
        tiltakskode = required("tiltakskode"),
        innsatsgrupper = required("innsatsgrupper"),
    )
}

private fun Parameters.required(name: String): String =
    get(name)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Missing field '$name'")

private fun Parameters.optional(name: String): String? = get(name)?.takeIf { it.isNotBlank() }

private fun String.toOffsetDateTimeUtcText(): String =
    LocalDateTime.parse(this, DATE_TIME_INPUT_FORMATTER).atOffset(ZoneOffset.UTC).toString()

