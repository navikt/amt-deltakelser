package no.nav.amt.felles.typegenerering

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.felles.typegenerering.collisions.one.DeltakerlisteResponse as FirstDeltakerlisteResponse
import no.nav.amt.felles.typegenerering.collisions.two.DeltakerlisteResponse as SecondDeltakerlisteResponse
import org.junit.jupiter.api.Test

class TypeDefinitionToZodTest {
    enum class Status {
        ACTIVE,
        INACTIVE,
    }

    data class SupportedExample(
        val id: Long,
        val optionalName: String?,
        val tags: List<String>,
        val metadata: Map<String, Int?>,
        val status: Status,
    )

    data class Nested(
        val name: String,
    )

    data class HasCustomType(
        val nested: Nested,
    )

    sealed interface Decision {
        object Approved : Decision

        data class Rejected(
            val reason: String,
        ) : Decision
    }

    data class HasSealedType(
        val decision: Decision,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface AnnotatedDecision {
        object Approved : AnnotatedDecision

        data class Rejected(
            val reason: String,
        ) : AnnotatedDecision
    }

    data class HasAnnotatedSealedType(
        val decision: AnnotatedDecision,
    )

    data class RecursiveNode(
        val next: RecursiveNode?,
    )

    data class HasUnsupportedMapKey(
        val metadata: Map<Int, String>,
    )

    @Test
    fun `toZodExpression skal generere zod-uttrykk for støttede typer`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(SupportedExample::class)
        val typeDefinition = typeDefinitions.requireDefinition(SupportedExample::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions, prettyPrint = false)

        result shouldBe
            "/** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.SupportedExample */\nconst typeDefinitionToZodTest_SupportedExampleSchema = z.object({id: z.number(), metadata: z.record(z.number().nullable()), optionalName: z.string().nullable(), status: z.enum([\"ACTIVE\", \"INACTIVE\"]), tags: z.array(z.string())})"
    }

    @Test
    fun `toZodExpression skal prettyprinte som default`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(SupportedExample::class)
        val typeDefinition = typeDefinitions.requireDefinition(SupportedExample::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions)

        result shouldBe
            """
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.SupportedExample */
            const typeDefinitionToZodTest_SupportedExampleSchema = z.object({
              id: z.number(),
              metadata: z.record(z.number().nullable()),
              optionalName: z.string().nullable(),
              status: z.enum(["ACTIVE", "INACTIVE"]),
              tags: z.array(z.string())
            })
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal referere custom klasse-typer ved navn`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(HasCustomType::class)
        val typeDefinition = typeDefinitions.requireDefinition(HasCustomType::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions, prettyPrint = false)
        result shouldBe
            """
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.Nested */
            const typeDefinitionToZodTest_NestedSchema = z.object({name: z.string()})
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.HasCustomType */
            const typeDefinitionToZodTest_HasCustomTypeSchema = z.object({nested: typeDefinitionToZodTest_NestedSchema})
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal støtte sealed typer via navngitt union schema`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(HasSealedType::class)
        val typeDefinition = typeDefinitions.requireDefinition(HasSealedType::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions, prettyPrint = false)
        result shouldBe
            """
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.Decision.Approved */
            const typeDefinitionToZodTest_Decision_ApprovedSchema = z.object({})
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.Decision.Rejected */
            const typeDefinitionToZodTest_Decision_RejectedSchema = z.object({reason: z.string()})
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.Decision */
            const typeDefinitionToZodTest_DecisionSchema = z.union([typeDefinitionToZodTest_Decision_ApprovedSchema, typeDefinitionToZodTest_Decision_RejectedSchema])
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.HasSealedType */
            const typeDefinitionToZodTest_HasSealedTypeSchema = z.object({decision: typeDefinitionToZodTest_DecisionSchema})
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal bruke discriminated union for JsonTypeInfo-annotert sealed type`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(HasAnnotatedSealedType::class)
        val typeDefinition = typeDefinitions.requireDefinition(HasAnnotatedSealedType::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions, prettyPrint = false)
        result shouldBe
            """
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.AnnotatedDecision.Approved */
            const typeDefinitionToZodTest_AnnotatedDecision_ApprovedSchema = z.object({type: z.literal("Approved")})
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.AnnotatedDecision.Rejected */
            const typeDefinitionToZodTest_AnnotatedDecision_RejectedSchema = z.object({type: z.literal("Rejected"), reason: z.string()})
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.AnnotatedDecision */
            const typeDefinitionToZodTest_AnnotatedDecisionSchema = z.discriminatedUnion("type", [typeDefinitionToZodTest_AnnotatedDecision_ApprovedSchema, typeDefinitionToZodTest_AnnotatedDecision_RejectedSchema])
            /** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.HasAnnotatedSealedType */
            const typeDefinitionToZodTest_HasAnnotatedSealedTypeSchema = z.object({decision: typeDefinitionToZodTest_AnnotatedDecisionSchema})
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal feile for map med ikke-string nøkkel`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(HasUnsupportedMapKey::class)
        val typeDefinition = typeDefinitions.requireDefinition(HasUnsupportedMapKey::class)

        val exception = shouldThrow<IllegalArgumentException> {
            TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions)
        }

        exception.message shouldBe "Only non-null String map keys are supported: kotlin.collections.Map<kotlin.Int, kotlin.String>"
    }

    @Test
    fun `toZodExpression skal bruke z-lazy for rekursive custom typer`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(RecursiveNode::class)
        val typeDefinition = typeDefinitions.requireDefinition(RecursiveNode::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions, prettyPrint = false)
        result shouldBe
            "/** no.nav.amt.felles.typegenerering.TypeDefinitionToZodTest.RecursiveNode */\nconst typeDefinitionToZodTest_RecursiveNodeSchema = z.object({next: z.lazy(() => typeDefinitionToZodTest_RecursiveNodeSchema).nullable()})"
    }

    data class Parent(
        val child: Child,
    ) {
        data class Child(
            val value: String,
        )
    }

    @Test
    fun `toZodExpression skal bruke parent-navn for nested classes`() {
        val typeDefinitions = KotlinTypeDefinitionParser.parseRecursively(Parent::class)
        val typeDefinition = typeDefinitions.requireDefinition(Parent::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, typeDefinitions, prettyPrint = false)
        result shouldContain "const typeDefinitionToZodTest_Parent_ChildSchema = z.object({value: z.string()})"
        result shouldContain "const typeDefinitionToZodTest_ParentSchema = z.object({child: typeDefinitionToZodTest_Parent_ChildSchema})"
    }

    @Test
    fun `toZodExpressions skal bruke package-prefiks ved navnekollisjon`() {
        val definitions = KotlinTypeDefinitionParser.parseRecursively(
            listOf(
                FirstDeltakerlisteResponse::class,
                SecondDeltakerlisteResponse::class,
            ),
        )
        val result = TypeDefinitionToZod.toZodExpressions(
            definitions = definitions,
            prettyPrint = false,
        )

        result shouldContain "/** no.nav.amt.felles.typegenerering.collisions.one.DeltakerlisteResponse */"
        result shouldContain "const deltakerlisteResponseSchema = z.object({id: z.string()})"
        result shouldContain "/** no.nav.amt.felles.typegenerering.collisions.two.DeltakerlisteResponse */"
        result shouldContain "const no_nav_amt_felles_typegenerering_collisions_two_DeltakerlisteResponseSchema = z.object({navn: z.string()})"
        result shouldNotContain "deltakerlisteResponseSchema_2"
    }

    private fun List<TypeDefinition>.requireDefinition(kClass: kotlin.reflect.KClass<*>): TypeDefinition =
        first { it.kClass == kClass }
}
