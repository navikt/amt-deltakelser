CREATE INDEX CONCURRENTLY deltaker_personident_mod_24_idx
    ON deltaker (MOD(personident::bigint, 24));