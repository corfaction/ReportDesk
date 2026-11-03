# ReportDesk

Paper plugin for submitting and reviewing player reports. Reports are stored persistently in `plugins/ReportDesk/reports.db` (SQLite); the database is created on the first server start.

## Commands

- `/report <player> <reason>` — submit a report. Reasons may contain spaces.
- `/report review` — open the staff review menu (permission: `reportdesk.staff`). Reports are shown oldest first; use the arrows to move through the queue and the buttons to accept or deny.
- `/report list [page]` — list open reports (permission: `reportdesk.staff`).
- `/report accept <id>` — close a report as accepted (permission: `reportdesk.staff`).
- `/report deny <id>` — close a report as denied (permission: `reportdesk.staff`).

Staff with `reportdesk.staff` receive new report alerts. The permission defaults to operators.

## Configuration

`plugins/ReportDesk/config.yml` controls the submission cooldown, self-reporting, whether reports may target unknown players (`settings.allow_unknown_players`), reason length, and page size. Unknown targets are stored by name using an offline UUID. Restart the server after changing it.

## Build

Build with Gradle and install `build/libs/ReportDesk-1.0-SNAPSHOT.jar` in the server's `plugins` directory. The build uses Shadow to include the SQLite JDBC driver, so no separate database driver is needed.

