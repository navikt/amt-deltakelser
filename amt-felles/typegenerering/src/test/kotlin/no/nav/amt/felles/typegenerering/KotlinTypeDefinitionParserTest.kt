package no.nav.amt.felles.typegenerering

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KotlinTypeDefinitionParserTest {
    data class Child(
        val name: String,
    )

    enum class Status {
        ACTIVE,
        INACTIVE,
    }

    sealed interface Decision {
        object Approved : Decision

        data class Rejected(
            val reason: String,
        ) : Decision
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface AnnotatedDecision {
        object Approved : AnnotatedDecision

        data class Rejected(
            val reason: String,
        ) : AnnotatedDecision
    }

    data class Example(
        val id: Long,
        val optionalName: String?,
        val tags: List<String>,
        val metadata: Map<String, Int?>,
        val child: Child,
        val status: Status,
        val decision: Decision,
        val children: Set<Child>,
    )

    data class AnnotatedExample(
        val decision: AnnotatedDecision,
    )

    data class RecursiveNode(
        val value: String,
        val next: RecursiveNode?,
    )

    data class Parent(
        val child: Child,
    ) {
        data class Child(
            val value: String,
        )
    }

    @Test
    fun `parse skal beskrive navn type nullability og generics`() {
        val definition = KotlinTypeDefinitionParser.parse(Example::class)

        definition.kClass shouldBe Example::class
        definition.typeName shouldBe "KotlinTypeDefinitionParserTest_Example"
        definition.fields.map { it.name } shouldContainExactly listOf(
            "child",
            "children",
            "decision",
            "id",
            "metadata",
            "optionalName",
            "status",
            "tags",
        )

        val id = definition.field("id").type
        id.kind shouldBe TypeKind.PRIMITIVE
        id.kClass shouldBe Long::class
        id.nullable shouldBe false
        id.genericArguments shouldContainExactly emptyList()

        val optionalName = definition.field("optionalName").type
        optionalName.kind shouldBe TypeKind.PRIMITIVE
        optionalName.kClass shouldBe String::class
        optionalName.nullable shouldBe true

        val tags = definition.field("tags").type
        tags.kind shouldBe TypeKind.COLLECTION
        tags.kClass shouldBe List::class
        tags.genericArguments.size shouldBe 1
        tags.genericArguments[0].kClass shouldBe String::class
        tags.genericArguments[0].nullable shouldBe false

        val metadata = definition.field("metadata").type
        metadata.kind shouldBe TypeKind.MAP
        metadata.kClass shouldBe Map::class
        metadata.genericArguments.size shouldBe 2
        metadata.genericArguments[0].kClass shouldBe String::class
        metadata.genericArguments[0].nullable shouldBe false
        metadata.genericArguments[1].kClass shouldBe Int::class
        metadata.genericArguments[1].nullable shouldBe true

        val child = definition.field("child").type
        child.kind shouldBe TypeKind.CLASS
        child.kClass shouldBe Child::class
        child.typeName shouldBe "KotlinTypeDefinitionParserTest_Child"
        child.nullable shouldBe false

        val status = definition.field("status").type
        status.kind shouldBe TypeKind.ENUM
        status.kClass shouldBe Status::class

        val decision = definition.field("decision").type
        decision.kind shouldBe TypeKind.SEALED
        decision.kClass shouldBe Decision::class
        decision.sealedSubclasses shouldContainExactly listOf(
            Decision.Approved::class,
            Decision.Rejected::class,
        )
    }

    @Test
    fun `parse skal representere rekursive typer uten output-type referanser`() {
        val definition = KotlinTypeDefinitionParser.parse(RecursiveNode::class)

        val next = definition.field("next").type
        next.kind shouldBe TypeKind.CLASS
        next.kClass shouldBe RecursiveNode::class
        next.typeName shouldBe "KotlinTypeDefinitionParserTest_RecursiveNode"
        next.nullable shouldBe true
        next.genericArguments shouldContainExactly emptyList()
    }

    @Test
    fun `parse skal inkludere parent-navn for nested classes`() {
        val definition = KotlinTypeDefinitionParser.parse(Parent::class)

        definition.typeName shouldBe "KotlinTypeDefinitionParserTest_Parent"
        definition.field("child").type.typeName shouldBe "KotlinTypeDefinitionParserTest_Parent_Child"
    }

    @Test
    fun `parse skal inkludere class-level annotation metadata`() {
        val definition = KotlinTypeDefinitionParser.parse(AnnotatedExample::class)

        val decision = definition.field("decision").type
        val jsonTypeInfo = decision.annotations.single()
        jsonTypeInfo.qualifiedName shouldBe "com.fasterxml.jackson.annotation.JsonTypeInfo"
        jsonTypeInfo.simpleName shouldBe "JsonTypeInfo"
        jsonTypeInfo.values["include"] shouldBe "PROPERTY"
        jsonTypeInfo.values["property"] shouldBe "type"
        jsonTypeInfo.values["use"] shouldBe "SIMPLE_NAME"
    }

    private fun TypeDefinition.field(name: String): FieldDefinition = fields.first { it.name == name }
}
