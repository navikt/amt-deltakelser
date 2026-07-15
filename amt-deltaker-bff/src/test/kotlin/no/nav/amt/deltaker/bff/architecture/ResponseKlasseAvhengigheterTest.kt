package no.nav.amt.deltaker.bff.architecture

import com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import org.junit.jupiter.api.Test

class ResponseKlasseAvhengigheterTest {
    @Test
    fun `Response-klasser skal kun ha tillatte felttyper`() {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("no.nav.amt.deltaker.bff")

        fields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage(*responsePakker)
            .should()
            .haveRawType(
                // Tillat bruk av typer utenfra vår applikasjon (typisk Java/Kotlin standardbibliotek, som String og LocalDate)
                resideOutsideOfPackage("no.nav.amt..")
                    // Tillat bruk av andre response-typer
                    .or(resideInAnyPackage(*responsePakker))
                    // Tillat enum-typer: verdiene endres sjelden og egne enum-definisjoner for BFF tar mye plass
                    .or(assignableTo(Enum::class.java)),
                // Men ikke tillat bruk av f.eks. Dbo- eller Model-klasser
            )
            .andShould(haveOnlyAllowedGenericTypeArguments(responsePakker))
            .check(importedClasses)
    }
}
