CREATE TABLE IF NOT EXISTS digital_bruker_cache
(
    personident VARCHAR(11) PRIMARY KEY,
    er_digital  BOOLEAN                  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

