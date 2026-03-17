CREATE UNIQUE INDEX tiltakshendelse_deltaker_id_forslag_id_uq
    ON tiltakshendelse (deltaker_id, forslag_id)
    WHERE forslag_id IS NOT NULL;
