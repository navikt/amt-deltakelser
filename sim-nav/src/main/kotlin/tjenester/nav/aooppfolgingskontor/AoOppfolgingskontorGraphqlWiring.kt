package tjenester.nav.aooppfolgingskontor

import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser

private const val AO_OPPFOLGINGSKONTOR_SCHEMA_RESOURCE = "/ao-oppfolgingskontor/ao-oppfolgingskontor.graphqls"

fun createAoOppfolgingskontorGraphql(kontorTilhorigheterDataFetcher: DataFetcher<Any?>): GraphQL {
    val executableSchema = createAoOppfolgingskontorExecutableSchema(kontorTilhorigheterDataFetcher)
    return GraphQL.newGraphQL(executableSchema).build()
}

fun createAoOppfolgingskontorExecutableSchema(kontorTilhorigheterDataFetcher: DataFetcher<Any?>): GraphQLSchema {
    val typeDefinitionRegistry = loadAoOppfolgingskontorTypeDefinitions()

    val runtimeWiring = RuntimeWiring.newRuntimeWiring()
        .type("Query") { typeWiring ->
            typeWiring.dataFetcher("kontorTilhorigheter", kontorTilhorigheterDataFetcher)
        }
        .build()

    return SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
}

private fun loadAoOppfolgingskontorTypeDefinitions() =
    SchemaParser().parse(loadResource(AO_OPPFOLGINGSKONTOR_SCHEMA_RESOURCE))

private fun loadResource(resourcePath: String): String =
    object {}.javaClass.getResourceAsStream(resourcePath)?.use { stream ->
        stream.reader().readText()
    } ?: error("Missing schema resource: $resourcePath")



