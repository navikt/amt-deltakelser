module "prisinformasjon_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "prisinformasjon_view"

  view_schema = jsonencode([
    { name = "gjennomforing_id", type = "STRING", mode = "NULLABLE" },
    { name = "gjennomforing_status", type = "STRING", mode = "NULLABLE" },
    { name = "rolle", type = "STRING", mode = "NULLABLE" },
    { name = "pristype", type = "STRING", mode = "NULLABLE" },
    { name = "prisinformasjon_status", type = "STRING", mode = "NULLABLE" },
    { name = "pris", type = "INTEGER", mode = "NULLABLE" },
    { name = "tilskuddstype", type = "STRING", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
select
    g.id as gjennomforing_id,
    g.status as gjennomforing_status,
    link.rolle as rolle,
    p.prisinformasjon_json_type as pristype,
    p.status as prisinformasjon_status,
    cast(coalesce(p.anskaffelse_pris, pb.pris) as int64) as pris,
    pb.pristype as tilskuddstype
from `${var.dataset_id}.public_gjennomforing` g
join `${var.dataset_id}.public_prisinformasjon_gjennomforing_link` link on g.id = link.gjennomforing_id
join `${var.dataset_id}.public_prisinformasjon` p on p.id = link.prisinformasjon_id
left join `${var.dataset_id}.public_prisinformasjon_belop` pb on p.id = pb.prisinfo_id
EOF
  )
}
