package valp

import DatabaseConfig
import DbOperations
import ValpGjennomforing
import ValpTiltakstype
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

const val VALP_PATH_PREFIX = "/valp"

fun Route.valpFakeRoutes() {
    route(VALP_PATH_PREFIX) {
        get {
            val gjennomforings = DbOperations.inTransaction {
                if (DatabaseConfig.isConnected()) {
                    transaction {
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
                } else {
                    emptyList()
                }
            }

            val tiltakstyper = DbOperations.inTransaction {
                if (DatabaseConfig.isConnected()) {
                    transaction {
                        ValpTiltakstype.selectAll().map { row ->
                            TiltakstypeRow(
                                id = row[ValpTiltakstype.id],
                                navn = row[ValpTiltakstype.navn],
                                tiltakskode = row[ValpTiltakstype.tiltakskode],
                                innsatsgrupper = row[ValpTiltakstype.innsatsgrupper],
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            }

            call.respondHtml(HttpStatusCode.OK) {
                valpPage(gjennomforings, tiltakstyper)
            }
        }
    }
}

private data class GjennomforingRow(
    val id: String,
    val type: String,
    val tiltakskode: String,
    val arrangor: String,
    val status: String,
    val navn: String?,
    val startDato: String?,
    val sluttDato: String?,
)

private data class TiltakstypeRow(
    val id: String,
    val navn: String,
    val tiltakskode: String,
    val innsatsgrupper: String,
)

private fun HTML.valpPage(
    gjennomforings: List<GjennomforingRow>,
    tiltakstyper: List<TiltakstypeRow>,
) {
    head {
        title("Valp - Simulator")
        style {
            unsafe {
                raw("""
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                    h2 { color: #666; margin-top: 30px; border-bottom: 2px solid #ddd; padding-bottom: 10px; }
                    .empty { color: #999; font-style: italic; padding: 20px; }
                    .section { margin: 30px 0; }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin-top: 10px;
                    }
                    th {
                        background-color: #f0f0f0;
                        border: 1px solid #ddd;
                        padding: 10px;
                        text-align: left;
                        font-weight: bold;
                    }
                    td {
                        border: 1px solid #ddd;
                        padding: 10px;
                    }
                    tr:nth-child(even) { background-color: #f9f9f9; }
                    .id { font-family: monospace; font-size: 0.9em; color: #666; }
                    .type-badge {
                        display: inline-block;
                        padding: 3px 8px;
                        border-radius: 3px;
                        font-size: 0.85em;
                        font-weight: bold;
                    }
                    .type-enkeltplass { background-color: #fff3cd; color: #856404; }
                    .type-gruppe { background-color: #cfe2ff; color: #084298; }
                """)
            }
        }
    }
    body {
        h1 { +"Valp - Simulator" }

        div(classes = "section") {
            h2 { +"Gjennomføringer (${gjennomforings.size})" }
            if (gjennomforings.isEmpty()) {
                div(classes = "empty") {
                    +"No gjennomføringer in database"
                }
            } else {
                table {
                    thead {
                        tr {
                            th { +"Type" }
                            th { +"Tiltakskode" }
                            th { +"Arrangor" }
                            th { +"Status" }
                            th { +"Navn" }
                            th { +"Start Dato" }
                            th { +"Slutt Dato" }
                            th { +"ID" }
                        }
                    }
                    tbody {
                        gjennomforings.forEach { row ->
                            tr {
                                td {
                                    span(classes = "type-badge type-${row.type}") {
                                        +row.type
                                    }
                                }
                                td { +row.tiltakskode }
                                td { +row.arrangor }
                                td { +row.status }
                                td { +(row.navn ?: "-") }
                                td { +(row.startDato ?: "-") }
                                td { +(row.sluttDato ?: "-") }
                                td {
                                    span(classes = "id") {
                                        +row.id
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        div(classes = "section") {
            h2 { +"Tiltakstyper (${tiltakstyper.size})" }
            if (tiltakstyper.isEmpty()) {
                div(classes = "empty") {
                    +"No tiltakstyper in database"
                }
            } else {
                table {
                    thead {
                        tr {
                            th { +"Navn" }
                            th { +"Tiltakskode" }
                            th { +"Innsatsgrupper" }
                            th { +"ID" }
                        }
                    }
                    tbody {
                        tiltakstyper.forEach { row ->
                            tr {
                                td { +row.navn }
                                td { +row.tiltakskode }
                                td {
                                    span(classes = "id") {
                                        +row.innsatsgrupper
                                    }
                                }
                                td {
                                    span(classes = "id") {
                                        +row.id
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        hr {}
        footer {
            p {
                small {
                    +"Valp Simulator - In development"
                }
            }
        }
    }
}


