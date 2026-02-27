package no.nav.amt.lib.utils.database

import kotliquery.queryOf

class DatabaseTestRepository {
    init {
        Database.query { session ->
            session.update(queryOf("CREATE TABLE IF NOT EXISTS foo (id INT PRIMARY KEY)"))
        }
    }

    fun cleanUp() = Database.query { session ->
        session.update(queryOf("TRUNCATE TABLE foo"))
    }

    fun insert(n: Int) = Database.query { session ->
        session.update(queryOf("INSERT INTO foo (id) VALUES (?)", n))
    }

    fun get(n: Int) = Database.query { session ->
        val queryAction = queryOf("SELECT * FROM foo WHERE id = ?", n)
            .map { r -> r.int("id") }
            .asSingle

        session.run(queryAction)
    }
}
