const HALIFAX = "America/Halifax"

export function formatWhen(value: string | null | undefined) {
  if (!value) {
    return "—"
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return "—"
  }
  return new Intl.DateTimeFormat("en-US", {
    timeZone: HALIFAX,
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date)
}

export function formatDuration(
  startedAt: string | null | undefined,
  completedAt?: string | null
) {
  if (!startedAt) {
    return "—"
  }
  const start = Date.parse(startedAt)
  const end = completedAt ? Date.parse(completedAt) : Date.now()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
    return "—"
  }
  const seconds = Math.round((end - start) / 1000)
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}

export function formatMoney(value: number | string | null | undefined) {
  if (value == null || value === "") {
    return "—"
  }
  const amount = typeof value === "number" ? value : Number(value)
  if (!Number.isFinite(amount)) {
    return "—"
  }
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 4,
    maximumFractionDigits: 4,
  }).format(amount)
}

export function formatTokens(value: number) {
  return new Intl.NumberFormat("en-US").format(value)
}

export function formatPercent(value: number) {
  return new Intl.NumberFormat("en-US", {
    style: "percent",
    maximumFractionDigits: 0,
  }).format(value)
}

export function statusLabel(status: string) {
  switch (status) {
    case "RUNNING":
      return "Working"
    case "WAITING_FOR_APPROVAL":
      return "Waiting for you"
    case "COMPLETED":
      return "Completed"
    case "FAILED":
      return "Failed"
    case "SUCCEEDED":
      return "Done"
    case "APPROVED":
      return "Approved"
    case "REJECTED":
      return "Rejected"
    case "DRAFT":
      return "Draft"
    case "PENDING":
      return "Pending"
    case "DECIDED":
      return "Decided"
    case "TIMED_OUT":
      return "Timed out"
    default:
      return status
  }
}

export function purposeLabel(purpose: string | null | undefined) {
  switch (purpose) {
    case "story-analysis":
      return "Story analyst"
    case "test-design":
      return "Test designer"
    case "embed":
      return "Rule book search"
    default:
      return purpose || "Model"
  }
}
