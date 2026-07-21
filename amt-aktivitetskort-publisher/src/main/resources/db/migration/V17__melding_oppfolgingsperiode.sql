alter TABLE melding
    ADD COLUMN oppfolgingsperiode uuid references oppfolgingsperiode(id);