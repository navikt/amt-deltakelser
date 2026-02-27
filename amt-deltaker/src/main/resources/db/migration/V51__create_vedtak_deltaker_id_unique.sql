DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE
                table_name='vedtak'
                AND constraint_name='vedtak_deltaker_id_unique'
        ) THEN
            ALTER TABLE vedtak ADD CONSTRAINT vedtak_deltaker_id_unique UNIQUE (deltaker_id);
        END IF;
    END$$;

DROP INDEX IF EXISTS vedtak_deltaker_id_idx;