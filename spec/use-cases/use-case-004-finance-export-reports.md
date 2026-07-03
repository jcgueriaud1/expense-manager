# UC-004: Finance Export Approved Reports

> Finance staff export approved expense reports for accounting and reconciliation.

---

**Goal:** As a finance team member, I want to export approved expense reports so that I can import them into the accounting system and process reimbursements.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Authenticated user with FINANCE role
- **Secondary actors:** Database; file export system

---

## Preconditions

- User is authenticated and has the FINANCE role
- At least one expense report has status APPROVED
- User has navigated to the "Finance Dashboard" or "Export Reports" page

---

## Trigger

Finance user clicks "Export Reports" button or navigates to the export interface.

---

## Main Flow

1. System displays the finance dashboard with a list of all APPROVED expense reports (organization-wide).

2. Each report row shows:
   - Report ID
   - Submitter name and employee ID
   - Total amount
   - Approval date
   - Department/Team (if available)

3. Finance user selects one or more reports using checkboxes (or selects "All Approved").

4. Finance user selects an export format:
   - CSV (comma-separated values)
   - Excel (.xlsx)

5. Finance user clicks "Export" button.

6. System generates the export file with columns:
   - Employee ID / Username
   - Employee Name
   - Report ID
   - Expense Date
   - Category
   - Description
   - Amount
   - Currency
   - Approval Date

7. System downloads the file with a timestamped filename (e.g., `expense-export-2025-01-15.csv`).

8. System displays success message: "Export complete. File downloaded."

9. Finance user can repeat to export different date ranges or filters.

---

## Alternative Flows

### AF-1: Filter Approved Reports

**Branches from:** Main Flow step 1  
**Condition:** Finance user applies filters before export

1. Finance user can filter by:
   - Date range (approval date or expense date)
   - Department/Team
   - Amount range
   - Category

2. System re-displays the list with matching reports.

3. Finance user selects and exports as in Main Flow steps 3–8.

4. Use case continues.

### AF-2: No Approved Reports

**Branches from:** Main Flow step 1  
**Condition:** No reports exist with status APPROVED

1. System displays: "No approved reports available for export."
2. System shows available filters and suggests checking back later or adjusting filters.
3. Use case ends.

### AF-3: View Report Before Export

**Branches from:** Main Flow step 3  
**Condition:** Finance user clicks on a report to view details

1. System displays the full report with all expenses and metadata.
2. Finance user can review and return to the export list.
3. Use case continues.

---

## Postconditions

- **On success:** Export file is generated and downloaded to the user's computer. Report data is unchanged in the system.
- **On failure:** Error message displayed (e.g., file generation failure); user can retry.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-018 | Only reports with status APPROVED can be exported (no drafts, submitted, or rejected reports). |
| BR-019 | Finance can see all approved reports regardless of department or submitter. |
| BR-020 | Export includes denormalized expense-level data (one row per expense, not per report). |
| BR-021 | Exported data is for record-keeping only; changes to the export file do not affect the system. |
| BR-022 | Export files are timestamped and contain no sensitive internal comments or rejection reasons. |

---

## Tests

- [ ] Main Flow (export CSV or Excel) covered (steps 1–9)
- [ ] AF-1 (filter before export) covered
- [ ] AF-2 (no approved reports) covered
- [ ] AF-3 (view report before export) covered
- [ ] BR-018 through BR-022 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| Finance Dashboard / Export Page | Authenticated FINANCE | Table of APPROVED reports with columns: ID, Submitter, Total, Approval Date, Department. Checkboxes for multi-select. |
| Filter Panel | Authenticated FINANCE | Date range picker (approval or expense date), department dropdown, amount range slider, category multi-select. |
| Format Selector | Authenticated FINANCE | Radio buttons: CSV or Excel. |
| Export Button | Authenticated FINANCE | Triggers download after format selection. |
| Success Message | Authenticated FINANCE | Toast/banner confirming download. |
| Empty State | Authenticated FINANCE | Message if no approved reports match filters. |
