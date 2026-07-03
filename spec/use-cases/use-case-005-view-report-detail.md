# UC-005: View Report Detail

> A user views the full details of a single expense report including all expenses, metadata, and approval history.

---

**Goal:** As an employee, manager, or finance staff, I want to view the complete details of an expense report so that I can review all expenses, metadata, and approval decisions.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Authenticated user (EMPLOYEE, MANAGER, or FINANCE role)
- **Secondary actors:** Database

---

## Preconditions

- User is authenticated
- User has permission to view the report (employee owns it, manager oversees submitter, or user is FINANCE)
- User has clicked on a report link or row from a list view

---

## Trigger

User clicks on a report ID or row from "My Reports," "Approvals," or "Finance Dashboard."

---

## Main Flow

1. System loads and displays the expense report detail page.

2. Page header shows:
   - Report ID and status badge (color-coded: Draft, Submitted, Approved, Rejected)
   - Submitter name and employee ID
   - Report description
   - Submission date (or "Draft" if no submission date)

3. Page displays a table of all expenses with columns:
   - Category
   - Description
   - Amount
   - Currency
   - Expense Date
   - Receipt (link if available)

4. Page displays total amount (sum of all expenses).

5. If report is SUBMITTED and user is the approver (manager or finance):
   - Page displays approval action panel (see UC-003)
   - Actor can approve, reject, or comment

6. If report is APPROVED or REJECTED:
   - Page displays approval metadata:
     - Approved/Rejected by (approver name)
     - Approval date
     - Rejection reason (if rejected)
   - Page displays approval history / audit trail showing all comments and actions

7. If report is DRAFT and user is the submitter:
   - Page displays "Edit" and "Delete" buttons
   - Actor can return to the form to edit before submission

8. Actor can click "Back to List" to return to the report list view.

---

## Alternative Flows

### AF-1: Permission Denied

**Branches from:** Main Flow step 1  
**Condition:** User is not the submitter, not the approver, and not FINANCE

1. System displays: "You do not have permission to view this report."
2. System navigates back to the user's list view or dashboard.
3. Use case ends.

### AF-2: Report Not Found

**Branches from:** Main Flow step 1  
**Condition:** Report ID is invalid or does not exist

1. System displays: "Report not found."
2. System offers a link to return to the list view.
3. Use case ends.

### AF-3: View Receipt

**Branches from:** Main Flow step 3  
**Condition:** User clicks on a receipt link

1. System opens the receipt file/image in a new tab or modal.
2. If receipt is unavailable (404 or deleted), system displays: "Receipt not available."
3. Use case continues.

### AF-4: Edit Draft Report

**Branches from:** Main Flow step 7  
**Condition:** Submitter clicks "Edit" on a DRAFT report

1. System navigates to the edit form (same as UC-001 "Submit Expense Report").
2. Form is pre-filled with all current expenses and report description.
3. Submitter can modify, add, or delete line items.
4. Submitter can save as draft or submit.
5. Use case ends (refer to UC-001 for form flow).

### AF-5: View Approval History

**Branches from:** Main Flow step 6  
**Condition:** Report has multiple approvals or comments; user clicks "View History"

1. System displays a chronological log of all ExpenseApproval records for this report.
2. Each entry shows:
   - Timestamp
   - Actor (approver or commenter)
   - Action (APPROVED, REJECTED, COMMENTED)
   - Comment text

3. User can scroll through history.
4. Use case continues.

---

## Postconditions

- **On success:** Full report details are displayed with appropriate UI elements based on user role and report status.
- **On permission denied:** Error message displayed; user is redirected to list view.
- **On not found:** Error message displayed; user is offered navigation back to list.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-023 | Employees can only view their own reports in detail. |
| BR-024 | Managers can view reports from their direct reports in detail. |
| BR-025 | Finance can view all reports in detail. |
| BR-026 | Admins can view all reports in detail. |
| BR-027 | Once a report is approved or rejected, it is read-only for the submitter (no edit or delete). |
| BR-028 | Drafts can be edited and resubmitted any number of times until submitted. |
| BR-029 | Approved reports cannot be edited but can be reprinted or exported. |
| BR-030 | All approval actions and comments are audited and visible (if user has permission to view the report). |

---

## Tests

- [ ] Main Flow covered (steps 1–8)
- [ ] AF-1 (permission denied) covered
- [ ] AF-2 (report not found) covered
- [ ] AF-3 (view receipt) covered
- [ ] AF-4 (edit draft) covered
- [ ] AF-5 (approval history) covered
- [ ] BR-023 through BR-030 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| Report Detail Page | Authenticated users with permission | Header with ID, Status (badge), Submitter, Description, Submission Date. Expense table (Category, Description, Amount, Currency, Date, Receipt link). Total amount. |
| Approval Action Panel | MANAGER or FINANCE (if SUBMITTED status) | Approve/Reject buttons, comment field, submit actions. |
| Approval Metadata | All users (if APPROVED or REJECTED) | Approver name, approval date, rejection reason (if applicable). |
| Approval History | All users with report access | Chronological audit trail of all actions and comments. |
| Edit & Delete Buttons | EMPLOYEE (if DRAFT status) | Allows returning to form or deleting draft. |
| Back to List Button | All users | Returns to appropriate list view (My Reports, Approvals, Finance Dashboard). |
| Receipt Link | All users | Clickable link to view attached receipt (or error if unavailable). |
