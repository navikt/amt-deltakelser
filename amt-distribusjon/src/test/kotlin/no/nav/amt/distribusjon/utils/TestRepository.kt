package no.nav.amt.distribusjon.utils

import kotliquery.queryOf
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.utils.DbUtils.toPGObject
import no.nav.amt.lib.utils.database.Database

object TestRepository {
    fun insertHendelse(hendelse: Hendelse) {
        val sql =
            """
            INSERT INTO hendelse (
                id, 
                deltaker_id, 
                deltaker, 
                ansvarlig, 
                payload, 
                distribusjonskanal, 
                manuelloppfolging, 
                created_at
            )
            VALUES (
                :id, 
                :deltaker_id, 
                :deltaker, 
                :ansvarlig, 
                :payload, 
                :distribusjonskanal, 
                :manuelloppfolging, 
                :created_at
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()

        val params = mapOf(
            "id" to hendelse.id,
            "deltaker_id" to hendelse.deltaker.id,
            "deltaker" to toPGObject(hendelse.deltaker),
            "ansvarlig" to toPGObject(hendelse.ansvarlig),
            "payload" to toPGObject(hendelse.payload),
            "distribusjonskanal" to hendelse.distribusjonskanal.name,
            "manuelloppfolging" to hendelse.manuellOppfolging,
            "created_at" to hendelse.opprettet,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }
}
