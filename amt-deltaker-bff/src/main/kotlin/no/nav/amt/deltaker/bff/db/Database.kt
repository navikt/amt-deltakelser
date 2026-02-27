package no.nav.amt.deltaker.bff.db

import no.nav.amt.deltaker.bff.application.plugins.writePolymorphicListAsString
import no.nav.amt.lib.utils.objectMapper
import org.postgresql.util.PGobject

inline fun <reified T> toPGObject(value: T?) = PGobject().also {
    it.type = "json"
    it.value = value?.let { v -> objectMapper.writePolymorphicListAsString(v) }
}
