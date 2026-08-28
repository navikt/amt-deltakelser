DO
$$
    DECLARE
        app_user name;
        table_record RECORD;
    BEGIN
        -- Nothing to do if no public tables are owned by cloudsqlsuperuser
        IF NOT EXISTS (
            SELECT 1
            FROM pg_tables
            WHERE schemaname = 'public'
              AND tableowner = 'cloudsqlsuperuser'
        ) THEN
            RAISE NOTICE 'No public tables owned by cloudsqlsuperuser. Nothing to do.';
            RETURN;
        END IF;

        -- Find the application user
        SELECT rolname
        INTO STRICT app_user
        FROM pg_roles
        WHERE rolname LIKE 'amt-aktivitetskort-publisher%'
          AND rolcanlogin;

        RAISE NOTICE 'Changing table owners to %', app_user;

        -- Change owner of all public tables owned by cloudsqlsuperuser
        FOR table_record IN
            SELECT schemaname, tablename
            FROM pg_tables
            WHERE schemaname = 'public'
              AND tableowner = 'cloudsqlsuperuser'
            LOOP
                RAISE NOTICE 'Changing owner of %.% to %',
                    table_record.schemaname,
                    table_record.tablename,
                    app_user;

                EXECUTE format(
                        'ALTER TABLE %I.%I OWNER TO %I',
                        table_record.schemaname,
                        table_record.tablename,
                        app_user
                        );
            END LOOP;
    END
$$;