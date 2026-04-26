-- Manglende FK constraints for interne uuid-kolonner.
-- Alle FK-er bruker NO ACTION (default) — ingen cascade-sletting.

-- arrangor.overordnet_arrangor_id → arrangor(id) (self-referencing)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'arrangor_overordnet_arrangor_id_fkey'
    ) THEN
        ALTER TABLE arrangor
            ADD CONSTRAINT arrangor_overordnet_arrangor_id_fkey
            FOREIGN KEY (overordnet_arrangor_id) REFERENCES arrangor(id);
    END IF;
END $$;

-- endring_fra_tiltakskoordinator.nav_ansatt_id → nav_ansatt(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'endring_fra_tiltakskoordinator_nav_ansatt_id_fkey'
    ) THEN
        ALTER TABLE endring_fra_tiltakskoordinator
            ADD CONSTRAINT endring_fra_tiltakskoordinator_nav_ansatt_id_fkey
            FOREIGN KEY (nav_ansatt_id) REFERENCES nav_ansatt(id);
    END IF;
END $$;

-- vedtak: opprettet_av/sist_endret_av → nav_ansatt, _enhet → nav_enhet
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'vedtak_opprettet_av_fkey'
    ) THEN
        ALTER TABLE vedtak
            ADD CONSTRAINT vedtak_opprettet_av_fkey
            FOREIGN KEY (opprettet_av) REFERENCES nav_ansatt(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'vedtak_opprettet_av_enhet_fkey'
    ) THEN
        ALTER TABLE vedtak
            ADD CONSTRAINT vedtak_opprettet_av_enhet_fkey
            FOREIGN KEY (opprettet_av_enhet) REFERENCES nav_enhet(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'vedtak_sist_endret_av_fkey'
    ) THEN
        ALTER TABLE vedtak
            ADD CONSTRAINT vedtak_sist_endret_av_fkey
            FOREIGN KEY (sist_endret_av) REFERENCES nav_ansatt(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'vedtak_sist_endret_av_enhet_fkey'
    ) THEN
        ALTER TABLE vedtak
            ADD CONSTRAINT vedtak_sist_endret_av_enhet_fkey
            FOREIGN KEY (sist_endret_av_enhet) REFERENCES nav_enhet(id);
    END IF;
END $$;

-- innsok_paa_felles_oppstart: innsokt_av → nav_ansatt, innsokt_av_enhet → nav_enhet
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'innsok_paa_felles_oppstart_innsokt_av_fkey'
    ) THEN
        ALTER TABLE innsok_paa_felles_oppstart
            ADD CONSTRAINT innsok_paa_felles_oppstart_innsokt_av_fkey
            FOREIGN KEY (innsokt_av) REFERENCES nav_ansatt(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'innsok_paa_felles_oppstart_innsokt_av_enhet_fkey'
    ) THEN
        ALTER TABLE innsok_paa_felles_oppstart
            ADD CONSTRAINT innsok_paa_felles_oppstart_innsokt_av_enhet_fkey
            FOREIGN KEY (innsokt_av_enhet) REFERENCES nav_enhet(id);
    END IF;
END $$;

