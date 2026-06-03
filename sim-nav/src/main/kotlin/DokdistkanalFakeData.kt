import DbOperations
import db.DokdistkanalPerson
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class DokdistkanalDistribusjonskanal {
    PRINT,
    SDP,
    DITT_NAV,
    LOKAL_PRINT,
    INGEN_DISTRIBUSJON,
    TRYGDERETTEN,
    DPVT,
}

data class DokdistkanalPersonRow(
    val personident: String,
    val distribusjonskanal: DokdistkanalDistribusjonskanal,
)

data class DokdistkanalPersonFormDefaults(
    val personident: String,
    val distribusjonskanal: DokdistkanalDistribusjonskanal,
)

data class DokdistkanalPersonFormInput(
    val personident: String,
    val distribusjonskanal: DokdistkanalDistribusjonskanal,
)

fun fetchDokdistkanalPersons(): List<DokdistkanalPersonRow> {
    return DbOperations.inTransaction {
        DokdistkanalPerson.selectAll()
            .map { it.toPersonRow() }
            .sortedBy { it.personident }
    }
}

fun fetchDokdistkanalPersonByPersonident(personident: String): DokdistkanalPersonRow? {
    return DbOperations.inTransaction {
        DokdistkanalPerson.selectAll()
            .firstOrNull { it[DokdistkanalPerson.personident] == personident }
            ?.toPersonRow()
    }
}

fun insertDokdistkanalPerson(form: DokdistkanalPersonFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        DokdistkanalPerson.insert {
            it[personident] = form.personident
            it[distribusjonskanal] = form.distribusjonskanal.name
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun updateDokdistkanalPerson(form: DokdistkanalPersonFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        DokdistkanalPerson.update({ DokdistkanalPerson.personident eq form.personident }) {
            it[distribusjonskanal] = form.distribusjonskanal.name
            it[updatedAt] = now
        }
    }
    return updatedRows > 0
}

fun deleteDokdistkanalPerson(personident: String): Boolean {
    val deletedRows = DbOperations.inTransaction {
        DokdistkanalPerson.deleteWhere { DokdistkanalPerson.personident eq personident }
    }
    return deletedRows > 0
}

fun DokdistkanalPersonRow.toFormDefaults(): DokdistkanalPersonFormDefaults {
    return DokdistkanalPersonFormDefaults(
        personident = personident,
        distribusjonskanal = distribusjonskanal,
    )
}

private fun ResultRow.toPersonRow(): DokdistkanalPersonRow {
    return DokdistkanalPersonRow(
        personident = this[DokdistkanalPerson.personident],
        distribusjonskanal = enumValueOf(this[DokdistkanalPerson.distribusjonskanal]),
    )
}

