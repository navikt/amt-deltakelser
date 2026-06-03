package tjenester.nav.nom

import DbOperations
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import db.NomRessurs
import db.VeilarboppfolgingPerson
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime
import java.time.ZoneOffset

data class NomRessursRow(
    val navident: String,
    val personident: String,
    val visningsnavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val primaryTelefon: String?,
    val telefon: List<NomTelefonFixture>,
    val orgTilknytning: List<NomOrgTilknytningFixture>,
)

data class NomRessursFormDefaults(
    val navident: String,
    val personident: String,
    val visningsnavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val primaryTelefon: String,
    val telefonJson: String,
    val orgTilknytningJson: String,
)

data class NomRessursFormInput(
    val navident: String,
    val personident: String,
    val visningsnavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val primaryTelefon: String?,
    val telefon: List<NomTelefonFixture>,
    val orgTilknytning: List<NomOrgTilknytningFixture>,
)

data class NomVeilederOption(
    val navident: String,
    val label: String,
)

data class NomPersonidentOption(
    val personident: String,
    val label: String,
)

data class NomRessursFixture(
    val navident: String,
    val personident: String,
    val visningsnavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val primaryTelefon: String?,
    val telefon: List<NomTelefonFixture>,
    val orgTilknytning: List<NomOrgTilknytningFixture>,
) {
    // Deprecated aliases still present in Nom GraphQL schema.
    val navIdent: String = navident
    val personIdent: String = personident
    val visningsNavn: String = visningsnavn
}

data class NomTelefonFixture(
    val nummer: String,
    val type: String,
)

data class NomOrgTilknytningFixture(
    val gyldigFom: String,
    val gyldigTom: String?,
    val orgEnhet: NomOrgEnhetFixture,
    val erDagligOppfolging: Boolean,
)

data class NomOrgEnhetFixture(
    val remedyEnhetId: String?,
)

private val nomJsonMapper = jacksonObjectMapper().findAndRegisterModules()

fun defaultNomRessursFormDefaults(): NomRessursFormDefaults {
    return NomRessursFormDefaults(
        navident = "",
        personident = "",
        visningsnavn = "",
        fornavn = "",
        etternavn = "",
        epost = "",
        primaryTelefon = "",
        telefonJson = "[]",
        orgTilknytningJson = "[]",
    )
}

fun fetchNomRessurser(): List<NomRessursRow> {
    return DbOperations.inTransaction {
        NomRessurs.selectAll()
            .map { it.toRessursRow() }
            .sortedBy { it.navident }
    }
}

fun fetchNomRessursByNavident(navident: String): NomRessursRow? {
    return DbOperations.inTransaction {
        NomRessurs.selectAll()
            .firstOrNull { it[NomRessurs.navident] == navident }
            ?.toRessursRow()
    }
}

fun insertNomRessurs(form: NomRessursFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        NomRessurs.insert {
            it[navident] = form.navident
            it[personident] = form.personident
            it[visningsnavn] = form.visningsnavn
            it[fornavn] = form.fornavn
            it[etternavn] = form.etternavn
            it[epost] = form.epost
            it[primaryTelefon] = form.primaryTelefon
            it[telefon] = nomJsonMapper.writeValueAsString(form.telefon)
            it[orgTilknytning] = nomJsonMapper.writeValueAsString(form.orgTilknytning)
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun updateNomRessurs(form: NomRessursFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        NomRessurs.update({ NomRessurs.navident eq form.navident }) {
            it[personident] = form.personident
            it[visningsnavn] = form.visningsnavn
            it[fornavn] = form.fornavn
            it[etternavn] = form.etternavn
            it[epost] = form.epost
            it[primaryTelefon] = form.primaryTelefon
            it[telefon] = nomJsonMapper.writeValueAsString(form.telefon)
            it[orgTilknytning] = nomJsonMapper.writeValueAsString(form.orgTilknytning)
            it[updatedAt] = now
        }
    }

    return updatedRows > 0
}

fun deleteNomRessurs(navident: String): Boolean {
    val deletedRows = DbOperations.inTransaction {
        NomRessurs.deleteWhere { NomRessurs.navident eq navident }
    }

    return deletedRows > 0
}

fun isNomRessursUsedByVeilarboppfolging(navident: String): Boolean {
    return DbOperations.inTransaction {
        VeilarboppfolgingPerson.selectAll().any { it[VeilarboppfolgingPerson.veilederIdent] == navident }
    }
}

fun fetchNomVeilederOptions(): List<NomVeilederOption> {
    return fetchNomRessurser().map { row ->
        NomVeilederOption(
            navident = row.navident,
            label = "${row.navident} - ${row.visningsnavn}",
        )
    }
}

fun fetchNomPersonidentOptions(): List<NomPersonidentOption> {
    return fetchNomRessurser().map { row ->
        NomPersonidentOption(
            personident = row.personident,
            label = "${row.personident} - ${row.visningsnavn}",
        )
    }
}

fun NomRessursRow.toRessursFixture(): NomRessursFixture {
    return NomRessursFixture(
        navident = navident,
        personident = personident,
        visningsnavn = visningsnavn,
        fornavn = fornavn,
        etternavn = etternavn,
        epost = epost,
        primaryTelefon = primaryTelefon,
        telefon = telefon,
        orgTilknytning = orgTilknytning,
    )
}

fun NomRessursRow.toFormDefaults(): NomRessursFormDefaults {
    return NomRessursFormDefaults(
        navident = navident,
        personident = personident,
        visningsnavn = visningsnavn,
        fornavn = fornavn,
        etternavn = etternavn,
        epost = epost,
        primaryTelefon = primaryTelefon.orEmpty(),
        telefonJson = nomJsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(telefon),
        orgTilknytningJson = nomJsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(orgTilknytning),
    )
}

fun parseNomTelefonJson(value: String): List<NomTelefonFixture> = nomJsonMapper.readValue(value)

fun parseNomOrgTilknytningJson(value: String): List<NomOrgTilknytningFixture> = nomJsonMapper.readValue(value)

fun nextNavident(): String {
    val existing = DbOperations.inTransaction {
        NomRessurs.selectAll().map { it[NomRessurs.navident] }
    }
    val maxNumber = existing
        .mapNotNull { Regex("[A-Z](\\d+)").matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
        .maxOrNull() ?: 0L
    return "Z%06d".format(maxNumber + 1)
}

private fun ResultRow.toRessursRow(): NomRessursRow {
    return NomRessursRow(
        navident = this[NomRessurs.navident],
        personident = this[NomRessurs.personident],
        visningsnavn = this[NomRessurs.visningsnavn],
        fornavn = this[NomRessurs.fornavn],
        etternavn = this[NomRessurs.etternavn],
        epost = this[NomRessurs.epost],
        primaryTelefon = this[NomRessurs.primaryTelefon],
        telefon = nomJsonMapper.readValue(this[NomRessurs.telefon]),
        orgTilknytning = nomJsonMapper.readValue(this[NomRessurs.orgTilknytning]),
    )
}

