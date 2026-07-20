-- Restore marketplace SKUs that were previously saved with generated platform prefixes.
-- Safe rules:
-- 1) Only update local product_variants currently using SHOPIFY-/LAZADA-/TIKTOK- generated SKU.
-- 2) Only use channel_product_variants.external_sku when it is present.
-- 3) Do not update if another non-deleted variant already owns that external SKU.
-- 4) Update product.sku only when it is still generated and the chosen SKU is not used by another product.

WITH preferred_variant_skus AS (
    SELECT DISTINCT ON (pv.id)
           pv.id AS variant_id,
           trim(cpv.external_sku) AS external_sku
    FROM product_variants pv
    JOIN channel_product_variants cpv ON cpv.variant_id = pv.id
    JOIN channel_products cp ON cp.id = cpv.channel_product_id
    JOIN channels ch ON ch.id = cp.channel_id
    WHERE pv.deleted_at IS NULL
      AND cp.mapping_state = 'ACTIVE'
      AND ch.deleted_at IS NULL
      AND cpv.external_sku IS NOT NULL
      AND btrim(cpv.external_sku) <> ''
      AND (
          pv.sku LIKE 'SHOPIFY-%'
          OR pv.sku LIKE 'LAZADA-%'
          OR pv.sku LIKE 'TIKTOK-%'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM product_variants other
          WHERE other.deleted_at IS NULL
            AND other.id <> pv.id
            AND lower(other.sku) = lower(trim(cpv.external_sku))
      )
    ORDER BY pv.id, cpv.updated_at DESC NULLS LAST
)
UPDATE product_variants pv
SET sku = preferred_variant_skus.external_sku,
    updated_at = now()
FROM preferred_variant_skus
WHERE pv.id = preferred_variant_skus.variant_id;

WITH preferred_product_skus AS (
    SELECT DISTINCT ON (p.id)
           p.id AS product_id,
           pv.sku AS restored_sku
    FROM products p
    JOIN product_variants pv ON pv.product_id = p.id
    WHERE p.deleted_at IS NULL
      AND pv.deleted_at IS NULL
      AND pv.sku IS NOT NULL
      AND btrim(pv.sku) <> ''
      AND (
          p.sku LIKE 'SHOPIFY-%'
          OR p.sku LIKE 'LAZADA-%'
          OR p.sku LIKE 'TIKTOK-%'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM products other_product
          WHERE other_product.deleted_at IS NULL
            AND other_product.id <> p.id
            AND lower(other_product.sku) = lower(pv.sku)
      )
    ORDER BY p.id, pv.updated_at DESC NULLS LAST
)
UPDATE products p
SET sku = preferred_product_skus.restored_sku,
    updated_at = now()
FROM preferred_product_skus
WHERE p.id = preferred_product_skus.product_id;
