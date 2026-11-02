import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Loading, Notice, StatusChip } from "@/components/status-chip"
import { STORIES, storyTitle } from "@/data/stories"
import { ApiError, api, type CostReport, type Run } from "@/lib/api"
import { formatDuration, formatMoney, formatWhen } from "@/lib/format"

const FLOW = [
  {
    title: "Story analyst",
    body: "Reads the story and the rule book, then writes a review: what can be tested, what is unclear, and a risk score.",
  },
  {
    title: "Test designer",
    body: "Turns that review into draft test cases and stores them as drafts. They are not final yet.",
  },
  {
    title: "Your decision",
    body: "You approve, reject, or edit every draft. The run waits until you do.",
  },
  {
    title: "Finished",
    body: "Approved drafts stay approved. Rejected drafts stay rejected.",
  },
]

export function RunsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [storyId, setStoryId] = useState("ENR-002")
  const [triggeredBy, setTriggeredBy] = useState("sam")
  const [formError, setFormError] = useState<string | null>(null)
  const [conflictRunId, setConflictRunId] = useState<string | null>(null)

  const runs = useQuery({
    queryKey: ["runs"],
    queryFn: () => api<Run[]>("/api/runs"),
    refetchInterval: (query) =>
      query.state.data?.some(
        (run) =>
          run.status === "RUNNING" || run.status === "WAITING_FOR_APPROVAL"
      )
        ? 4000
        : false,
  })
  const costs = useQuery({
    queryKey: ["metrics", "", ""],
    queryFn: () => api<CostReport>("/api/metrics/costs"),
  })
  const costByRun = new Map(
    costs.data?.byRun.map((row) => [row.runId, row.costUsd]) ?? []
  )

  const start = useMutation({
    mutationFn: () =>
      api<Run>("/api/runs", {
        method: "POST",
        body: JSON.stringify({
          storyId,
          triggeredBy: triggeredBy.trim() || "sam",
        }),
      }),
    onSuccess: async (run) => {
      await queryClient.invalidateQueries({ queryKey: ["runs"] })
      dialogRef.current?.close()
      navigate(`/runs/${run.id}`)
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setFormError(error.message)
        setConflictRunId(error.status === 409 ? (error.runId ?? null) : null)
        return
      }
      setFormError("The run could not be started.")
      setConflictRunId(null)
    },
  })

  function openDialog() {
    setFormError(null)
    setConflictRunId(null)
    dialogRef.current?.showModal()
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex max-w-2xl flex-col gap-2">
          <h1 className="text-2xl font-medium">Runs</h1>
          <p className="text-sm text-muted-foreground">
            One story goes through two agents, then stops so you can decide.
            Start a run and stay on its page to watch the steps.
          </p>
        </div>
        <Button onClick={openDialog}>Start a run</Button>
      </div>

      <ol className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {FLOW.map((step, index) => (
          <li key={step.title} className="rounded-xl border bg-card p-4">
            <p className="text-xs font-medium text-muted-foreground">
              Step {index + 1}
            </p>
            <p className="mt-1 font-medium">{step.title}</p>
            <p className="mt-2 text-sm text-muted-foreground">{step.body}</p>
          </li>
        ))}
      </ol>

      {runs.isLoading ? <Loading>Loading runs…</Loading> : null}
      {runs.isError ? (
        <Notice>
          The run list could not be loaded. Check that the app on port 8080 is
          running.
        </Notice>
      ) : null}

      {runs.data && runs.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No runs yet. Start one to see both agents work.
        </p>
      ) : null}

      {runs.data && runs.data.length > 0 ? (
        <div className="overflow-x-auto rounded-xl border">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b bg-muted/50 text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Story</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Started</th>
                <th className="px-4 py-3 font-medium">Duration</th>
                <th className="px-4 py-3 font-medium">Cost</th>
              </tr>
            </thead>
            <tbody>
              {runs.data.map((run) => (
                <tr key={run.id} className="border-b last:border-b-0">
                  <td className="px-4 py-3">
                    <Link
                      to={`/runs/${run.id}`}
                      className="font-medium underline-offset-4 hover:underline"
                    >
                      {run.storyId}
                    </Link>
                    <p className="text-muted-foreground">
                      {storyTitle(run.storyId) ?? "Enrollment story"}
                    </p>
                  </td>
                  <td className="px-4 py-3">
                    <StatusChip status={run.status} />
                  </td>
                  <td className="px-4 py-3">{formatWhen(run.startedAt)}</td>
                  <td className="px-4 py-3">
                    {formatDuration(run.startedAt, run.completedAt)}
                  </td>
                  <td className="px-4 py-3">
                    {formatMoney(costByRun.get(run.id))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      <p className="text-xs text-muted-foreground">
        Times are shown in Halifax.
      </p>

      <dialog
        ref={dialogRef}
        className="w-[min(32rem,calc(100%-2rem))] rounded-xl border bg-background p-0 text-foreground backdrop:bg-black/40"
      >
        <form
          className="flex flex-col gap-4 p-5"
          onSubmit={(event) => {
            event.preventDefault()
            setFormError(null)
            start.mutate()
          }}
        >
          <div>
            <h2 className="text-lg font-medium">Start a run</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              The story analyst runs first, then the test designer. The page
              pauses when the drafts are ready for you.
            </p>
          </div>
          <label className="flex flex-col gap-1 text-sm">
            Story
            <select
              className="h-9 rounded-lg border bg-background px-2"
              value={storyId}
              onChange={(event) => setStoryId(event.target.value)}
            >
              <optgroup label="Clear stories">
                {STORIES.filter((story) => !story.vague).map((story) => (
                  <option key={story.id} value={story.id}>
                    {story.id} — {story.title}
                  </option>
                ))}
              </optgroup>
              <optgroup label="Vague on purpose">
                {STORIES.filter((story) => story.vague).map((story) => (
                  <option key={story.id} value={story.id}>
                    {story.id} — {story.title}
                  </option>
                ))}
              </optgroup>
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            Your name
            <input
              className="h-9 rounded-lg border bg-background px-2"
              value={triggeredBy}
              onChange={(event) => setTriggeredBy(event.target.value)}
            />
          </label>
          {formError ? <Notice>{formError}</Notice> : null}
          {conflictRunId ? (
            <Link
              to={`/runs/${conflictRunId}`}
              className="text-sm underline-offset-4 hover:underline"
              onClick={() => dialogRef.current?.close()}
            >
              Open the run that is already going
            </Link>
          ) : null}
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => dialogRef.current?.close()}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={start.isPending}>
              {start.isPending ? "Starting…" : "Start"}
            </Button>
          </div>
        </form>
      </dialog>
    </div>
  )
}
