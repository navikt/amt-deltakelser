package valp

import DbOperations
import db.ValpGjennomforing
import db.ValpTiltakstype
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val DATE_TIME_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

data class GjennomforingRow(
    val id: String,
    val type: String,
    val tiltakskode: String,
    val arrangor: String,
    val status: String,
    val navn: String?,
    val startDato: String?,
    val sluttDato: String?,
)

data class TiltakstypeRow(
    val id: String,
    val navn: String,
    val tiltakskode: String,
    val innsatsgrupper: String,
)

data class GjennomforingFormDefaults(
    val id: String,
    val type: String,
    val tiltakskode: String,
    val arrangorOrganisasjonsnummer: String,
    val pameldingType: String,
    val status: String,
    val oppstart: String,
    val opprettetTidspunkt: String,
    val oppdatertTidspunkt: String,
    val prisinformasjon: String,
    val navn: String,
    val startDato: String,
    val sluttDato: String,
    val tilgjengeligForArrangorFraOgMedDato: String,
    val apentForPamelding: String,
    val antallPlasser: String,
    val deltidsprosent: String,
    val oppmoteSted: String,
)

data class TiltakstypeFormDefaults(
    val id: String,
    val navn: String,
    val tiltakskode: String,
    val innsatsgrupper: String,
)

fun defaultGjennomforingEnkeltplassFormDefaults(now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): GjennomforingFormDefaults {
    val nowString = now.format(DATE_TIME_INPUT_FORMATTER)
    return GjennomforingFormDefaults(
        id = UUID.randomUUID().toString(),
        type = "enkeltplass",
        tiltakskode = "",
        arrangorOrganisasjonsnummer = "",
        pameldingType = "LOPENDE",
        status = "GJENNOMFORES",
        oppstart = "LOPENDE",
        opprettetTidspunkt = nowString,
        oppdatertTidspunkt = nowString,
        prisinformasjon = "",
        navn = "",
        startDato = "",
        sluttDato = "",
        tilgjengeligForArrangorFraOgMedDato = "",
        apentForPamelding = "true",
        antallPlasser = "",
        deltidsprosent = "",
        oppmoteSted = "",
    )
}

fun defaultGjennomforingGruppeFormDefaults(now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): GjennomforingFormDefaults {
    val nowString = now.format(DATE_TIME_INPUT_FORMATTER)
    return GjennomforingFormDefaults(
        id = UUID.randomUUID().toString(),
        type = "gruppe",
        tiltakskode = "",
        arrangorOrganisasjonsnummer = "",
        pameldingType = "LOPENDE",
        status = "GJENNOMFORES",
        oppstart = "LOPENDE",
        opprettetTidspunkt = nowString,
        oppdatertTidspunkt = nowString,
        prisinformasjon = "",
        navn = "",
        startDato = "",
        sluttDato = "",
        tilgjengeligForArrangorFraOgMedDato = "",
        apentForPamelding = "true",
        antallPlasser = "10",
        deltidsprosent = "100",
        oppmoteSted = "",
    )
}

fun defaultTiltakstypeFormDefaults(): TiltakstypeFormDefaults {
    return TiltakstypeFormDefaults(
        id = UUID.randomUUID().toString(),
        navn = "",
        tiltakskode = "",
        innsatsgrupper = "[]",
    )
}

fun fetchGjennomforinger(): List<GjennomforingRow> {
    return DbOperations.inTransaction {
        ValpGjennomforing.selectAll().map { row ->
            GjennomforingRow(
                id = row[ValpGjennomforing.id],
                type = row[ValpGjennomforing.type],
                tiltakskode = row[ValpGjennomforing.tiltakskode],
                arrangor = row[ValpGjennomforing.arrangorOrganisasjonsnummer],
                status = row[ValpGjennomforing.status],
                navn = row[ValpGjennomforing.navn],
                startDato = row[ValpGjennomforing.startDato],
                sluttDato = row[ValpGjennomforing.sluttDato],
            )
        }
    }
}

fun fetchTiltakstyper(): List<TiltakstypeRow> {
    return DbOperations.inTransaction {
        ValpTiltakstype.selectAll().map { row ->
            TiltakstypeRow(
                id = row[ValpTiltakstype.id],
                navn = row[ValpTiltakstype.navn],
                tiltakskode = row[ValpTiltakstype.tiltakskode],
                innsatsgrupper = row[ValpTiltakstype.innsatsgrupper],
            )
        }
    }
}

fun insertGjennomforing(form: GjennomforingFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        ValpGjennomforing.insert {
            it[id] = form.id
            it[type] = form.type
            it[tiltakskode] = form.tiltakskode
            it[arrangorOrganisasjonsnummer] = form.arrangorOrganisasjonsnummer
            it[pameldingType] = form.pameldingType
            it[status] = form.status
            it[oppstart] = form.oppstart
            it[opprettetTidspunkt] = form.opprettetTidspunkt
            it[oppdatertTidspunkt] = form.oppdatertTidspunkt
            it[prisinformasjon] = form.prisinformasjon
            it[navn] = form.navn
            it[startDato] = form.startDato
            it[sluttDato] = form.sluttDato
            it[tilgjengeligForArrangorFraOgMedDato] = form.tilgjengeligForArrangorFraOgMedDato
            it[apentForPamelding] = form.apentForPamelding
            it[antallPlasser] = form.antallPlasser
            it[deltidsprosent] = form.deltidsprosent
            it[oppmoteSted] = form.oppmoteSted
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun insertTiltakstype(form: TiltakstypeFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        ValpTiltakstype.insert {
            it[id] = form.id
            it[navn] = form.navn
            it[tiltakskode] = form.tiltakskode
            it[innsatsgrupper] = form.innsatsgrupper
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}


