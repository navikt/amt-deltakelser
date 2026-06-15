CREATE TABLE IF NOT EXISTS deltakerliste_prisinfo
(
    deltakerliste_id UUID                     NOT NULL REFERENCES deltakerliste (id) ON DELETE CASCADE,
    pristype         VARCHAR                  NOT NULL,
    pris             INT                      NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id, pristype)
);
