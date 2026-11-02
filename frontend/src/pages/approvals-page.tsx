import { useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useSearchParams } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Loading, Notice, StatusChip } from "@/components/status-chip"
import { storyTitle } from "@/data/stories"
import {
  api,
  type Approval,
  type Draft,
  type TestCaseContent,
  type TestStep,
} from "@/lib/api"

type Action = "APPROVE" | "REJECT" | "EDIT"

type DraftChoice = {
  action: Action | ""
  title: string
  type: string
  priority: string
  steps: TestStep[]
  preconditions: string[]
  dataRequirements: string[]
  linkedCriteria: string[]
}

const NAME_KEY = "qa-decided-by"

export function ApprovalsPage() {
  const [params] = useSearchParams()
  const focusRun = params.get("run")
  const queryClient = useQueryClient()
  const [name, setName] = useState(
    () => localStorage.getItem(NAME_KEY) || "sam"
  )
  const [choices, setChoices] = useState<Record<string, DraftChoice>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [sent, setSent] = useState<Record<string, boolean>>({})
  const [notice, setNotice] = useState<string | null>(null)

  const approvals = useQuery({
    queryKey: ["approvals"],
    queryFn: () => api<Approval[]>("/api/approvals"),
    refetchInterval: (query) =>
      query.state.data?.some((item) => item.status === "PENDING")
        ? 3000
        : false,
  })

  const decide = useMutation({
    mutationFn: (approval: Approval) => {
      const body = {
        decidedBy: name.trim(),
        decisions: approval.drafts.map((draft) => {
          const choice = choiceFor(choices, draft)
          if (choice.action !== "EDIT") {
            return { key: draft.key, action: choice.action }
          }
          return {
            key: draft.key,
            action: "EDIT",
            editedCase: {
              key: draft.key,
              title: choice.title.trim(),
              type: choice.type,
              priority: choice.priority,
              preconditions: choice.preconditions,
              steps: choice.steps.map((step) => ({
                action: step.action.trim(),
                expected: step.expected.trim(),
              })),
              dataRequirements: choice.dataRequirements,
              linkedCriteria: choice.linkedCriteria,
            },
          }
        }),
      }
      return api<{ status: string }>(`/api/approvals/${approval.id}/decision`, {
        method: "POST",
        body: JSON.stringify(body),
      })
    },
    onSuccess: async (_result, approval) => {
      localStorage.setItem(NAME_KEY, name.trim())
      setNotice(`Decision sent for ${approval.storyId}. The run is finishing.`)
      setSent((current) => ({ ...current, [approval.id]: true }))
      setErrors((current) => ({ ...current, [approval.id]: "" }))
      await queryClient.invalidateQueries({ queryKey: ["approvals"] })
      await queryClient.invalidateQueries({ queryKey: ["runs"] })
      await queryClient.invalidateQueries({ queryKey: ["run", approval.runId] })
    },
    onError: (error, approval) => {
      setSent((current) => ({ ...current, [approval.id]: false }))
      setErrors((current) => ({
        ...current,
        [approval.id]:
          error instanceof Error
            ? error.message
            : "The decision could not be sent.",
      }))
    },
  })

  const pending =
    approvals.data?.filter((item) => item.status === "PENDING") ?? []
  const closed =
    approvals.data?.filter((item) => item.status !== "PENDING") ?? []

  function update(draft: Draft, patch: Partial<DraftChoice>) {
    setChoices((current) => ({
      ...current,
      [draft.id]: { ...choiceFor(current, draft), ...patch },
    }))
  }

  function setAll(approval: Approval, action: Action) {
    setChoices((current) => {
      const next = { ...current }
      for (const draft of approval.drafts) {
        next[draft.id] = { ...choiceFor(current, draft), action }
      }
      return next
    })
  }

  function submit(approval: Approval) {
    if (!name.trim()) {
      setErrors((current) => ({
        ...current,
        [approval.id]: "Enter your name.",
      }))
      return
    }
    for (const draft of approval.drafts) {
      const choice = choiceFor(choices, draft)
      if (!choice.action) {
        setErrors((current) => ({
          ...current,
          [approval.id]: `Choose approve, reject, or edit for ${draft.key}.`,
        }))
        return
      }
      if (choice.action === "EDIT" && !choice.title.trim()) {
        setErrors((current) => ({
          ...current,
          [approval.id]: `The edit of ${draft.key} needs a title.`,
        }))
        return
      }
      if (
        choice.action === "EDIT" &&
        choice.steps.some(
          (step) => !step.action.trim() || !step.expected.trim()
        )
      ) {
        setErrors((current) => ({
          ...current,
          [approval.id]: `Each step in the edit of ${draft.key} needs an action and an expected result.`,
        }))
        return
      }
    }
    setErrors((current) => ({ ...current, [approval.id]: "" }))
    decide.mutate(approval)
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="max-w-2xl">
        <h1 className="text-2xl font-medium">Approvals</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          The test designer stops here. Approve, reject, or edit every draft,
          then send the decision. The run finishes after that.
        </p>
      </div>

      {approvals.isLoading ? <Loading>Loading approvals…</Loading> : null}
      {approvals.isError ? (
        <Notice>
          Approvals could not be loaded. Check that the app on port 8080 is
          running.
        </Notice>
      ) : null}
      {notice ? <p className="text-sm">{notice}</p> : null}
      {approvals.data && pending.length === 0 && !notice ? (
        <p className="text-sm text-muted-foreground">
          Nothing is waiting. Start a run, then come back when the drafts are
          ready.
        </p>
      ) : null}

      {pending.map((approval) => (
        <ApprovalCard
          key={approval.id}
          approval={approval}
          focused={approval.runId === focusRun}
          name={name}
          onName={setName}
          choices={choices}
          onChange={update}
          onAll={setAll}
          onSubmit={submit}
          sending={decide.isPending && decide.variables?.id === approval.id}
          sent={Boolean(sent[approval.id]) && approval.status === "PENDING"}
          error={errors[approval.id]}
        />
      ))}

      {closed.length > 0 ? (
        <section className="flex flex-col gap-3">
          <h2 className="font-medium">Already decided</h2>
          {closed.map((approval) => (
            <article
              key={approval.id}
              className="rounded-xl border p-4 text-sm"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Link
                  to={`/runs/${approval.runId}`}
                  className="font-medium underline-offset-4 hover:underline"
                >
                  {approval.storyId}
                </Link>
                <StatusChip status={approval.status} />
              </div>
              <p className="mt-1 text-muted-foreground">
                {storyTitle(approval.storyId)}
                {approval.decidedBy
                  ? ` · decided by ${approval.decidedBy}`
                  : ""}
              </p>
            </article>
          ))}
        </section>
      ) : null}
    </div>
  )
}

function ApprovalCard({
  approval,
  focused,
  name,
  onName,
  choices,
  onChange,
  onAll,
  onSubmit,
  sending,
  sent,
  error,
}: {
  approval: Approval
  focused: boolean
  name: string
  onName: (value: string) => void
  choices: Record<string, DraftChoice>
  onChange: (draft: Draft, patch: Partial<DraftChoice>) => void
  onAll: (approval: Approval, action: Action) => void
  onSubmit: (approval: Approval) => void
  sending: boolean
  sent: boolean
  error?: string
}) {
  const ready = useMemo(
    () => approval.drafts.every((draft) => choiceFor(choices, draft).action),
    [approval.drafts, choices]
  )
  return (
    <article
      id={`approval-${approval.runId}`}
      className={`flex flex-col gap-4 rounded-xl border p-4 ${focused ? "ring-2 ring-sky-400" : ""}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-medium">
            <Link
              to={`/runs/${approval.runId}`}
              className="underline-offset-4 hover:underline"
            >
              {approval.storyId}
            </Link>
          </h2>
          <p className="text-sm text-muted-foreground">
            {storyTitle(approval.storyId) ?? "Enrollment story"}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => onAll(approval, "APPROVE")}
          >
            Approve all
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => onAll(approval, "REJECT")}
          >
            Reject all
          </Button>
        </div>
      </div>

      {approval.drafts.map((draft) => (
        <DraftEditor
          key={draft.id}
          draft={draft}
          choice={choiceFor(choices, draft)}
          onChange={(patch) => onChange(draft, patch)}
        />
      ))}

      <label className="flex max-w-xs flex-col gap-1 text-sm">
        Your name
        <input
          className="h-9 rounded-lg border bg-background px-2"
          value={name}
          onChange={(event) => onName(event.target.value)}
        />
      </label>
      {error ? <Notice>{error}</Notice> : null}
      {sent ? (
        <p className="text-sm text-muted-foreground">
          Decision sent. The run is finishing.
        </p>
      ) : null}
      <div>
        <Button
          type="button"
          disabled={sending || !ready}
          onClick={() => onSubmit(approval)}
        >
          {sending ? "Sending…" : "Send decision"}
        </Button>
      </div>
    </article>
  )
}

function DraftEditor({
  draft,
  choice,
  onChange,
}: {
  draft: Draft
  choice: DraftChoice
  onChange: (patch: Partial<DraftChoice>) => void
}) {
  const content = draft.content
  return (
    <div className="rounded-lg border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-medium">
            {draft.key}
            {content?.title ? (
              <span className="font-normal"> — {content.title}</span>
            ) : null}
          </p>
          <p className="text-sm text-muted-foreground">
            {[
              content?.type,
              content?.priority,
              draft.externalRef ? `qTest ${draft.externalRef}` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
        <div className="flex gap-1">
          {(["APPROVE", "REJECT", "EDIT"] as Action[]).map((action) => (
            <Button
              key={action}
              type="button"
              size="sm"
              variant={choice.action === action ? "default" : "outline"}
              onClick={() => onChange({ action })}
            >
              {action === "APPROVE"
                ? "Approve"
                : action === "REJECT"
                  ? "Reject"
                  : "Edit"}
            </Button>
          ))}
        </div>
      </div>
      {content?.steps && content.steps.length > 0 ? (
        <ol className="mt-3 flex flex-col gap-1 text-sm">
          {content.steps.map((step, index) => (
            <li key={`${step.action}-${index}`}>
              {index + 1}. {step.action}
              <span className="block text-muted-foreground">
                Expected: {step.expected}
              </span>
            </li>
          ))}
        </ol>
      ) : null}
      {choice.action === "EDIT" ? (
        <div className="mt-3 flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            Title
            <input
              className="h-9 rounded-lg border bg-background px-2"
              value={choice.title}
              onChange={(event) => onChange({ title: event.target.value })}
            />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="flex flex-col gap-1 text-sm">
              Type
              <select
                className="h-9 rounded-lg border bg-background px-2"
                value={choice.type}
                onChange={(event) => onChange({ type: event.target.value })}
              >
                <option value="POSITIVE">POSITIVE</option>
                <option value="NEGATIVE">NEGATIVE</option>
                <option value="BOUNDARY">BOUNDARY</option>
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">
              Priority
              <select
                className="h-9 rounded-lg border bg-background px-2"
                value={choice.priority}
                onChange={(event) => onChange({ priority: event.target.value })}
              >
                <option value="P1">P1</option>
                <option value="P2">P2</option>
                <option value="P3">P3</option>
              </select>
            </label>
          </div>
          {choice.steps.map((step, index) => (
            <div key={index} className="grid gap-2 sm:grid-cols-2">
              <label className="flex flex-col gap-1 text-sm">
                Action
                <input
                  className="h-9 rounded-lg border bg-background px-2"
                  value={step.action}
                  onChange={(event) => {
                    const steps = choice.steps.map((item, itemIndex) =>
                      itemIndex === index
                        ? { ...item, action: event.target.value }
                        : item
                    )
                    onChange({ steps })
                  }}
                />
              </label>
              <label className="flex flex-col gap-1 text-sm">
                Expected
                <input
                  className="h-9 rounded-lg border bg-background px-2"
                  value={step.expected}
                  onChange={(event) => {
                    const steps = choice.steps.map((item, itemIndex) =>
                      itemIndex === index
                        ? { ...item, expected: event.target.value }
                        : item
                    )
                    onChange({ steps })
                  }}
                />
              </label>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function choiceFor(
  choices: Record<string, DraftChoice>,
  draft: Draft
): DraftChoice {
  const existing = choices[draft.id]
  if (existing) {
    return existing
  }
  const content: TestCaseContent = draft.content ?? {}
  return {
    action: "",
    title: content.title ?? "",
    type: content.type ?? "POSITIVE",
    priority: content.priority ?? "P2",
    steps: content.steps?.length
      ? content.steps.map((step) => ({ ...step }))
      : [{ action: "", expected: "" }],
    preconditions: content.preconditions ?? [],
    dataRequirements: content.dataRequirements ?? [],
    linkedCriteria: content.linkedCriteria ?? [],
  }
}
