package no.nav.amt.distribusjon.utils

import no.nav.amt.lib.utils.objectMapper
import org.postgresql.util.PGobject

object DbUtils {
    fun toPGObject(value: Any?) = PGobject().also {
        it.type = "json"
        it.value = value?.let { v -> objectMapper.writeValueAsString(v) }
    }
}
