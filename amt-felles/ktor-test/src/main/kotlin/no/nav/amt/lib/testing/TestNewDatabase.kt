package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.NewDatabase

/**
 * Delt [NewDatabase]-instans for tester som bruker det nye JDBI-baserte grensesnittet
 * ([no.nav.amt.lib.utils.database.DatabaseApi]) i stedet for det globale, Kotliquery-baserte
 * [no.nav.amt.lib.utils.database.Database]-singletonet.
 *
 * Kobler seg til samme Postgres-testcontainer som [TestPostgresContainer], og opprettes
 * kun én gang per JVM slik at testklasser i samme modul deler samme datakilde.
 */
object TestNewDatabase {
    val instance: NewDatabase by lazy {
        TestPostgresContainer.bootstrap()
        NewDatabase(TestPostgresContainer.databaseConfig())
    }
}

