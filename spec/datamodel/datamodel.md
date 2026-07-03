# Data Model

> Entity definitions and relationships. Evolves as features are added.

## Entities

### User
Represents an authenticated system user with role and team assignment.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| id | Long | ✓ | Primary key |
| username | String | ✓ | Unique, used for login |
| email | String | ✓ | Unique, contact address |
| firstName | String | ✓ | Employee first name |
| lastName | String | ✓ | Employee last name |
| role | Enum (EMPLOYEE, MANAGER, FINANCE, ADMIN) | ✓ | Determines access level |
| manager | FK (User) | | User's direct manager (null for top-level or non-employees) |
| active | Boolean | ✓ | Soft delete; inactive users cannot log in |
| createdAt | LocalDateTime | ✓ | Audit timestamp |
| updatedAt | LocalDateTime | ✓ | Audit timestamp |

**Relationships:**
- User has many ExpenseReports (one-to-many, as submitter)
- User has many ExpenseApprovals (one-to-many, as approver)
- User has many as manager (one-to-many; employees report to this user)

---

### ExpenseCategory
Defines allowed expense types (e.g., Travel, Meals, Equipment).

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| id | Long | ✓ | Primary key |
| name | String | ✓ | Unique; e.g., "Travel", "Meals & Entertainment" |
| description | String | | Brief explanation of what expenses fit here |
| active | Boolean | ✓ | Can be deactivated without deleting history |
| createdAt | LocalDateTime | ✓ | Audit timestamp |

**Relationships:**
- ExpenseCategory has many Expenses (one-to-many)

---

### ExpenseReport
Groups one or more expenses under a single submission with approval status.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| id | Long | ✓ | Primary key |
| submittedBy | FK (User) | ✓ | Employee who submitted the report |
| status | Enum (DRAFT, SUBMITTED, APPROVED, REJECTED) | ✓ | Current workflow state |
| totalAmount | BigDecimal | ✓ | Sum of all expenses in report (denormalized for query efficiency) |
| submittedAt | LocalDateTime | | Null while in DRAFT; set when submitted |
| approvedAt | LocalDateTime | | Null until approved/rejected |
| approvedBy | FK (User) | | Manager/Finance who approved/rejected |
| rejectionReason | String | | Only populated if status is REJECTED |
| description | String | | Employee's summary of business purpose |
| createdAt | LocalDateTime | ✓ | Audit timestamp |
| updatedAt | LocalDateTime | ✓ | Audit timestamp |

**Relationships:**
- ExpenseReport has many Expenses (one-to-many, cascade delete)
- ExpenseReport belongs to User (submittedBy)
- ExpenseReport has optional ExpenseApproval (one-to-one)

---

### Expense
Individual line item within an expense report (e.g., one meal, one flight).

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| id | Long | ✓ | Primary key |
| report | FK (ExpenseReport) | ✓ | Parent report |
| category | FK (ExpenseCategory) | ✓ | Type of expense |
| description | String | ✓ | What was purchased or spent on |
| amount | BigDecimal | ✓ | Amount in company currency |
| currency | String | | e.g., "USD", "EUR" (defaults to company default if not specified) |
| expenseDate | LocalDate | ✓ | When the expense occurred |
| receiptUrl | String | | Path/URL to attached receipt or document |
| createdAt | LocalDateTime | ✓ | Audit timestamp |

**Relationships:**
- Expense belongs to ExpenseReport
- Expense belongs to ExpenseCategory

---

### ExpenseApproval
Audit trail for approval/rejection actions and comments.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| id | Long | ✓ | Primary key |
| report | FK (ExpenseReport) | ✓ | Which report was approved/rejected |
| approver | FK (User) | ✓ | Who approved/rejected |
| action | Enum (APPROVED, REJECTED, COMMENTED) | ✓ | What action was taken |
| comment | String | | Optional approver notes |
| createdAt | LocalDateTime | ✓ | When the action occurred |

**Relationships:**
- ExpenseApproval belongs to ExpenseReport
- ExpenseApproval belongs to User (approver)

---

## Summary of Relationships

| From | To | Cardinality | Notes |
|------|----|-----------  |-------|
| User | ExpenseReport | 1:N | User submits many reports |
| User | User | 1:N | Manager has many subordinates |
| User | ExpenseApproval | 1:N | User approves many reports |
| ExpenseReport | Expense | 1:N | Report contains many expenses (cascade delete) |
| ExpenseReport | ExpenseApproval | 1:N | Report has approval history |
| ExpenseCategory | Expense | 1:N | Category has many expenses |
| Expense | ExpenseCategory | N:1 | Each expense has one category |

---

## Design Notes

- **Status workflow:** DRAFT → SUBMITTED → (APPROVED or REJECTED). Rejected reports can be edited and resubmitted.
- **Denormalized totalAmount:** Stored on ExpenseReport for fast queries and reporting; recalculated after each expense change.
- **Soft deletes:** Users and categories use an `active` flag rather than deletion to preserve audit history.
- **Audit columns:** All entities have `createdAt` and `updatedAt`; sensitive actions (approvals) have their own audit table.
- **Receipt storage:** Stored as URL/path for simplicity; assumes a separate file service or blob storage.
