# Use Case Specifications
## Event Resources Module

**Companion document to:** Business Requirements Document — Event Resources Module
**Status:** Draft v1

---

## About this document

Each use case below describes a specific way a user interacts with the Event Resources module: who does it, why, what happens step by step, and what the outcome is. Where relevant, a use case also includes notes on how it's intended to work in the user interface — these UI notes describe the original idea for how things could work and how they tie together, not a fixed requirement to build exactly as described.

## Use Case Index

| ID | Use Case | Primary Actor |
|---|---|---|
| UC-01 | Register a Person | Admin |
| UC-02 | Register Equipment | Admin |
| UC-03 | Maintain Expertise Areas and Asset Categories | Admin |
| UC-04 | Create a Draft Event | User |
| UC-05 | Fetch Event Details via AI Lookup | User |
| UC-06 | Check Resource Availability for a Draft Event | User |
| UC-07 | Confirm an Event and Reserve Resources | User |
| UC-08 | View the Resource Planning (Gantt) Overview | User |
| UC-09 | Close Out an Event (Equipment Return & Impact Metrics) | User |
| UC-10 | Change an Event's Lifecycle Stage or Cancel It | User |
| UC-11 | Resolve a Resource Conflict | User |
| UC-12 | Generate a Resource or Event Report | Management |
| UC-13 | Generate a Return-on-Investment Report | Management |

---

## UC-01: Register a Person

**Actor:** Admin

**Goal:** Add a person to the pool of people who can be assigned to events as a resource.

**Preconditions:** None.

**Main flow:**
1. The user opens the People section and chooses to add a new person.
2. The user enters the person's name.
3. The user tags the person with one or more expertise areas from the shared, maintainable expertise area list, reflecting the expertise or specialization they bring (e.g. Java, web components, architecture, sales, UX). A person may hold multiple expertise areas at once — this replaces the need for a separate job title field.
4. The user saves the person record.

**Postconditions:** The person appears in the pool of available resources and can be considered for event assignment and availability checks. The expertise this person brings becomes available to any event they attend, without needing a per-event role to be assigned.

**Notes:** The pool of people is expected to be relatively small (around 20 people company-wide), so this is a low-frequency, low-volume administrative task rather than a bulk-management flow.

---

## UC-02: Register Equipment

**Actor:** Admin

**Goal:** Add a piece of equipment to the system so it can be tracked, reserved, and shown in availability views.

**Preconditions:** Relevant asset category exists (see UC-03), or the user creates one as part of this flow.

**Main flow (tracked assets — e.g. computers, presentation tech, booth furniture):**
1. The user opens the Equipment section and chooses to add a new tracked asset.
2. The user selects a category (e.g. computer, display tech, booth furniture) from the maintainable category list.
3. The user enters an identifier to uniquely tell this specific item apart from others — this doesn't have to be a formal serial number; a made-up label works too (e.g. "green desk" for a piece of booth furniture that has no serial number of its own).
4. The asset is set to **In Use** status by default. The user can later set it to **Out of Use** if it becomes lost, broken, retired, or otherwise removed from circulation.
5. The user may add free-text notes about the item.
6. The user saves the asset.

**Alternate flow (semi-tracked promotional stock — e.g. branded t-shirts):**
1. The user chooses to add a promotional stock item instead of a tracked asset.
2. The user names the item and does not assign an individual identifier (no individual identity is tracked).
3. Quantities for this item are set per event, and running totals of amounts handed out are tracked over time (see UC-04/UC-09).

**Postconditions:** The equipment item is available to be included when planning and confirming events, at the tracking level appropriate to its type.

**Notes:** A tracked asset carries a simple **In Use / Out of Use** status, which reflects whether the item is in circulation at all — not whether it's free on a given date. Only In Use assets are offered when assigning equipment to an event (UC-07). Whether an In Use asset is free at a particular time is a separate matter, worked out from its event reservations rather than stored on the asset; the word "availability" is deliberately avoided for the status to keep these two ideas distinct. Where an asset physically is at any point in time is not stored either — it can be seen by filtering the planning view (UC-08) to that one asset.

---

## UC-03: Maintain Expertise Areas and Asset Categories

**Actor:** Admin

**Goal:** Keep the shared lists of expertise areas (for people) and asset categories (for tracked assets) up to date as the department's needs evolve.

**Preconditions:** None.

**Main flow:**
1. The user opens the relevant management screen (Expertise Areas or Asset Categories).
2. The user adds, renames, or retires an entry (e.g. adding a new expertise area such as "cloud infrastructure," or a new asset category such as "audio equipment").
3. Changes are reflected immediately wherever these lists are used elsewhere in the system (person profiles, equipment records, filters).

**Postconditions:** The updated list is available for use across the module without requiring a technical change to the application.

---

## UC-04: Create a Draft Event

**Actor:** User

**Goal:** Record a potential event the department is considering attending, without committing any resources to it.

**Preconditions:** None.

**Main flow:**
1. The user creates a new event and enters at minimum a name (e.g. "JavaOne 2026").
2. The user may optionally trigger UC-05 (AI Lookup) to pre-fill logistics details, or enter dates, venue, and location manually.
3. The event is saved in **Draft / Proposed** status. No people or equipment are reserved at this point — the event exists purely for planning purposes.
4. The user may now use UC-06 to explore what people and equipment would be available if this event were confirmed.

**Postconditions:** A draft event exists and appears in planning views (see UC-08), but does not hold any resources.

**UI notes:** Creating a draft event is intended to be a very low-friction action — essentially just naming the event — so that the department can capture "events we might go to" as soon as they come up, well before any commitment decision is needed.

---

## UC-05: Fetch Event Details via AI Lookup

**Actor:** User

**Goal:** Reduce manual data entry by having an AI agent fetch objective, publicly known logistics details for a well-known event.

**Preconditions:** An event record exists (typically just-created, per UC-04) with at least a name entered.

**Main flow:**
1. From the event record, the user presses a "Fetch details" action. Helper text near the name field guides the user to enter an accurate, specific value (e.g. including the year or edition, such as "JavaOne 2026" rather than just "JavaOne").
2. The AI agent searches for the named event and retrieves factual logistics details — dates, venue, and location.
3. The retrieved details are pre-filled into the event record as suggested values.
4. The user reviews the pre-filled details and confirms, edits, or discards them before saving.

**Alternate flow — event not found:**
- If the agent finds no matching event, a small notification informs the user, who can refine the name or enter the details manually.

**Alternate flow — multiple possible matches:**
- If the agent finds several possible matches (e.g. a recurring conference across different years or cities), a small notification prompts the user to provide more specific search keywords to narrow it down.

**Postconditions:** The event record is populated with logistics details, subject to the user's review and confirmation; or the user is informed that automatic lookup could not be completed and can proceed manually.

**Explicitly out of scope for this use case:** The AI agent does not suggest or infer anything beyond objective logistics facts — it does not recommend whether to attend, suggest which people or equipment to bring, estimate attendance numbers, or draw on past years' data. Its role is strictly limited to confirmed, factual lookup.

---

## UC-06: Check Resource Availability for a Draft Event

**Actor:** User

**Goal:** Understand what people and equipment would be available if the department committed to a draft event, before making that commitment.

**Preconditions:** A draft event exists with at least a tentative date range.

**Main flow:**
1. The user opens the draft event and views its proposed date range against the resource planning overview (see UC-08).
2. The system shows which people and which equipment are free during that window, based on existing confirmed events and other drafts. A person's availability also reflects any personal unavailability (leave, sickness, etc.) provided by the external availability system, so someone on leave does not show as free.
3. If another draft event overlaps in time and shares one or more of the same people or equipment, the system highlights this as a **soft conflict warning** — it does not block anything, since neither draft has committed resources yet. The user decides which draft to prioritize, if any.
4. If a person or piece of equipment is required at this draft event shortly after (or before) being needed at another event in a different location, the system highlights this as a **location transfer warning** — flagging that the realistic travel time between the two locations may not allow the transfer (for example, an event ending in Barcelona on Tuesday followed by an event starting in Germany on Wednesday). This is a warning for the user to judge, not an automatic block.
5. Based on this view, the user decides whether to proceed toward confirming the event (UC-07), adjust who/what they intend to bring, or abandon the draft.

**Postconditions:** No resources are reserved as a result of this use case — it is purely informational. The user is equipped to make an informed confirm/don't-confirm decision.

**UI notes:** This is the primary use of the filterable Gantt-style planning view (UC-08) — the user looks at the proposed event's timeframe overlaid on people/equipment lanes to visually spot both availability and warnings.

---

## UC-07: Confirm an Event and Reserve Resources

**Actor:** User

**Goal:** Commit to attending an event, locking in the specific people and equipment that will be sent.

**Preconditions:** A draft event exists. Availability has typically already been reviewed (UC-06).

**Main flow:**
1. The user moves the event from **Draft / Proposed** to **Confirmed** status.
2. The user selects the specific people attending (typically 3–5 from the pool of ~20). No per-event role is assigned to them — the expertise each person brings is simply available to the event through their profile.
3. The user selects the specific equipment to bring: tracked assets (only those with In Use status are offered, chosen by their identifier) and planned quantities of semi-tracked promotional stock.
4. The user optionally jots down a free-form to-do list of ad hoc items to remember for the event (e.g. chocolate, or anything else to buy locally) — this is just a reminder list on the event, not a tracked inventory.
5. The user may optionally link specific pieces of tracked equipment to specific people attending (e.g. "this laptop travels with Anna"). This linkage is optional and not required — equipment may simply travel with the group as a shared pool.
6. The user notes, as free text on the event, which presentations are planned to be given there.
7. Upon confirmation, the selected people and tracked equipment become **reserved** for the event's time window — they will now show as unavailable in availability checks (UC-06) for any other draft or confirmed event overlapping that window.
8. If any selected person or asset is already reserved by another confirmed event overlapping this window, the confirmation is **not blocked** — instead, both affected events are flagged with a persistent **conflict indicator** (error styling and an explanatory message). The event can be confirmed while in conflict, but the user is expected to resolve it (see UC-11). Nothing is reassigned or removed automatically.
9. The event's finalized logistics, attendee list, equipment list, ad hoc to-do list, and presentation notes are saved.

**Postconditions:** The event is confirmed. Its people and equipment are reserved for its date range and will show as unavailable elsewhere. If the confirmation introduced an overlap with another confirmed event, both events now carry a conflict indicator awaiting resolution. The event appears in the planning overview as a committed allocation rather than a tentative one.

---

## UC-08: View the Resource Planning (Gantt) Overview

**Actor:** User

**Goal:** Get a visual, at-a-glance understanding of how people and equipment are allocated across time.

**Preconditions:** At least one event (draft or confirmed) exists.

**Main flow:**
1. The user opens the planning overview, which by default shows a high-level, timeline-based (Gantt-style) view of all people and/or equipment, with bars indicating where each is committed to an event.
2. The user filters the view down as needed — for example, to a specific resource type (people vs. equipment), a specific expertise area (e.g. only people with the "web components" tag), a specific asset category, or a specific individual or item.
3. The user can visually identify gaps (availability) and overlaps (conflicts or transfer-time issues, as flagged per UC-06) directly on the timeline.
4. The user can click into a specific bar/allocation to jump to the related event record.

**Postconditions:** None (read-only, informational use case) — though it commonly leads into UC-06 or UC-07.

**UI notes:** This is the anchor view for planning. It starts broad (everything, across all time) and drills down by resource type, expertise area, or category as the user narrows their question — e.g. "show me only presentation gear" or "show me only people with Kubernetes expertise."

---

## UC-09: Close Out an Event (Equipment Return & Impact Metrics)

**Actor:** User

**Goal:** Wrap up a confirmed event after it has taken place — returning equipment, recording its condition, and capturing how impactful the event was.

**Preconditions:** The event's date range has passed and it is in Confirmed status.

**Main flow:**
1. The user opens the event and initiates close-out.
2. For each tracked asset that was taken, the user marks it as checked back in, which frees it up for reservation at future events, and optionally adds or updates free-text notes (e.g. condition issues).
3. For semi-tracked promotional stock, the user records how many units were actually handed out at the event, which updates the running yearly total for that item.
4. The user records general remarks about the event.
5. The user records impact metrics for the event — most notably the number of contacts or leads made (which may be sourced from an ID badge scanner), to help gauge how impactful attending the event was.
6. The user moves the event to **Past / Archived** status.

**Postconditions:** All tracked equipment involved is checked back in and freed up for future reservation. The event's full record — logistics, attendees, equipment used, remarks, and impact metrics — is retained for future reference and reporting (UC-12, UC-13).

**Notes:** Presentations given at the event are simply the free-text list noted on the event at confirmation (UC-07) — they are not treated as a variable outcome to verify or re-record at close-out; if a presentation was planned, it is taken as having been delivered.

---

## UC-10: Change an Event's Lifecycle Stage or Cancel It

**Actor:** User

**Goal:** Move an event to a different lifecycle stage — including backward — or cancel it, keeping resource reservations correct in each case.

**Preconditions:** The event exists in any stage.

**Main flow (move between stages):**
1. The user opens the event and selects a new lifecycle stage.
2. The lifecycle can flow in **both directions** — for example, a Confirmed event can be returned to Draft (which releases its reservations, since drafts hold none), or a Draft can be moved to Confirmed (which reserves resources, per UC-07).
3. The system updates reservations to match the new stage and re-evaluates any conflict indicators accordingly.

**Alternate flow (cancel an event that has not yet started):**
1. The user cancels the event before its start date.
2. The event moves to **Cancelled** status and its reservations are **released** — the people and equipment become free again for that window, and any conflict indicators that existed only because of this event are cleared.

**Alternate flow (cancel an event that has already started):**
1. The user cancels the event on or after its start date (the people and equipment were already physically at the location).
2. The event moves to **Cancelled** status but its reservations are **retained for the original dates**. Because those resources are treated as still committed for that window, any attempt to use the same resources within that timeframe raises a conflict indicator (see UC-11).

**Postconditions:** The event is in its new stage (or Cancelled). Reservations and conflict indicators reflect the rules above. No data is deleted — a cancelled event's record is retained.

**Notes:** "Effectively removed" for a not-yet-started cancelled event means its hold on resources is gone, not that the record is deleted; the event remains visible as Cancelled.

---

## UC-11: Resolve a Resource Conflict

**Actor:** User

**Goal:** Clear a persistent conflict flagged when the same person or asset is committed to two overlapping events.

**Preconditions:** At least one event carries a conflict indicator (raised per UC-07 or UC-10).

**Main flow:**
1. The user opens an event showing a conflict indicator (error styling and an explanatory message identifying the clashing resource and the other event involved).
2. The user resolves it by adjusting one of the events — for example, removing or swapping the contested person or asset on one event, changing dates, or cancelling/returning one event to draft.
3. Once the overlap no longer exists, the system clears the conflict indicator from both affected events.

**Postconditions:** The conflict indicator is cleared once the underlying overlap is gone. Nothing was ever changed automatically — every adjustment was made explicitly by the user.

**Notes:** The system never auto-resolves conflicts by reassigning or removing resources; its role is only to surface the conflict clearly and persistently until a human resolves it.

---

## UC-12: Generate a Resource or Event Report

**Actor:** Management

**Goal:** Get a report on resource usage — either for a single event or aggregated across multiple events over a period.

**Preconditions:** At least one past event exists with recorded data.

**Main flow:**
1. The user opens the reporting section and chooses what to report on — for example, promotional stock distributed, equipment usage, or people/time committed.
2. The user chooses the scope: a single event, or a set of events within a date range (e.g. "all events in the last 12 months").
3. The system generates the report — for example, total t-shirts distributed across all events this year, or total person-days spent attending events in a quarter.
4. The user reviews the report on screen and may export or share it.

**Postconditions:** None (read-only, informational use case).

---

## UC-13: Generate a Return-on-Investment Report

**Actor:** Management

**Goal:** Compare an event's (or set of events') impact against the resources spent on it, to understand which events are worth attending.

**Preconditions:** At least one closed (Past) event exists with recorded impact metrics (UC-09) and one or more related expense items in the Travel Expense module.

**Main flow:**
1. The user opens the ROI reporting view and selects an event, or a set of events over a period.
2. The system reads, for each closed event, the related expense items from the Travel Expense module. That module returns, per participant, an object containing the employee, the trip duration, and the summed expenses for that participant.
3. The system computes the resource cost as: the summed travel expenses across participants, plus the summed participant time multiplied by a single **fixed average staff cost rate** (an admin-configured constant standing in for the average cost of an employee's time — real salaries are not used in this phase).
4. The system presents this cost against the event's impact metrics (e.g. contacts/leads made), giving a rough return-on-investment picture per event, or aggregated across the selected set.
5. The user reviews the report and may export or share it.

**Postconditions:** None (read-only, informational use case).

**Notes:** This use case relies on a narrow, read-only feed from the Travel Expense module (per-participant employee, trip duration, and summed expenses per closed event) and on the configured average staff cost rate. These simplifying assumptions are recorded in the Business Requirements Document, Sections 4.1 and 7. The precise mechanics of the read-only feed remain a dependency to confirm with that module's owners.

---

*Data structures referenced throughout (Person, Equipment, Event, Expertise Area, etc.) are defined in detail in the accompanying Data Description document.*
