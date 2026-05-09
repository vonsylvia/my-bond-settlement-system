-- Bond Settlement System - Schema Migration
-- Version: 1.1
-- Description: Add async retry support (RETRY_COUNT, FAILURE_REASON columns)
--              and SUBMITTING status for two-phase async processing.

-- 1. Add new columns
ALTER TABLE SETTLEMENT_INSTRUCTION ADD (
    RETRY_COUNT     NUMBER(3)     DEFAULT 0 NOT NULL,
    FAILURE_REASON  VARCHAR2(1000)
);

-- 2. Drop old CHECK constraint on STATUS and recreate with SUBMITTING
ALTER TABLE SETTLEMENT_INSTRUCTION DROP CONSTRAINT CK_STATUS;
ALTER TABLE SETTLEMENT_INSTRUCTION ADD CONSTRAINT CK_STATUS
    CHECK (STATUS IN ('PENDING', 'SUBMITTING', 'SENT', 'MATCHED', 'FAILED', 'CANCELLED'));

-- 3. Data patch: existing records need no changes
--    (RETRY_COUNT defaults to 0, FAILURE_REASON defaults to NULL)
