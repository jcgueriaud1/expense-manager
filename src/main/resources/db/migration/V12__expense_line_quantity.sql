-- V12__expense_line_quantity.sql — Expense-line quantity (ADR-0023, issue #122).
--
-- `amount` is repurposed (not renamed) as the gross **unit price** (each) and a
-- new `quantity` multiplies it: line gross = amount × quantity (HALF_UP scale 2).
-- Existing rows backfill to quantity 1, so every stored amount stays untouched
-- and every pre-migration report totals exactly as before (old gross = unit × 1).
--
-- Strictly positive is a domain invariant enforced by ExpenseLine (credits ride a
-- negative unit price, never a negative quantity); the column keeps the NOT NULL
-- and the default so a legacy insert path can never write a null.

alter table expense_line
    add column quantity numeric(19, 2) not null default 1;

comment on column expense_line.amount is
    'Gross unit price (each) as entered. Required and non-zero; negatives allowed for credits (ADR-0023).';
comment on column expense_line.quantity is
    'Line quantity, strictly > 0, default 1. Line gross = amount × quantity (ADR-0023).';
