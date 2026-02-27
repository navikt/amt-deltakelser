package no.nav.amt.distribusjon.utils

import no.nav.amt.distribusjon.testEnvironment

abstract class ClientTestBase {
    protected val mockAzureAdTokenClient = mockAzureAdClient(testEnvironment)
}
