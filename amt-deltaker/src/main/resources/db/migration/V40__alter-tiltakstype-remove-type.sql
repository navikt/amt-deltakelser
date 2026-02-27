ALTER TABLE tiltakstype DROP CONSTRAINT tiltakstype_type_key;

-- fjerner også indeksen tiltakstype_type_idx
ALTER TABLE tiltakstype DROP COLUMN type;

-- legger til unikt kriterium og indeks
ALTER TABLE tiltakstype ADD CONSTRAINT tiltakstype_tiltakskode_key UNIQUE (tiltakskode);