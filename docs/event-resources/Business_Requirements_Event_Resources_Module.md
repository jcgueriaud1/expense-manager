# Business Requirements Document
## Event Resources Module

**Application area:** Extension to existing company application (alongside the Travel Expense Logging module)
**Prepared for:** Marketing / Developer Relations department
**Status:** Draft v1

---

## 1. Background

The company already has an application used for logging travel expenses, capturing trip details (time, destination) and associated expense files.

The Marketing and Developer Relations departments regularly travel to exhibitions and events to present the company's products. Attending an event successfully requires more than booking travel — it requires having the **right combination of people and equipment** available at the right time. Today, this coordination is not supported by any system, which creates risk of double-booking people, missing equipment, or discovering shortages too late to correct them.

## 2. Problem Statement

The department needs a way to:

- Move each event through its lifecycle: a planning stage, where the event is only a possibility; a confirmed stage, once the department has committed to attending; a past stage, once the event has taken place; and a cancelled state, if plans fall through. An event can move backward as well as forward (e.g. a confirmed event returning to planning).
- See which people and which equipment are available for a given time period, while an event is still in the planning stage, before committing to it.
- Reserve the correct combination of people and equipment once an event moves into the confirmed stage.
- Surface conflicts where the same person or the same piece of equipment is needed in two places at once. Rather than blocking the user, the system flags any affected event with a persistent conflict indicator that the user must actively resolve; nothing is ever reassigned or erased automatically.
- Identify and highlight cases where a person or piece of equipment is assigned to two events in different locations with little or no time gap between them, so the department can judge whether the transfer between locations is realistically achievable (for example, an event in Barcelona ending Tuesday followed by an event in Germany starting Wednesday). The system should surface this as a warning rather than deciding on the department's behalf.
- Keep track of equipment as it moves out to events and returns, including its condition, once the event reaches the past stage.
- Optionally link specific pieces of equipment to specific people attending an event, for example to track who is responsible for a particular laptop, while allowing equipment to simply travel as a shared pool with no individual assignment when that level of detail isn't needed.
- Reduce manual effort in setting up new events by pulling in publicly known event details automatically.

There is currently no single place where staff availability, equipment availability, and event planning come together. Planning relies on informal knowledge and manual coordination, which does not scale well as the number of events, staff, and equipment grows.

## 3. Business Goals

1. **Enable confident event planning.** Give the department a clear, reliable view of what people and equipment would be available if a given event were confirmed, before making that commitment.
2. **Prevent resource conflicts.** Surface conflicts early when two potential events compete for the same people or equipment, so the department can make an informed choice rather than discovering the clash later.
3. **Provide a single source of truth for equipment.** Know what equipment exists, what category it belongs to, whether it's in service, and its condition — for equipment worth tracking individually. Where a given asset is at any point in time can be seen by filtering the planning view to that asset, rather than being stored as a separate location field.
4. **Support planning of promotional stock at a lighter level.** Track quantities of semi-tracked promotional items (e.g. branded merchandise) taken to events and handed out over time, without requiring full inventory management.
5. **Streamline event setup.** Reduce the manual effort of creating a new event by allowing objective, publicly available event details (dates, venue, location) to be fetched automatically, while keeping all planning and staffing decisions in human hands.
6. **Give the department a visual, filterable overview** of how people and equipment are allocated across time, so gaps and overlaps are easy to spot at a glance.
7. **Enable reporting across resources and events.** Allow the department to generate reports on people, equipment, or promotional stock, both for a single event and aggregated across multiple events over a period — for example, how many t-shirts were distributed at an event, or how much time or how many resources were spent across all events attended in a year.
8. **Support a return-on-investment view per event.** Compare an event's impact metrics (such as contacts or leads made) against the resources spent on it — namely trip expenses (drawn from the existing Travel Expense module, which may log multiple expense entries against a single event) and an estimated cost of staff time spent attending rather than doing other productive work. This depends on data from the Travel Expense module and may require revisiting the cross-module integration noted as out of scope below.

## 4. Scope

### 4.1 In Scope

- A **People** register: individuals treated as resources, each described by expertise area tags reflecting what expertise or specialization they bring (e.g. Java, web components, architecture, sales, UX — including specific technical specialties, with a person able to hold several). An event is connected directly to people, and the expertise available at that event follows from whichever people are attending; there is no separate per-event role assigned to a person. Presentations given at an event are noted as free text on the event itself, rather than tracked as structured data per person.
- A shared, maintainable list of **expertise area** tags that can be applied to people.
- An **Equipment** register covering:
  - **Tracked assets** (e.g. booth computers, presentation tech, booth furniture such as backdrops and tables): each with a maintainable category, a simple made-up identifier for individual identification (e.g. "green desk" — not necessarily a manufacturer serial number), free-text notes, and an **In Use / Out of Use** status. Only In Use assets are offered when assigning equipment to an event; Out of Use covers items that are lost, broken, retired, or otherwise not in circulation. (This status is deliberately distinct from whether the asset is free at a given time — the latter follows from event reservations and is not called "availability" here to avoid confusion.)
  - **Semi-tracked promotional stock** (e.g. branded t-shirts): planned quantity per event and running totals of amounts handed out over time, without individual identity.
- A free-form to-do style list of ad hoc items on each event (e.g. chocolate, other items purchased locally as needed), purely as a packing reminder with no quantity tracking.
- An **Event lifecycle**:
  - **Draft / proposed** — a possible event under consideration, used to explore what people and equipment would be available if the company committed to it, without reserving anything.
  - **Confirmed** — the event is committed to; the selected people and equipment are reserved for that time window.
  - **Past / archived** — the event has happened and been closed out; its full record is retained for reference. The transition into this stage is the **wrap-up** activity: equipment is checked back in, condition notes and general remarks are logged, and impact metrics are captured (such as the number of contacts or leads made, for example from ID badge scans, to help gauge how impactful the event was). Wrap-up is a step in reaching the Past stage, not a separate stored status of its own.
  - **Cancelled** — the event is called off. An event can be cancelled at any point. If it is cancelled **before it starts**, its reservations are released and the people and equipment become free again. If it is cancelled **after it has already started** (the resources are physically at the location), its reservations are retained for the original dates, so any attempt to use the same resources within that window raises a conflict indicator.
  - The lifecycle is **bidirectional** — an event can move backward as well as forward (for example, a confirmed event can be returned to draft), not only in one direction.
- **Conflict handling**: when the same person or asset is committed to two events that overlap in time, the system does not block the action. Instead, each affected event is marked with a persistent **conflict indicator** (shown with error styling and an explanatory message) that the user must actively resolve. Nothing is reassigned or erased automatically. Draft-stage overlaps are surfaced as softer warnings, since drafts hold no binding reservations.
- A **filterable, Gantt-style planning view** showing people and/or equipment across a timeline, startable as a high-level overview and drillable by resource type, expertise area, or other attributes.
- **AI-assisted event creation**: given an event name, an AI agent fetches objective, publicly available logistics details (dates, venue, location) to pre-fill a new event record for user review. Helper text guides the user to enter an accurate, specific value (e.g. including the year or edition). If no matching event is found, a small notification informs the user; if several possible matches are found, the user is prompted to provide more specific search keywords. Scope is limited to factual lookup only — no AI-driven suggestions or judgment calls about attendance, staffing, or equipment.
- **Reporting**: the ability to generate reports on people, equipment, or promotional stock usage, either for a single event or aggregated across a set of events over time — for example, quantities of promotional items distributed, or total time or resources committed across all events attended in a given period.
- **Return-on-investment reporting**: comparing an event's impact metrics against the resources spent on it. This phase rests on some deliberate simplifying assumptions, since not all underlying data is tracked in this system:
  - Staff time is valued at a single **fixed average staff cost rate** (an admin-configured constant representing the average cost of an employee's time), rather than real per-person salaries.
  - The **time each participant spent** at an event and the **sum of their travel expenses** come from the Travel Expense module. Each closed event relates to one or more expense items in that module, and that module returns, per participant, an object containing the employee, the trip duration, and the summed expenses.
  - ROI is then a comparison of the event's impact metrics against (summed travel expenses) + (summed participant time × average staff cost rate).
  This defines a narrow, read-only interface to the Travel Expense module (see Section 4.2).

### 4.2 Out of Scope (for this phase)

- Tracking quantities or stock levels of ad hoc items (e.g. chocolate) — these remain a free-form reminder list only.
- A dedicated person-centric history view. The system does retain which people attended which past events (this is what reporting reads), but there is no separate "career history per person" screen in this phase.
- Managing personal unavailability (leave, sickness, etc.) within this system — see the assumption in Section 7 that this is provided by an external system.
- Full inventory management of promotional stock (e.g. current stock on hand, reorder points) beyond planned-per-event quantities and running distributed totals.
- Maintenance scheduling or condition/service history for tracked equipment beyond free-text notes and the In Use / Out of Use status.
- AI-driven recommendations on whether to attend an event, who should be sent, or what equipment to bring.
- Deep, two-way integration with the Travel Expense module. The only interface in this phase is a **narrow, read-only feed** from that module for ROI reporting, as defined in Section 4.1 (per-participant employee, trip duration, and summed expenses for each closed event). Anything beyond that read-only feed is out of scope for now.

## 5. Key Stakeholders

- **User** — the day-to-day role within the Marketing/Developer Relations department: plans events, checks availability, confirms events, assigns people and equipment, and is responsible for checking equipment in and out and recording its condition after an event.
- **Admin** — maintains the underlying data the module relies on: registering people and equipment, and keeping the shared expertise area and asset category lists up to date. In practice this may be the same individuals as the User role, wearing a different hat.
- **Management** — primarily interested in the reporting side: resource usage reports and return-on-investment reporting across events.
- **Event attendees (the "resources")** — presenters, booth staff, executives representing the company at events. These are the people being scheduled, not necessarily system users themselves.

## 6. Success Criteria

- The department can, at any time, answer "who and what would be available if we committed to event X?" without manual cross-checking.
- Double-booking of people or key equipment across confirmed events is eliminated.
- Conflicting draft events are visibly flagged to the user before confirmation.
- Setting up a new event's basic logistics takes a single lookup action rather than manual data entry.
- Staff can visually see, and filter, resource allocation across time via the planning view.

## 7. High-Level Assumptions

- The pool of people acting as event resources is relatively stable in size (approx. 20 people), with a typical event drawing 3–5 of them.
- Expertise areas and asset categories will evolve over time and must be maintainable within the application rather than hardcoded.
- Reservation of resources only becomes binding once an event is confirmed; draft events are exploratory and non-binding.
- A person's availability beyond their event reservations — for example vacation, sickness, or other leave — is **provided by an external system** rather than managed here. Availability checks in this module are assumed to take that external availability information into account.
- A single fixed average staff cost rate is a sufficient basis for ROI in this phase; real per-person salary data is not used.

---

*This document defines the business rationale and scope for the Event Resources module. Detailed functional behavior is defined in the accompanying Use Case Specifications, and the underlying data structures are defined in the Data Description document.*
