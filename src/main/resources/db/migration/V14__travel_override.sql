-- V14__travel_override.sql — Quantity Override (ADR-0024, issue #131).
--
-- A user correcting a travel-generated allowance line does it by overriding the
-- line's QUANTITY — the count — never its amount: the unit price stays statutory
-- and server-computed, so the "client never sends money" contract holds (the
-- client sends a count). Every override carries a mandatory reason.
--
-- The override is a trip INPUT, so it hangs off `travel` beside `kilometres` and
-- `parking_fees`, not off `expense_line`: generated lines stay pure derivations the
-- aggregate regenerates on every save, and an overridden line is structurally
-- identical to a calculated one. `expense_line` is therefore untouched here.
--
-- Keyed by (travel, generated_kind): a trip holds AT MOST ONE override per kind,
-- which is what keeps the overridable-kind list (PER_DIEM_FULL / PER_DIEM_PARTIAL /
-- MEAL — the kilometre distance and the parking fee are edited on the trip) data
-- rather than schema. Revisiting that list is a code change, not a migration.
--
-- Rows cascade with their travel, which already cascades with its report.

create table travel_override (
    -- The owning trip; the composite PK gives the one-per-kind cardinality.
    travel_id      bigint         not null references travel (id) on delete cascade,
    -- Which generated line this corrects — the same discriminator vocabulary as
    -- expense_line.generated_kind (varchar(20) holds PER_DIEM_PARTIAL).
    generated_kind varchar(20)    not null,
    -- The claimed count. numeric(19,2) matches every other quantity (ADR-0023);
    -- the domain additionally requires a whole number (a count of discrete days
    -- or meals), with a floor of 1 in this slice — issue #132 lowers it to 0 so
    -- an override can suppress its line.
    quantity       numeric(19, 2) not null,
    -- Why the calculated count does not fit the trip. NOT NULL and non-blank
    -- (enforced in the domain): an unexplained correction is never stored.
    reason         text           not null,
    primary key (travel_id, generated_kind)
);

comment on table travel_override is
    'Quantity Override: a user-entered count replacing a travel-generated allowance line''s calculated quantity, with a mandatory reason (ADR-0024). The unit price is never overridden.';
