# Data Description
## Event Resources Module

**Companion document to:** Business Requirements Document & Use Case Specifications — Event Resources Module
**Status:** Draft v1

---

## About this document

This document describes the core pieces of information ("entities") the Event Resources module needs to keep track of. For each entity, there's a plain-language description of what it is and what it holds, followed by a structured table listing its fields for a more technical reader. A final section describes, in plain language, how these entities connect to one another.

This is a conceptual description, not a database design — it's meant to capture what needs to be tracked and why, ahead of any technical design work.

---

## 1. Person

**In plain language:** A Person represents one of the roughly twenty individuals in the pool who can be sent to events. Each person has a name and one or more expertise areas describing the expertise or specialization they bring (such as Java, web components, architecture, sales, or UX — a person can hold several). There is no separate job title field, and no per-event role — expertise areas capture what matters for staffing an event, and the expertise a person brings simply becomes available to any event they attend. A person's availability beyond their event reservations (leave, sickness, etc.) is not stored here — it comes from an external availability system.

| Field | Type | Description |
|---|---|---|
| Person ID | Identifier | Unique identifier for the person |
| Name | Text | The person's name |
| Expertise Areas | List of references (Expertise Area) | One or more expertise area tags reflecting specialization; multi-select |

---

## 2. Expertise Area

**In plain language:** An Expertise Area is one entry in a shared, company-maintained list of specializations that can be applied to people, reflecting the expertise or specialization they bring to an event — for example, Java, web components, architecture, sales, or UX. This replaces the need for a job title: what matters for staffing an event is the specific expertise someone has, not their formal role. This list is not fixed; admins can add, rename, or retire entries as the department's needs change. Multiple people can share the same expertise area, and a single person can carry several.

| Field | Type | Description |
|---|---|---|
| Expertise Area ID | Identifier | Unique identifier for the expertise area |
| Name | Text | e.g. "Java", "Architecture", "Sales" |

---

## 3. Asset Category

**In plain language:** An Asset Category groups tracked equipment into types — such as computer, display tech, or booth furniture. Like expertise areas, this is a shared, admin-maintainable list, so new categories (e.g. "audio equipment") can be added later without any technical change.

| Field | Type | Description |
|---|---|---|
| Category ID | Identifier | Unique identifier for the category |
| Name | Text | e.g. "Computer", "Display Tech", "Booth Furniture" |

---

## 4. Tracked Asset

**In plain language:** A Tracked Asset is a specific, individually identifiable piece of company-owned equipment — for example, one particular booth laptop, a display screen, or a backdrop stand. Each tracked asset belongs to a category and has its own identifier so it can be told apart from identical-looking items — this doesn't need to be a formal manufacturer serial number; a simple made-up label works too (e.g. "green desk" for a piece of booth furniture with no serial number of its own). Free-text notes can be attached, for example to record a condition issue. Each asset also carries a simple **In Use / Out of Use** status: In Use means the item is in circulation and can be assigned to events; Out of Use means it's lost, broken, retired, or otherwise removed from circulation, and it won't be offered when assigning equipment. This status is deliberately *not* called "availability" — whether an In Use asset is free on a given date is a separate matter, worked out from its event reservations. Where an asset physically is at any moment isn't stored either; it can be seen by filtering the planning view to that one asset.

| Field | Type | Description |
|---|---|---|
| Asset ID | Identifier | Unique identifier for the asset record |
| Category | Reference (Asset Category) | What kind of equipment this is |
| Identifier | Text | A label uniquely identifying this specific physical item — a serial number where one exists, or a made-up label otherwise (e.g. "green desk") |
| Status | Text (from list) | In Use or Out of Use — whether the item is in circulation at all (not whether it's free on a given date) |
| Notes | Text (optional, free-form) | Condition notes or other remarks |

---

## 5. Promotional Stock Item

**In plain language:** A Promotional Stock Item is a semi-tracked type of branded giveaway, such as t-shirts. These aren't tracked individually — there's no per-item identifier — but for each event, a planned quantity is recorded (e.g. "150 t-shirts to Barcelona"), and once the event is closed out, the actual quantity handed out is logged. This feeds a running total of how many have been distributed over time.

| Field | Type | Description |
|---|---|---|
| Item ID | Identifier | Unique identifier for the promotional item type |
| Name | Text | e.g. "Company T-Shirt" |
| Running Total Distributed | Number | Cumulative quantity handed out across all events, updated after each event close-out |

---

## 6. Event

**In plain language:** An Event represents an exhibition or event the company might attend, is attending, or has attended. An event moves through a lifecycle: Draft/Proposed (just a possibility, nothing reserved), Confirmed (committed to, with people and equipment reserved), Past/Archived (the event has happened and been closed out), and Cancelled (called off). The lifecycle is bidirectional — an event can move backward as well as forward (e.g. Confirmed back to Draft). Cancelling behaves differently depending on timing: cancelling before the event starts releases its reservations; cancelling after it has started retains them for the original dates (the resources were physically there), so reusing those same resources in that window raises a conflict. Separately, an event may carry a **conflict flag** whenever one of its reserved people or assets overlaps with another event — this flag sits alongside the status (an event can be, say, Confirmed *and* in conflict) and must be resolved by the user. An event holds its name, dates, venue, and location — filled in manually or fetched via the AI lookup. At confirmation it also holds a free-text list of planned presentations and a free-form ad hoc to-do list (neither is a structured object, just notes). Once closed out it also holds general remarks and impact metrics, such as the number of contacts or leads made.

| Field | Type | Description |
|---|---|---|
| Event ID | Identifier | Unique identifier for the event |
| Name | Text | e.g. "JavaOne 2026" |
| Status | Text (from list) | Draft / Proposed, Confirmed, Past / Archived, Cancelled |
| In Conflict | Yes/No | Whether the event currently has an unresolved resource conflict (sits alongside Status) |
| Start Date | Date | Event start date |
| End Date | Date | Event end date |
| Venue | Text | Venue name |
| Location | Text | City/country or address |
| Presentations | Text (optional, free-form) | Free-text list of presentations planned for the event, noted at confirmation |
| Ad Hoc To-Do List | Text (optional, free-form) | Free-form reminder list of ad hoc items to bring or buy locally (e.g. chocolate); not quantity-tracked |
| Remarks | Text (optional, free-form) | General notes logged at close-out |
| Contacts/Leads Made | Number (optional) | Impact metric logged at close-out, e.g. from badge scans |

---

## 7. Event Assignment (Person ↔ Event)

**In plain language:** An Event Assignment records that a particular person is attending a particular confirmed event. This is what actually reserves that person's time for the event's dates. There is no per-event role or function — the expertise that person brings is simply available to the event through their profile.

| Field | Type | Description |
|---|---|---|
| Assignment ID | Identifier | Unique identifier for the assignment |
| Event | Reference (Event) | The event this assignment relates to |
| Person | Reference (Person) | The person attending |

---

## 8. Equipment Reservation (Equipment ↔ Event)

**In plain language:** An Equipment Reservation records that a particular tracked asset (or a quantity of a promotional stock item) is allocated to a particular confirmed event. For tracked assets, it may optionally be linked to a specific person attending that event — for example, "this laptop travels with Anna" — though this link is entirely optional; equipment can just as well travel as a shared pool with no individual assignment.

| Field | Type | Description |
|---|---|---|
| Reservation ID | Identifier | Unique identifier for the reservation |
| Event | Reference (Event) | The event this reservation relates to |
| Tracked Asset | Reference (Tracked Asset), optional | The specific asset reserved (if applicable) |
| Promotional Stock Item | Reference (Promotional Stock Item), optional | The stock item reserved (if applicable) |
| Planned Quantity | Number, optional | Planned quantity, for promotional stock items |
| Actual Quantity Distributed | Number, optional | Logged at event close-out, for promotional stock items |
| Assigned Person | Reference (Person), optional | Optional link to a specific person carrying/responsible for this asset |

---

## 9. Trip Expense (external reference)

**In plain language:** A Trip Expense is not owned by this module — it lives in the Travel Expense module. A closed event here relates to one or more expense items there. For ROI reporting, this module reads a narrow, read-only feed: for each closed event, the Travel Expense module returns one entry per participant containing that employee, the duration of their trip, and the summed expenses for that participant. This module does not store or duplicate the underlying expense detail; it only reads what it needs to compute ROI. Staff-time cost is then estimated from trip duration multiplied by a single fixed average staff cost rate (an admin-configured constant), since real salaries are not held anywhere in the system.

| Field | Type | Description |
|---|---|---|
| Employee | Reference (Person) | The participant the expense entry relates to |
| Event | Reference (Event) | The closed event these expenses relate to |
| Trip Duration | Duration | How long this participant's trip lasted (used with the average staff cost rate to estimate time cost) |
| Summed Expenses | Currency | Total travel expenses for this participant on this event |

*Note: this represents the read-only interface provided by the Travel Expense module. The precise mechanics of that feed are a dependency still to be confirmed with that module's owners (see Business Requirements Document, Sections 4.1 and 4.2). The average staff cost rate is a separate admin-configured setting, not part of this feed.*

---

## How the Entities Connect

In plain language:

- A **Person** can hold several **Expertise Areas**, and each Expertise Area can apply to several people. The expertise available at an event comes entirely from whichever people attend — there is no separate per-event role.
- An **Event** holds a simple free-text list of the presentations planned for it — this isn't linked to structured Person records, so it doesn't imply anything about who specifically can give a given talk.
- An **Event** also holds its own free-form ad hoc to-do list — this is just text on the event, not a separate tracked item or entity.
- **Tracked Assets** each belong to one **Asset Category**, and a category can cover many items. Only assets with In Use status are offered when assigning equipment to an event.
- An **Event** starts as a Draft and gains real reservations only once Confirmed. The lifecycle is bidirectional (it can return to Draft, releasing reservations) and includes a Cancelled state. Cancelling before the event starts releases its reservations; cancelling after it starts retains them for the original dates.
- Any **Event** may carry a conflict flag when one of its reserved people or assets overlaps with another event's reservation for the same window. The flag is raised on both affected events and cleared only when a user resolves the overlap — never automatically.
- A Confirmed **Event** has one or more **Event Assignments**, each simply linking one Person to that event. A Person can be assigned to many events over time; overlapping assignments are allowed but raise a conflict flag.
- A Confirmed **Event** has one or more **Equipment Reservations**, each linking a Tracked Asset or a Promotional Stock Item to that event. Overlapping reservations of the same Tracked Asset are allowed but raise a conflict flag; a Promotional Stock Item can be reserved (with a planned quantity) across many events freely.
- An **Equipment Reservation** for a Tracked Asset may optionally also link to a specific Person attending the same event — but doesn't have to.
- Once an Event is closed out, its Equipment Reservations record actual quantities distributed (for promotional stock) and the Event itself gains Remarks and Contacts/Leads Made.
- A closed **Event** relates to one or more **Trip Expense** entries in the Travel Expense module. This module reads a per-participant feed (employee, trip duration, summed expenses) from there for ROI reporting rather than storing that data itself.

---

*This document is intended to be read alongside the Business Requirements Document (for the why) and the Use Case Specifications (for the how).*
