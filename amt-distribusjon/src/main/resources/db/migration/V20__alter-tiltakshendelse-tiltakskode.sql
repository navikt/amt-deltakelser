ALTER TABLE tiltakshendelse ADD COLUMN tiltakskode VARCHAR;

UPDATE tiltakshendelse
SET tiltakskode = CASE tiltakstype
                      WHEN 'ARBFORB'     THEN 'ARBEIDSFORBEREDENDE_TRENING'
                      WHEN 'ARBRRHDAG'   THEN 'ARBEIDSRETTET_REHABILITERING'
                      WHEN 'AVKLARAG'    THEN 'AVKLARING'
                      WHEN 'DIGIOPPARB'  THEN 'DIGITALT_OPPFOLGINGSTILTAK'
                      WHEN 'GRUPPEAMO'   THEN 'GRUPPE_ARBEIDSMARKEDSOPPLAERING'
                      WHEN 'GRUFAGYRKE'  THEN 'GRUPPE_FAG_OG_YRKESOPPLAERING'
                      WHEN 'JOBBK'       THEN 'JOBBKLUBB'
                      WHEN 'INDOPPFAG'   THEN 'OPPFOLGING'
                      WHEN 'VASV'        THEN 'VARIG_TILRETTELAGT_ARBEID_SKJERMET'
    END;

ALTER TABLE tiltakshendelse ALTER COLUMN tiltakskode SET NOT NULL;

ALTER TABLE tiltakshendelse DROP COLUMN tiltakstype;