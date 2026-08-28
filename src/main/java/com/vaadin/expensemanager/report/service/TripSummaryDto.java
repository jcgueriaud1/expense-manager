package com.vaadin.expensemanager.report.service;

import java.time.LocalDateTime;

import com.vaadin.expensemanager.report.domain.Travel;

/**
 * One trip row on a report card (issue #147, ADR-0003) — the list projection of
 * a {@link Travel}.
 *
 * <p>Pure passthrough of the three fields the card draws: the route the user
 * typed and the trip's own date range. <strong>One row is one trip, not one
 * leg</strong>: {@link #destinations} is a single free-text string, so a card
 * showing {@code "Turku → Helsinki → Copenhagen"} has one trip whose arrows the
 * user wrote — the app neither parses nor composes a route, and there is no leg
 * model to build.
 *
 * <p>Deliberately unformatted: the {@code 25 Aug 2026 – 25 Aug 2026} rendering
 * (including repeating the date for a single-day trip) is the view's job.
 *
 * @param destinations the free-text route the user entered (required, never blank)
 * @param departureAt  departure date &amp; time
 * @param returnAt     return date &amp; time
 */
public record TripSummaryDto(String destinations, LocalDateTime departureAt,
        LocalDateTime returnAt) {
}
