-- V10__fix_2026_kilometre_rate.sql — correct the seeded 2026 kilometre rate.
--
-- V7 seeded the 2026 kilometre compensation at EUR 0.590/km, which is wrong: the
-- Verohallinto 2026 decision sets it at EUR 0.550/km (issue #90). V7 has already
-- shipped, so it must not be edited (Flyway checksum); this migration corrects
-- the value in place for existing databases. Fresh databases run V7 then V10 and
-- land on the same correct figure. Only the untouched 2026 default is corrected;
-- any admin-edited value is left alone (see the guard below).
update kilometre_rate
set amount_per_km = 0.550,
    updated_at    = now()
where year = 2026
  and amount_per_km = 0.590;
