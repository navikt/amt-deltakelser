-- Funn 1: innsokt og utkast_delt er timestamp without time zone, resten av skjemaet bruker timestamptz.
-- Verdiene er lagret i Europe/Oslo (JVM TZ=Europe/Oslo via Dockerfile).
ALTER TABLE innsok_paa_felles_oppstart
    ALTER COLUMN innsokt TYPE timestamptz USING innsokt AT TIME ZONE 'Europe/Oslo',
    ALTER COLUMN utkast_delt TYPE timestamptz USING utkast_delt AT TIME ZONE 'Europe/Oslo';

