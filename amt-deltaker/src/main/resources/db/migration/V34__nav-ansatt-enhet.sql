ALTER TABLE nav_ansatt ADD COLUMN nav_enhet_id uuid references nav_enhet (id);
