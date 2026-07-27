-- V13__per_diem_full_partial.sql — Split the per-diem generated kind (ADR-0023,
-- issue #124).
--
-- The single `PER_DIEM` discriminator is replaced by `PER_DIEM_FULL` and
-- `PER_DIEM_PARTIAL`, so each per-diem line is an honest `quantity = days` ×
-- `unit price = per-day rate` instead of one pre-multiplied lump. Both new values
-- stay tax-free (0 % VAT) and group into the same per-diem subtotal.
--
-- Any persisted `PER_DIEM` row is reclassified to `PER_DIEM_FULL` — a value rename,
-- not a re-split: the row keeps its stored amount at quantity 1, so **every total is
-- unchanged** by this migration and no rate lookup (which this SQL cannot do) is
-- needed. The next edit of the owning travel re-costs the trip and re-splits it into
-- honest full/partial lines. Where no such row exists (fresh/target environments)
-- this is exactly the plain value rename, and the statement is a harmless no-op.
--
-- No column change: `generated_kind` is varchar(20) (V9), which holds the longer
-- `PER_DIEM_PARTIAL` value.

update expense_line
set generated_kind = 'PER_DIEM_FULL'
where generated_kind = 'PER_DIEM';

comment on column expense_line.generated_kind is
    'Generated (travel-owned) line kind: PER_DIEM_FULL / PER_DIEM_PARTIAL / KILOMETRE / MEAL / PARKING; null for a manual line (ADR-0023).';
