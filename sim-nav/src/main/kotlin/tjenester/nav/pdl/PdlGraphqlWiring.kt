package tjenester.nav.pdl

import graphql.GraphQL
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser

private const val PDL_SCHEMA_RESOURCE = "/pdl/pdl.graphqls"

/**
 * Minimal schema-first GraphQL-java setup for PDL.
 *
 * Current data fetchers are placeholders and will be replaced when the new PdlFake implementation lands.
 */
fun createPdlGraphql(): GraphQL {
    val executableSchema = createPdlExecutableSchema()
    return GraphQL.newGraphQL(executableSchema).build()
}

fun createPdlGraphql(
    hentPersonDataFetcher: DataFetcher<Any?>,
    hentIdenterDataFetcher: DataFetcher<Any?>,
): GraphQL {
    val executableSchema = createPdlExecutableSchema(
        hentPersonDataFetcher = hentPersonDataFetcher,
        hentIdenterDataFetcher = hentIdenterDataFetcher,
    )
    return GraphQL.newGraphQL(executableSchema).build()
}

fun createPdlExecutableSchema(): GraphQLSchema = createPdlExecutableSchema(
    hentPersonDataFetcher = DataFetcher { null },
    hentIdenterDataFetcher = DataFetcher { null },
)

fun createPdlExecutableSchema(
    hentPersonDataFetcher: DataFetcher<Any?>,
    hentIdenterDataFetcher: DataFetcher<Any?>,
): GraphQLSchema {
    val typeDefinitionRegistry = loadPdlTypeDefinitions()

    val runtimeWiring = RuntimeWiring.newRuntimeWiring()
        .scalar(ExtendedScalars.Date)
        .scalar(ExtendedScalars.DateTime)
        .scalar(ExtendedScalars.GraphQLLong)
        .type("Query") { typeWiring ->
            typeWiring
                .dataFetcher("hentPerson", hentPersonDataFetcher)
                .dataFetcher("hentIdenter", hentIdenterDataFetcher)
        }
        .build()

    return SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
}

private fun loadPdlTypeDefinitions() =
    object {}.javaClass.getResourceAsStream(PDL_SCHEMA_RESOURCE)?.use { stream ->
        SchemaParser().parse(stream.reader())
    } ?: error("Missing schema resource: $PDL_SCHEMA_RESOURCE")




