-- V11__expense_type_other.sql — add the "Other" catch-all expense type (issue #87).
--
-- The seeded types (V3, V9) missed a general catch-all, so expenses that fit no
-- specific category — a training/course invoice, a conference ticket, an
-- uncategorised service — had nowhere to be filed. "Other" was part of the
-- original flat category list (ADR-0018) and is added back as an admin-editable
-- type like any other: deactivatable, reorderable, never a special case.
--
-- Defaults to the general 25.5 % VAT rate (ADR-0018: goods/services → general
-- rate); the line may still override it. Appended after the existing display
-- order; matching the rate by value is unambiguous (the 25.5 % row is unique).
insert into expense_type (name, display_order, active, default_vat_rate_id, created_at, updated_at)
select 'Other', 8, true,
       (select id from vat_rate where value = 25.5),
       now(), now();
