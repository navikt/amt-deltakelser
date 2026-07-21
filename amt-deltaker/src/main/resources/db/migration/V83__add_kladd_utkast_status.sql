ALTER TABLE enkeltplass_prisinformasjon
    DROP CONSTRAINT enkeltplass_prisinformasjon_status_check,
    ADD CONSTRAINT enkeltplass_prisinformasjon_status_check
        CHECK (status IN ('KLADD_UTKAST', 'SENDT', 'RETURNERT', 'TIL_BEHANDLING', 'SATT_PA_VENT', 'GODKJENT')),
    ALTER COLUMN status SET DEFAULT 'KLADD_UTKAST';
