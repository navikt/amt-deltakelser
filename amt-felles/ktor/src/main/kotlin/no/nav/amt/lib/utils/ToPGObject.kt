package no.nav.amt.lib.utils

import org.postgresql.util.PGobject

inline fun <reified T> toPGObject(value: T?) = PGobject().also {
    it.type = "json"
    it.value = value?.let { v -> objectMapper.writePolymorphicListAsString(v) }
}
