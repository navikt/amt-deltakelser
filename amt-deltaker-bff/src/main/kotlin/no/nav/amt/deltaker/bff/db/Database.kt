package no.nav.amt.deltaker.bff.db

import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.writePolymorphicListAsString
import org.postgresql.util.PGobject

inline fun <reified T> toPGObject(value: T?) = PGobject().also {
    it.type = "json"
    it.value = value?.let { v -> objectMapper.writePolymorphicListAsString(v) }
}
