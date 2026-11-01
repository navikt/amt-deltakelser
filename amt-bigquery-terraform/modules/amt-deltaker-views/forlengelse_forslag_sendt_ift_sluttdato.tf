module "forlengelse_forslag_sendt_ift_sluttdato" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "forlengelse_forslag_sendt_ift_sluttdato"

  view_schema = jsonencode([
    { name = "time_diff_bucket", type = "STRING", mode = "NULLABLE" },
    { name = "count_per_bucket", type = "INTEGER", mode = "NULLABLE" },
    { name = "bucket_order", type = "INTEGER", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF

  WITH sluttdatoer AS (
    SELECT deltaker_id, 
           JSON_EXTRACT_SCALAR(endring, '$.sluttdato') AS sluttdato, 
           created_at
    FROM ${var.dataset_id}.public_deltaker_endring
    WHERE JSON_EXTRACT_SCALAR(endring, '$.sluttdato') IS NOT NULL
    UNION ALL
    SELECT deltaker_id, 
           JSON_EXTRACT_SCALAR(deltaker_ved_import, '$.sluttdato') AS sluttdato, 
           importert_dato AS created_at
    FROM ${var.dataset_id}.public_importert_fra_arena
    WHERE JSON_EXTRACT_SCALAR(deltaker_ved_import, '$.sluttdato') IS NOT NULL
    UNION ALL
    SELECT deltaker_id, 
           JSON_EXTRACT_SCALAR(endring, '$.sluttdato') AS sluttdato, 
           created_at
    FROM `${var.dataset_id}.public_endring_fra_arrangor`
    WHERE JSON_EXTRACT_SCALAR(endring, '$.sluttdato') IS NOT NULL
),

latest_sluttdato AS (
    SELECT s.deltaker_id, 
           s.sluttdato, 
           s.created_at,
           ROW_NUMBER() OVER (PARTITION BY s.deltaker_id ORDER BY s.created_at DESC) AS rn
    FROM sluttdatoer s
),
ranked_results AS (
    SELECT 
        f.id AS forslag_id,
        f.created_at AS forslag_created_at,
        s.created_at AS forrige_sluttdato_created_at,
        s.sluttdato as forrige_sluttdato,
        JSON_EXTRACT_SCALAR(f.endring, '$.sluttdato') AS ny_sluttdato,
        ROW_NUMBER() OVER (PARTITION BY f.id ORDER BY s.created_at DESC) AS rn_per_forslag
    FROM latest_sluttdato s
    JOIN ${var.dataset_id}.public_forslag f
        ON s.deltaker_id = f.deltaker_id
        AND s.created_at < f.created_at
    WHERE JSON_EXTRACT_SCALAR(f.endring, '$.type') = 'ForlengDeltakelse'
),
time_differences AS (
SELECT TIMESTAMP_DIFF(DATE(forrige_sluttdato), DATE(forslag_created_at, 'Europe/Oslo'), DAY) AS time_diff_days
FROM ranked_results 
where rn_per_forslag = 1
)

SELECT
  CASE
    WHEN time_diff_days < -5 then 'Mer enn 5 dager etter sluttdato'
    WHEN time_diff_days = -5 then '-5'
    WHEN time_diff_days = -4 then '-4'
    WHEN time_diff_days = -3 then '-3'
    WHEN time_diff_days = -2 then '-2'
    WHEN time_diff_days = -1 then '-1'
    WHEN time_diff_days = 0 then 'Samme dag som sluttdato'
    WHEN time_diff_days = 1 then '1'
    WHEN time_diff_days = 2 then '2'
    WHEN time_diff_days = 3 then '3'
    WHEN time_diff_days = 4 then '4'
    WHEN time_diff_days = 5 then '5'
    WHEN time_diff_days > 5 then 'Mer enn 5 dager før sluttdato'
  END AS time_diff_bucket,
  COUNT(*) AS count_per_bucket,
  CASE 
    WHEN time_diff_days < -5 then 1
    WHEN time_diff_days = -5 then 2
    WHEN time_diff_days = -4 then 3
    WHEN time_diff_days = -3 then 4
    WHEN time_diff_days = -2 then 5
    WHEN time_diff_days = -1 then 6
    WHEN time_diff_days = 0 then 7
    WHEN time_diff_days = 1 then 8
    WHEN time_diff_days = 2 then 9
    WHEN time_diff_days = 3 then 10
    WHEN time_diff_days = 4 then 11
    WHEN time_diff_days = 5 then 12
    WHEN time_diff_days > 5 then 13
  END AS bucket_order,
FROM time_differences
GROUP BY time_diff_bucket, bucket_order
ORDER BY bucket_order;
/*
*/
EOF
  )
}
