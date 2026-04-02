package no.nav.amt.distribusjon.utils

import no.nav.amt.lib.testing.utils.ClientTestUtils.mockAzureAdClient

abstract class ClientTestBase {
    protected val mockAzureAdTokenClient = mockAzureAdClient()
}
