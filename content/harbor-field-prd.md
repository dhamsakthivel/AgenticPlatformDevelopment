# Harbor Field Service — product brief

Harbor schedules field technicians for same-day utility and facilities work. Dispatchers own the board. Technicians work from a phone, often with weak signal. Customers see an arrival window, not a live map pin.

## Roles

- Dispatcher: assigns jobs, shifts windows, and watches crew load.
- Field technician: receives the day's jobs, records completion, and works offline.
- Customer: sees the promised arrival window and a change notice when that window moves.
- Supervisor: reviews missed windows, parts shortages, and repeat visits.

## Dispatch board

The board shows today's jobs grouped by crew. Each card shows the site, the promised window, required parts, and a risk flag when the drive time no longer fits the window. A dispatcher can move a job to another crew. The move must record who changed it and why. Jobs with a locked customer promise cannot move until the dispatcher confirms a new window.

## Same-day window changes

When a promised window changes by more than 30 minutes, Harbor notifies the assigned technician and the customer. The technician notification includes the new window, the site address, and whether parts are still reserved. The customer notification states the new window and does not include technician personal phone numbers. If the technician has not acknowledged within 10 minutes, the dispatcher sees an unacknowledged badge.

## Offline job completion

Technicians mark travel, on site, and complete while offline. The device stores the completion note, photos, and parts used, then syncs when the network returns. A completion submitted offline must not create a second visit record when it syncs. If a dispatcher reassigned the job while the technician was offline, the sync keeps the completion and opens a conflict for the supervisor.

## Parts reservation

A job that lists required parts cannot be marked complete until each part is used, substituted, or marked not needed. Substitutions require a reason. Parts shortages raise a risk flag on the dispatch board the morning of the job.

## Permissions

Technicians see only their assigned jobs. Dispatchers see every crew in their depot. Supervisors see missed windows and conflicts across depots they manage. Customer notifications are sent by the system, never by exposing crew contact details.

## Out of scope

Live GPS tracking of technicians, customer chat, and billing invoices are out of scope for this release.
