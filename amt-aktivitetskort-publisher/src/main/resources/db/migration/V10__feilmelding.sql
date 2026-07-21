CREATE TABLE feilmelding
(
    id             uuid primary key         not null,
    key            uuid                     not null,
    errorMessage   varchar                  not null,
    errorType      varchar                  not null,
    failingMessage varchar                  not null,
    timestamp      timestamp with time zone not null
)
