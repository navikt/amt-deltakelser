package no.nav.amt.pdfgen.util

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.pdfgen.util.RenderUtils.toNorwegianShortDate
import java.time.LocalDate

object AssertUtils {
    fun String.assertSectionText(
        pattern: String,
        value: Any?,
    ) {
        if (value != null) {
            if (value is LocalDate) {
                this shouldContain "$pattern ${value.toNorwegianShortDate()}"
            } else {
                this shouldContain "$pattern $value"
            }
        } else {
            this shouldNotContain pattern
        }
    }
}
