import { useState } from "react"
import { useQuery } from "@tanstack/react-query"

import { Button } from "@/components/ui/button"
import { Loading, Notice } from "@/components/status-chip"
import { api, type CostReport } from "@/lib/api"
import {
  formatMoney,
  formatPercent,
  formatTokens,
  purposeLabel,
} from "@/lib/format"

export function MetricsPage() {
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")
  const [applied, setApplied] = useState({ from: "", to: "" })
  const report = useQuery({
    queryKey: ["metrics", applied.from, applied.to],
    queryFn: () =>
      api<CostReport>(
        `/api/metrics/costs${queryString(applied.from, applied.to)}`
      ),
  })

  const totalCost =
    report.data?.byDay.reduce(
      (sum, day) => sum + Number(day.costUsd || 0),
      0
    ) ?? 0
  const decided =
    (report.data?.approved ?? 0) +
    (report.data?.rejected ?? 0) +
    (report.data?.edited ?? 0)

  return (
    <div className="flex flex-col gap-8">
      <div className="max-w-2xl">
        <h1 className="text-2xl font-medium">Metrics</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          What the model calls cost, and how often drafts are accepted. An edit
          counts as accepted because the replacement case is approved.
        </p>
      </div>

      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          setApplied({ from, to })
        }}
      >
        <label className="flex flex-col gap-1 text-sm">
          From
          <input
            className="h-9 rounded-lg border bg-background px-2"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          To
          <input
            className="h-9 rounded-lg border bg-background px-2"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </label>
        <Button type="submit">Apply</Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setFrom("")
            setTo("")
            setApplied({ from: "", to: "" })
          }}
        >
          All dates
        </Button>
      </form>

      {report.isLoading ? <Loading>Loading metrics…</Loading> : null}
      {report.isError ? (
        <Notice>
          {report.error instanceof Error
            ? report.error.message
            : "Metrics could not be loaded."}
        </Notice>
      ) : null}

      {report.data ? (
        <>
          <dl className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl border px-4 py-3">
              <dt className="text-xs text-muted-foreground">Model cost</dt>
              <dd className="mt-1 text-xl">{formatMoney(totalCost)}</dd>
            </div>
            <div className="rounded-xl border px-4 py-3">
              <dt className="text-xs text-muted-foreground">
                Average tokens per run
              </dt>
              <dd className="mt-1 text-xl">
                {formatTokens(Math.round(report.data.averageTokensPerRun))}
              </dd>
              <p className="text-xs text-muted-foreground">
                {report.data.runsWithModelCalls} runs called the model
              </p>
            </div>
            <div className="rounded-xl border px-4 py-3">
              <dt className="text-xs text-muted-foreground">Drafts accepted</dt>
              <dd className="mt-1 text-xl">
                {decided ? formatPercent(report.data.acceptanceRate) : "—"}
              </dd>
              <p className="text-xs text-muted-foreground">
                {report.data.approved} approved, {report.data.edited} edited,{" "}
                {report.data.rejected} rejected
              </p>
            </div>
          </dl>

          <section className="flex flex-col gap-2">
            <h2 className="font-medium">By agent</h2>
            {report.data.byPurpose.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No model calls in this range.
              </p>
            ) : null}
            {report.data.byPurpose.length > 0 ? (
              <div className="overflow-x-auto rounded-xl border">
                <table className="w-full min-w-[36rem] text-left text-sm">
                  <thead className="border-b bg-muted/50 text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3 font-medium">Agent</th>
                      <th className="px-4 py-3 font-medium">Calls</th>
                      <th className="px-4 py-3 font-medium">Tokens</th>
                      <th className="px-4 py-3 font-medium">Cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.data.byPurpose.map((row) => (
                      <tr
                        key={row.purpose}
                        className="border-b last:border-b-0"
                      >
                        <td className="px-4 py-3">
                          {purposeLabel(row.purpose)}
                        </td>
                        <td className="px-4 py-3">{row.calls}</td>
                        <td className="px-4 py-3">
                          {formatTokens(row.inputTokens + row.outputTokens)}
                        </td>
                        <td className="px-4 py-3">
                          {formatMoney(row.costUsd)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </section>

          <section className="flex flex-col gap-2">
            <h2 className="font-medium">By day (UTC)</h2>
            {report.data.byDay.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No model calls in this range.
              </p>
            ) : null}
            {report.data.byDay.length > 0 ? (
              <div className="overflow-x-auto rounded-xl border">
                <table className="w-full min-w-[32rem] text-left text-sm">
                  <thead className="border-b bg-muted/50 text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3 font-medium">Day</th>
                      <th className="px-4 py-3 font-medium">Input tokens</th>
                      <th className="px-4 py-3 font-medium">Output tokens</th>
                      <th className="px-4 py-3 font-medium">Cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.data.byDay.map((row) => (
                      <tr key={row.day} className="border-b last:border-b-0">
                        <td className="px-4 py-3">{row.day}</td>
                        <td className="px-4 py-3">
                          {formatTokens(row.inputTokens)}
                        </td>
                        <td className="px-4 py-3">
                          {formatTokens(row.outputTokens)}
                        </td>
                        <td className="px-4 py-3">
                          {formatMoney(row.costUsd)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </section>
        </>
      ) : null}
    </div>
  )
}

function queryString(from: string, to: string) {
  const params = new URLSearchParams()
  if (from) {
    params.set("from", startOfLocalDay(from))
  }
  if (to) {
    params.set("to", startOfNextLocalDay(to))
  }
  const text = params.toString()
  return text ? `?${text}` : ""
}

function startOfLocalDay(isoDate: string) {
  const [year, month, day] = isoDate.split("-").map(Number)
  return new Date(year, month - 1, day).toISOString()
}

function startOfNextLocalDay(isoDate: string) {
  const [year, month, day] = isoDate.split("-").map(Number)
  return new Date(year, month - 1, day + 1).toISOString()
}
