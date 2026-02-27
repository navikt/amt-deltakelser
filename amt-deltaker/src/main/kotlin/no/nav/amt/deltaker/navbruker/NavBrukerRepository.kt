package no.nav.amt.deltaker.navbruker

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class NavBrukerRepository {
    fun upsert(bruker: NavBruker) = runCatching {
        val sql =
            """
            INSERT INTO nav_bruker (
                person_id, 
                personident, 
                fornavn, 
                mellomnavn, 
                etternavn, 
                nav_veileder_id, 
                nav_enhet_id, 
                telefonnummer, 
                epost, 
                er_skjermet, 
                adresse, 
                adressebeskyttelse, 
                oppfolgingsperioder, 
                innsatsgruppe
            ) 
            VALUES (
                :person_id, 
                :personident, 
                :fornavn, 
                :mellomnavn, 
                :etternavn, 
                :nav_veileder_id, 
                :nav_enhet_id, 
                :telefonnummer, 
                :epost, 
                :er_skjermet, 
                :adresse, 
                :adressebeskyttelse, 
                :oppfolgingsperioder, 
                :innsatsgruppe
            )
            ON CONFLICT (person_id) DO UPDATE SET
                personident = :personident,
                fornavn = :fornavn,
                mellomnavn = :mellomnavn,
                etternavn = :etternavn,
                nav_veileder_id = :nav_veileder_id,
                nav_enhet_id = :nav_enhet_id,
                telefonnummer = :telefonnummer,
                epost = :epost,
                er_skjermet = :er_skjermet,
                adresse = :adresse,
                adressebeskyttelse = :adressebeskyttelse,
                oppfolgingsperioder = :oppfolgingsperioder,
                innsatsgruppe = :innsatsgruppe,
                modified_at = CURRENT_TIMESTAMP
            RETURNING *
            """.trimIndent()

        val params = mapOf(
            "person_id" to bruker.personId,
            "personident" to bruker.personident,
            "fornavn" to bruker.fornavn,
            "mellomnavn" to bruker.mellomnavn,
            "etternavn" to bruker.etternavn,
            "nav_veileder_id" to bruker.navVeilederId,
            "nav_enhet_id" to bruker.navEnhetId,
            "telefonnummer" to bruker.telefon,
            "epost" to bruker.epost,
            "er_skjermet" to bruker.erSkjermet,
            "adresse" to toPGObject(bruker.adresse),
            "adressebeskyttelse" to bruker.adressebeskyttelse?.name,
            "oppfolgingsperioder" to toPGObject(bruker.oppfolgingsperioder),
            "innsatsgruppe" to bruker.innsatsgruppe?.name,
        )

        Database.query { session ->
            session.run(queryOf(sql, params).map(::rowMapper).asSingle)
                ?: throw NoSuchElementException("Noe gikk galt med upsert av bruker ${bruker.personId}")
        }
    }

    fun get(personId: UUID): Result<NavBruker> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    statement = "SELECT * FROM nav_bruker WHERE person_id = :person_id",
                    paramMap = mapOf("person_id" to personId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke bruker $personId")
        }
    }

    fun get(personident: String): Result<NavBruker> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    statement = "SELECT * FROM nav_bruker WHERE personident = :personident",
                    paramMap = mapOf("personident" to personident),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke bruker med personident")
        }
    }

    companion object {
        private fun rowMapper(row: Row) = NavBruker(
            personId = row.uuid("person_id"),
            personident = row.string("personident"),
            fornavn = row.string("fornavn"),
            mellomnavn = row.stringOrNull("mellomnavn"),
            etternavn = row.string("etternavn"),
            navVeilederId = row.uuidOrNull("nav_veileder_id"),
            navEnhetId = row.uuidOrNull("nav_enhet_id"),
            telefon = row.stringOrNull("telefonnummer"),
            epost = row.stringOrNull("epost"),
            erSkjermet = row.boolean("er_skjermet"),
            adresse = row.stringOrNull("adresse")?.let { objectMapper.readValue(it) },
            adressebeskyttelse = row.stringOrNull("adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
            oppfolgingsperioder = row.stringOrNull("oppfolgingsperioder")?.let { objectMapper.readValue(it) } ?: emptyList(),
            innsatsgruppe = row.stringOrNull("innsatsgruppe")?.let { Innsatsgruppe.valueOf(it) },
        )
    }
}
