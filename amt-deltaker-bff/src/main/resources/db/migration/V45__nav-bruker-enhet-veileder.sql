alter table nav_bruker
    add column nav_veileder_id uuid references nav_ansatt (id);
alter table nav_bruker
    add column nav_enhet_id uuid references nav_enhet (id);

