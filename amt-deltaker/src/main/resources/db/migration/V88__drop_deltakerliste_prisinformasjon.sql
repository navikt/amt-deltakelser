-- Fjerner den ubrukte fritekst-kolonnen deltakerliste.prisinformasjon (varchar).
-- Feltet ble aldri enkeltplass-prisinformasjon, og ble fjernet fra inngående Kafka-payload (#371),
-- slik at det alltid ble skrevet som null. Ingen aktiv kode avhenger lenger av verdien.
ALTER TABLE deltakerliste
    DROP COLUMN prisinformasjon;
