module "prisinformasjon_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "prisinformasjon_view"

  view_schema = jsonencode([
    { name = "gjennomforing_id", type = "STRING", mode = "NULLABLE" },
    { name = "gjennomforing_status", type = "STRING", mode = "NULLABLE" },
    { name = "rolle", type = "STRING", mode = "NULLABLE" },
    { name = "pris_status", type = "STRING", mode = "NULLABLE" },
    { name = "pristype", type = "STRING", mode = "NULLABLE" },
    { name = "godkjenningsstatus", type = "STRING", mode = "NULLABLE" },
    { name = "pris", type = "INTEGER", mode = "NULLABLE" },
    { name = "tilskuddstype", type = "STRING", mode = "NULLABLE" },
    { name = "prisinformasjon_opprettet", type = "TIMESTAMP", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
select
    gjennomforing.id as gjennomforing_id,
    gjennomforing.status as gjennomforing_status,
    prisinformasjon_gjennomforing_link.rolle as rolle,
    case
        when prisinformasjon_gjennomforing_link.rolle = 'GJELDENDE' then 'GJELDENDE'
        when prisinformasjon_gjennomforing_link.rolle = 'ENDRING' then 'FORSLAG'
        else 'HISTORISK'
    end as pris_status,
    prisinformasjon.prisinformasjon_json_type as pristype,
    prisinformasjon.status as godkjenningsstatus,
    cast(coalesce(prisinformasjon.anskaffelse_pris, prisinformasjon_tilskudd_belop.pris) as int64) as pris,
    prisinformasjon_tilskudd_belop.pristype as tilskuddstype,
    prisinformasjon.created_at as prisinformasjon_opprettet
from `${var.dataset_id}.public_prisinformasjon` prisinformasjon
join `${var.dataset_id}.public_gjennomforing` gjennomforing on gjennomforing.id = prisinformasjon.gjennomforing_id
left join `${var.dataset_id}.public_prisinformasjon_gjennomforing_link` prisinformasjon_gjennomforing_link on prisinformasjon_gjennomforing_link.prisinformasjon_id = prisinformasjon.id
left join `${var.dataset_id}.public_prisinformasjon_belop` as prisinformasjon_tilskudd_belop on prisinformasjon.id = prisinformasjon_tilskudd_belop.prisinfo_id
EOF
  )
}
