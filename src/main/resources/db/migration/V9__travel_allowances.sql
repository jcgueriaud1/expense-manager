-- V9__travel_allowances.sql — Phase 4.3 Travel Calculator km / meal / parking
-- (ADR-0006, ADR-0010, ADR-0019).
--
-- Slice 2 (V8) let a Travel own a single generated per-diem line. This slice
-- lets one trip own up to four generated lines — per-diem, kilometre allowance,
-- meal allowance, and parking — so:
--   1. expense_line gains a generated_kind discriminator (PER_DIEM / KILOMETRE /
--      MEAL / PARKING), null for a manual line. It is the first-class marker the
--      per-diem line lacked (F-034), and it routes a generated line in the totals:
--      the three tax-free allowances get their own subtotal rows while parking —
--      being VAT-bearing — flows into Net/VAT.
--   2. travel gains the remaining trip INPUTS the new lines are generated from:
--      kilometres driven, a pay-meal-allowance flag, and parking fees. Money at
--      numeric(19,2) (ADR-0010); the amounts stay on generated lines, never here.
--   3. two new 0 %-VAT expense types are seeded — "Kilometre allowance" and
--      "Meal allowance" — kept distinct from the existing "Travel allowance" so
--      each allowance line is filed under its own type.

alter table expense_line
    add column generated_kind varchar(20);

alter table travel
    add column kilometres         numeric(19, 2) not null default 0,
    add column pay_meal_allowance boolean        not null default false,
    add column parking_fees       numeric(19, 2) not null default 0;

-- Two more allowance expense types, each defaulting to the seeded 0 % VAT rate
-- (V3, ADR-0018). Appended after the existing display order; matching the rate by
-- value is unambiguous (the 0 % row is unique).
insert into expense_type (name, display_order, active, default_vat_rate_id, created_at, updated_at)
select seed.name, seed.ord, true,
       (select id from vat_rate where value = 0.0),
       now(), now()
from (values
    ('Kilometre allowance', 6),
    ('Meal allowance',      7)
) as seed (name, ord);
