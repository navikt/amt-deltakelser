-- Rollback requires re-adding the nullable column; dropped values cannot be recovered.
ALTER TABLE enkeltplass_prisinformasjon
    DROP COLUMN ingenkostnader_aarsak;
