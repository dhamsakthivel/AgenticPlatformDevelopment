import type { ReactNode } from "react"

import { statusLabel } from "@/lib/format"

const TONE: Record<string, string> = {
  RUNNING: "bg-amber-100 text-amber-950",
  WAITING_FOR_APPROVAL: "bg-sky-100 text-sky-950",
  COMPLETED: "bg-emerald-100 text-emerald-950",
  SUCCEEDED: "bg-emerald-100 text-emerald-950",
  FAILED: "bg-red-100 text-red-950",
  APPROVED: "bg-emerald-100 text-emerald-950",
  REJECTED: "bg-red-100 text-red-950",
  DRAFT: "bg-muted text-foreground",
  PENDING: "bg-sky-100 text-sky-950",
  DECIDED: "bg-emerald-100 text-emerald-950",
  TIMED_OUT: "bg-muted text-foreground",
}

export function StatusChip({ status }: { status: string }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${TONE[status] ?? "bg-muted text-foreground"}`}
    >
      {statusLabel(status)}
    </span>
  )
}

export function Notice({ children }: { children: ReactNode }) {
  return (
    <p
      className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
      role="alert"
    >
      {children}
    </p>
  )
}

export function Loading({ children }: { children: ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>
}
