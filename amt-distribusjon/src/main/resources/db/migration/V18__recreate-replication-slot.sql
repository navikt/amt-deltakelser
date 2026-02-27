DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1 FROM pg_replication_slots WHERE slot_name = 'amt_distribusjon_replication'
        ) THEN
            PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT('amt_distribusjon_replication', 'pgoutput');
        END IF;
    END;
$$;