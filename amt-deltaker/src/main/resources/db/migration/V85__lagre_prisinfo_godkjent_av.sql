-- Vi trenger å lagre hvem som har godkjent prisinformasjon for å vise det i historikken.
-- Det er ikke alltid tilstrekkelig å bruke vedtak.fattet_av, siden prisinformasjon også kan endres etter at
-- vedtak først er fattet, og ikke nødvendigvis av den samme saksbehandleren.
ALTER TABLE enkeltplass_prisinformasjon
    ADD COLUMN godkjent_av       UUID REFERENCES nav_ansatt (id),
    ADD COLUMN godkjent_av_enhet UUID REFERENCES nav_enhet (id);

CREATE INDEX enkeltplass_prisinformasjon_godkjent_av_idx ON enkeltplass_prisinformasjon (godkjent_av);
CREATE INDEX enkeltplass_prisinformasjon_godkjent_av_enhet_idx ON enkeltplass_prisinformasjon (godkjent_av_enhet);

