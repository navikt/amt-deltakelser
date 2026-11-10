module "deltaker_underoppfolging_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "deltaker_underoppfolging_view"

  view_schema = jsonencode([
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltakerliste_id", type = "STRING", mode = "NULLABLE" },
    { name = "oppstartstype", type = "STRING", mode = "NULLABLE" },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE" },
    { name = "navbruker_innsatsgruppe", type = "STRING", mode = "NULLABLE" },
    { name = "navbruker_oppfolgingsperioder", type = "JSON", mode = "NULLABLE" },
    { name = "aktiv_oppfolgingsperiode", type = "BOOLEAN", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
select d.id                   as deltaker_id,
       d.person_id            as deltaker_person_id,
       coalesce(
        date(JSON_EXTRACT_SCALAR(ia.deltaker_ved_import, '$.innsoktDato')),
        date(i.innsokt, 'Europe/Oslo'),
        date(v.created_at, 'Europe/Oslo')
      )                       as deltaker_innsokt_dato,
       d.startdato            as deltaker_startdato,
       d.sluttdato            as deltaker_sluttdato,
       d.created_at           as deltaker_created_at,
       d.modified_at          as deltaker_modified_at,
       d.kilde                as deltaker_kilde,
       ds.type                as deltaker_status_type,
       ds.gyldig_fra          as deltaker_status_gyldig_fra,
       ds.gyldig_til          as deltaker_status_gyldig_til,
       ds.created_at          as deltaker_status_created_at,
       ds.modified_at         as deltaker_status_modified_at,
       dl.id                  as deltakerliste_id,
       dl.oppstart            as oppstartstype,
       t.tiltakskode          as tiltak_tiltakskode,
       nb.innsatsgruppe       as navbruker_innsatsgruppe,
       nb.oppfolgingsperioder as navbruker_oppfolgingsperioder,
       (EXISTS (
          SELECT 1
          FROM UNNEST(JSON_QUERY_ARRAY(nb.oppfolgingsperioder)) AS period
          WHERE JSON_VALUE(period, '$.sluttdato') IS NULL
      )) as aktiv_oppfolgingsperiode
from ${var.dataset_id}.public_deltaker d
         join ${var.dataset_id}.public_deltaker_status ds on d.id = ds.deltaker_id
         join ${var.dataset_id}.public_gjennomforing dl on d.deltakerliste_id = dl.id
         join ${var.dataset_id}.public_tiltakstype t on dl.tiltakstype_id = t.id
         join ${var.dataset_id}.public_nav_bruker nb on d.person_id = nb.person_id
         left join ${var.dataset_id}.public_vedtak v on v.deltaker_id = d.id
         left join ${var.dataset_id}.public_importert_fra_arena ia on ia.deltaker_id = d.id
         left join ${var.dataset_id}.public_innsok i on i.deltaker_id = d.id
where ds.gyldig_til is null and ds.gyldig_fra <= CURRENT_TIMESTAMP() and nb.adressebeskyttelse is null
EOF
  )
}
