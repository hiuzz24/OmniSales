-- Add tax_code column to suppliers table
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS tax_code VARCHAR(50);

-- Add NOT NULL constraint after backfilling existing rows
UPDATE suppliers SET tax_code = '' WHERE tax_code IS NULL;
