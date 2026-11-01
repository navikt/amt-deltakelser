module "deltaker_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "deltaker_view"

  view_schema = jsonencode([
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_dager_per_uke", type = "FLOAT", mode = "NULLABLE" },
    { name = "deltaker_deltakelsesprosent", type = "FLOAT", mode = "NULLABLE" },
    { name = "deltaker_bakgrunnsinformasjon", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_innhold", type = "JSON", mode = "NULLABLE" },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_aarsak", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltakerliste_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_navn", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_status", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_start_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltakerliste_slutt_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltakerliste_oppstart", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_gjennomforingstype", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltakerliste_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE" },
    { name = "arrangor_id", type = "STRING", mode = "NULLABLE" },
    { name = "arrangor_navn", type = "STRING", mode = "NULLABLE" },
    { name = "arrangor_orgnr", type = "STRING", mode = "NULLABLE" },
    { name = "overordnet_arrangor_id", type = "STRING", mode = "NULLABLE" },
    { name = "overordnet_arrangor_navn", type = "STRING", mode = "NULLABLE" },
    { name = "overordnet_arrangor_orgnr", type = "STRING", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
select d.id                   as deltaker_id,
       d.person_id            as deltaker_person_id,
       coalesce(
        date(v.created_at),
        date(JSON_EXTRACT_SCALAR(ia.deltaker_ved_import, '$.innsoktDato'))
      )                       as deltaker_innsokt_dato,
       d.startdato            as deltaker_startdato,
       d.sluttdato            as deltaker_sluttdato,
       d.dager_per_uke        as deltaker_dager_per_uke,
       d.deltakelsesprosent   as deltaker_deltakelsesprosent,
       d.bakgrunnsinformasjon as deltaker_bakgrunnsinformasjon,
       d.innhold              as deltaker_innhold,
       d.created_at           as deltaker_created_at,
       d.modified_at          as deltaker_modified_at,
       d.kilde                as deltaker_kilde,
       ds.type                as deltaker_status_type,
       ds.aarsak              as deltaker_status_aarsak,
       ds.gyldig_fra          as deltaker_status_gyldig_fra,
       ds.gyldig_til          as deltaker_status_gyldig_til,
       ds.created_at          as deltaker_status_created_at,
       ds.modified_at         as deltaker_status_modified_at,
       dl.id                  as deltakerliste_id,
       dl.navn                as deltakerliste_navn,
       dl.status              as deltakerliste_status,
       dl.start_dato          as deltakerliste_start_dato,
       dl.slutt_dato          as deltakerliste_slutt_dato,
       dl.oppstart            as deltakerliste_oppstart,
       dl.gjennomforingstype as deltakerliste_gjennomforingstype,
       dl.modified_at         as deltakerliste_modified_at,
       dl.created_at          as deltakerliste_created_at,
       t.tiltakskode          as tiltak_tiltakskode,
       a.id                   as arrangor_id,
       a.navn                 as arrangor_navn,
       a.organisasjonsnummer  as arrangor_orgnr,
       oa.id                  as overordnet_arrangor_id,
       oa.navn                as overordnet_arrangor_navn,
       oa.organisasjonsnummer as overordnet_arrangor_orgnr
from ${var.dataset_id}.public_deltaker d
         join ${var.dataset_id}.public_deltaker_status ds on d.id = ds.deltaker_id
         join ${var.dataset_id}.public_gjennomforing dl on d.deltakerliste_id = dl.id
         join ${var.dataset_id}.public_tiltakstype t on dl.tiltakstype_id = t.id
         join ${var.dataset_id}.public_arrangor a on dl.arrangor_id = a.id
         left join ${var.dataset_id}.public_arrangor oa on a.overordnet_arrangor_id = oa.id
         left join ${var.dataset_id}.public_vedtak v on v.deltaker_id = d.id
         left join ${var.dataset_id}.public_importert_fra_arena ia on ia.deltaker_id = d.id
         join `${var.dataset_id}.public_nav_bruker` nb on nb.person_id = d.person_id
where ds.gyldig_til is null and ds.gyldig_fra <= CURRENT_TIMESTAMP() and nb.adressebeskyttelse is null
EOF
  )
}
