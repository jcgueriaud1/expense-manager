# UC-002: View My Expense Reports

> An employee views a list of their submitted and draft expense reports with filtering and sorting options.

---

**Goal:** As an employee, I want to view all my expense reports (both draft and submitted) so that I can track the status of my reimbursement requests.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Authenticated employee
- **Secondary actors:** Database

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- User has navigated to the "My Reports" or "Expense Reports" page

---

## Trigger

User clicks "My Reports" menu item or navigates to the reports list page.

---

## Main Flow

1. System loads and displays a table/list of expense reports belonging to the authenticated user.

2. Each report row shows:
   - Report ID (link to detail view)
   - Status (DRAFT, SUBMITTED, APPROVED, REJECTED)
   - Total amount
   - Number of expenses in the report
   - Submission date (or "Unsaved" for drafts)
   - Approval date (or empty if not yet approved)

3. System sorts reports by most recent first (descending submission date; drafts first if no submission date).

4. Actor can click on any report ID or row to view full report details.

5. Actor can use a filter menu to show:
   - All reports
   - Draft reports only
   - Submitted reports only
   - Approved reports only
   - Rejected reports only

6. Actor can use a search box to filter by report description or keyword.

7. System displays a "New Report" button to start a new submission.

8. If user has no reports, system displays: "No expense reports yet. Create one to get started."

---

## Alternative Flows

### AF-1: Empty List

**Branches from:** Main Flow step 1  
**Condition:** User has no expense reports

1. System displays: "No expense reports yet. Create one to get started."
2. System shows a "New Report" button.
3. Use case ends.

### AF-2: Filter Reports

**Branches from:** Main Flow step 5  
**Condition:** Actor selects a filter option

1. System re-filters the list to show only reports matching the selected status.
2. System preserves any active search filter.
3. Table updates immediately.
4. Use case continues.

### AF-3: Search Reports

**Branches from:** Main Flow step 6  
**Condition:** Actor types in the search box

1. System filters the table to show only reports where the description or any expense description contains the search term (case-insensitive).
2. System preserves any active status filter.
3. Table updates immediately (or after actor stops typing, depending on debounce).
4. Use case continues.

### AF-4: Report Rejected with Comments

**Branches from:** Main Flow step 2  
**Condition:** A report has been rejected and comments are available

1. System displays a visual indicator (e.g., red badge or warning icon) next to rejected reports.
2. When actor clicks a rejected report row, system navigates to detail view where comments from the approver are visible.
3. Use case continues (see UC-005: View Detailed Report).

---

## Postconditions

- **On success:** List of reports is displayed, filtered and sorted according to user's selections. User can navigate to create or view a report.
- **On failure:** No reports are retrieved (database error); system displays error message and offers refresh option.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-008 | Each employee can only see their own reports; managers and finance see their reports only in this view (see UC-003 for manager/finance approval views). |
| BR-009 | Reports are sorted by submission date (most recent first); drafts appear first since they have no submission date. |
| BR-010 | Status filter includes all five states: All, Draft, Submitted, Approved, Rejected. |
| BR-011 | Search is case-insensitive and matches both report description and line-item expense descriptions. |
| BR-012 | Reports remain visible even after approval/rejection; they are never deleted. |

---

## Tests

- [ ] Main Flow covered (steps 1–8)
- [ ] AF-1 (Empty List) covered
- [ ] AF-2 (Filter Reports) covered
- [ ] AF-3 (Search Reports) covered
- [ ] AF-4 (Rejected with Comments) covered
- [ ] BR-008 through BR-012 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| My Expense Reports List | Authenticated EMPLOYEE | Table/list with columns: ID, Status, Total, Count, Submitted, Approved. Rows are clickable; Status shown with color coding (Draft=gray, Submitted=blue, Approved=green, Rejected=red). |
| Status Filter Dropdown | Authenticated EMPLOYEE | Options: All, Draft, Submitted, Approved, Rejected. Default: All. |
| Search Box | Authenticated EMPLOYEE | Text input; filters by report and expense descriptions. |
| New Report Button | Authenticated EMPLOYEE | Navigates to UC-001. |
| Empty State | Authenticated EMPLOYEE | Message + link to create first report. |
