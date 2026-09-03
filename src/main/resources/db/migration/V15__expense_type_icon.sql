-- V15__expense_type_icon.sql — an icon per expense type (issue #172, ADR-0026).
--
-- The report-detail design gives every expense row a 20px Lucide glyph for its
-- expense type, and draws no colour swatch anywhere. The app's `.category-dot`
-- was app-invented and hashed String.hashCode() of the type NAME onto six palette
-- hues, so the colour was stored nowhere and changed silently on a rename. It also
-- did not work: against the nine seeded types four collide on --aura-red (Travel
-- allowance, Taxi/transport, Accommodation, Other), two on --aura-green, and
-- --aura-purple is unreachable.
--
-- So the glyph becomes a persisted attribute of the type, chosen by an admin from
-- the Lucide set — not a view-side name→glyph map, which would carry exactly the
-- rename fragility of the hash it replaces (docs/design/components/
-- expense-line-card.md § "The glyph replaces the colour dot").
--
-- NULLABLE on purpose: a type an admin adds before picking a glyph is a valid type,
-- and its rows render with no glyph rather than a wrong one. Nothing rests on the
-- glyph — every row names its type in text beside it (ADR-0020).
--
-- The value is the upstream Lucide glyph name, which is also the <symbol> id in the
-- vendored sprite and the LucideIcon enum's own `glyph()`. varchar(40) covers the
-- longest name in the set with room to spare.

alter table expense_type
    add column icon varchar(40);

comment on column expense_type.icon is
    'Lucide glyph name for this type''s rows (the sprite <symbol> id, e.g. ''plane''). Null means no glyph; the type name always renders as text beside it (ADR-0020, ADR-0026).';

-- The nine seeded types (V3, V9, V11). Four are the design's own, drawn on frame
-- 116:4444: plane, car-taxi-front, bed, utensils. The design draws no row for the
-- other five, so these are the app's initial choices from the glyphs already in the
-- sprite — an admin may change any of them.
update expense_type set icon = 'plane'           where name = 'Travel allowance';
update expense_type set icon = 'car-taxi-front'  where name = 'Taxi/transport';
update expense_type set icon = 'bed'             where name = 'Accommodation';
update expense_type set icon = 'utensils'        where name = 'Restaurant/meals';
update expense_type set icon = 'inbox'           where name = 'Parking/supplies/goods';
update expense_type set icon = 'file-text'       where name = 'Publications';
update expense_type set icon = 'map-pin'         where name = 'Kilometre allowance';
update expense_type set icon = 'utensils'        where name = 'Meal allowance';
update expense_type set icon = 'archive'         where name = 'Other';
