# Project Context

> High-level context for the project: the problem being solved, who it's for, what's in scope, and what constraints apply.

## 1. Vision

The Vaadin Expense Reporter is an internal tool that enables Vaadin Oy employees to submit, manage, and report business expenses. The application demonstrates a production-grade Vaadin implementation with real authentication, role-based access control, database persistence, and deployment patterns — serving as both a functional expense system and a reference implementation for realistic Vaadin applications.

Success means employees can easily submit and track expense reports, managers can approve or reject submissions, and finance can export data for accounting systems. Beyond functionality, the system must expose and solve the real architectural challenges (security, state management, database design, and deployment) that toy projects hide.

## 2. Users

- **Employee (authenticated):** Can submit new expense reports (single or multiple expenses per report), view their own submitted reports and approval status, edit draft reports, and receive notifications on status changes.
- **Manager (authenticated, role-based):** Can view and approve/reject expense reports submitted by their team members, add comments to reports, and see a list of pending approvals.
- **Finance (authenticated, role-based):** Can view all reports regardless of team, export approved reports for accounting, and track expense categories and spending trends.
- **System Administrator (authenticated, role-based):** Can manage users, assign roles, and configure system settings (e.g., approval workflows, expense categories).

## 3. Constraints

- **Authentication:** All features require login via Spring Security. No anonymous access to reports or sensitive data.
- **Authorization:** Role-based access control enforces that employees only see their own data (and managers see their team, finance sees all).
- **Database:** Real persistent storage using a relational database (configured in `pom.xml` and `application.properties`).
- **Deployment:** Must be deployable as a Docker container and runnable in a staging/production environment similar to actual internal tools.
- **No external integrations:** Approval workflows, notifications, and reporting are all within the application — no third-party APIs for payments, email, or compliance.
- **Realistic data volume:** System should handle hundreds of employees and thousands of expense records without artificial simplifications.

---

# Related Documents

- [Spec README](README.md) — process overview and workflow
- [Architecture](architecture.md) — technology stack and application structure
- [Design System](design-system.md) — theme, component usage, and visual standards
- [Data Model](datamodel/datamodel.md) — entity definitions and relationships
- [Use Cases](use-cases/) — feature specifications
