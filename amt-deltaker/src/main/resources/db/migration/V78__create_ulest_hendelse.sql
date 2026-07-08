CREATE TABLE ulest_hendelse (
    id uuid PRIMARY KEY,
    deltaker_id uuid NOT NULL,
    opprettet timestamp with time zone NOT NULL,
    ansvarlig jsonb,
    hendelse jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ulest_hendelse_deltaker_id_idx ON ulest_hendelse (deltaker_id);
