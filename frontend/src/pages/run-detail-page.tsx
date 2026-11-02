import { useEffect } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"

import { Loading, Notice, StatusChip } from "@/components/status-chip"
import { storyTitle } from "@/data/stories"
import {
  asComment,
  asDesign,
  asReview,
  api,
  type DesignedCase,
  type LlmCall,
  type Review,
  type Run,
  type Step,
  type TestCaseContent,
} from "@/lib/api"
import {
  formatDuration,
  formatMoney,
  formatTokens,
  formatWhen,
} from "@/lib/format"

const PIPELINE = [
  { name: "fetchStory", label: "Read the story", who: "Story analyst" },
  {
    name: "retrieveContext",
    label: "Search the rule book",
    who: "Story analyst",
  },
  { name: "runStoryAnalysis", label: "Write the review", who: "Story analyst" },
  { name: "postStoryComment", label: "Save the review", who: "Story analyst" },
  { name: "runTestDesign", label: "Draft test cases", who: "Test designer" },
  {
    name: "saveDraftTestCases",
    label: "Save the drafts",
    who: "Test designer",
  },
  { name: "awaitApproval", label: "Wait for your decision", who: "You" },
]

export function RunDetailPage() {
  const { runId = "" } = useParams()
  const queryClient = useQueryClient()
  const run = useQuery({
    queryKey: ["run", runId],
    queryFn: () => api<Run>(`/api/runs/${runId}`),
    enabled: runId.length > 0,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === "RUNNING") {
        return 2000
      }
      if (status === "WAITING_FOR_APPROVAL") {
        return 4000
      }
      return false
    },
  })
  const usage = useQuery({
    queryKey: ["usage", runId],
    queryFn: () => api<LlmCall[]>(`/api/runs/${runId}/usage`),
    enabled: runId.length > 0,
    refetchInterval: run.data?.status === "RUNNING" ? 2000 : false,
  })

  useEffect(() => {
    if (
      !runId ||
      run.data?.status === "COMPLETED" ||
      run.data?.status === "FAILED"
    ) {
      return
    }
    const source = new EventSource(`/api/runs/${runId}/events`)
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: ["run", runId] })
      void queryClient.invalidateQueries({ queryKey: ["usage", runId] })
    }
    source.addEventListener("RUNNING", refresh)
    source.addEventListener("SUCCEEDED", refresh)
    source.addEventListener("FAILED", refresh)
    return () => source.close()
  }, [queryClient, run.data?.status, runId])

  if (run.isLoading) {
    return <Loading>Loading the run…</Loading>
  }
  if (run.isError || !run.data) {
    return <Notice>This run could not be loaded.</Notice>
  }

  const details = run.data
  const calls = usage.data ?? []
  const review =
    details.steps
      .map((step) => asReview(step.output))
      .find((item) => item !== null) ?? null
  const comment =
    details.steps
      .map((step) => asComment(step.output))
      .find((item) => item !== null) ?? null
  const designed =
    details.steps
      .map((step) => asDesign(step.output))
      .find((item) => item !== null) ?? null
  const cost = calls.reduce((sum, call) => sum + Number(call.costUsd || 0), 0)
  const tokens = calls.reduce(
    (sum, call) => sum + call.inputTokens + call.outputTokens,
    0
  )

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm text-muted-foreground">
            <Link to="/runs" className="underline-offset-4 hover:underline">
              Runs
            </Link>
          </p>
          <h1 className="mt-1 text-2xl font-medium">
            {details.storyId}
            <span className="mt-1 block text-base font-normal text-muted-foreground">
              {storyTitle(details.storyId) ?? "Enrollment story"}
            </span>
          </h1>
        </div>
        <StatusChip status={details.status} />
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-4">
        <Fact
          label="Started"
          value={`${formatWhen(details.startedAt)} Halifax`}
        />
        <Fact
          label="Duration"
          value={formatDuration(details.startedAt, details.completedAt)}
        />
        <Fact
          label="Tokens"
          value={calls.length ? formatTokens(tokens) : "—"}
        />
        <Fact label="Cost" value={calls.length ? formatMoney(cost) : "—"} />
      </dl>

      {details.status === "FAILED" ? (
        <Notice>
          {details.summary?.error ||
            details.steps.find((step) => step.error)?.error ||
            "The run failed."}
        </Notice>
      ) : null}
      {details.status === "WAITING_FOR_APPROVAL" ? (
        <div className="rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-950">
          Both agents are finished. Review the drafts and send a decision for
          every one.
          <Link
            to={`/approvals?run=${details.id}`}
            className="ml-2 font-medium underline-offset-4 hover:underline"
          >
            Open the approval
          </Link>
        </div>
      ) : null}
      {details.status === "RUNNING" ? (
        <p className="text-sm text-muted-foreground" aria-live="polite">
          The agents are working. This page updates as each step finishes.
        </p>
      ) : null}

      <section className="flex flex-col gap-3">
        <h2 className="font-medium">What this run does</h2>
        <ol className="flex flex-col gap-2">
          {PIPELINE.map((item) => {
            const step = details.steps.find(
              (candidate) => candidate.stepName === item.name
            )
            return (
              <PipelineRow
                key={item.name}
                item={item}
                step={step}
                calls={calls.filter((call) => call.stepId === step?.id)}
              />
            )
          })}
        </ol>
      </section>

      {review ? <ReviewCard review={review} /> : null}
      {comment ? (
        <section className="flex flex-col gap-2">
          <h2 className="font-medium">Review comment</h2>
          <pre className="overflow-x-auto rounded-xl border bg-muted/40 p-4 text-sm whitespace-pre-wrap">
            {comment}
          </pre>
        </section>
      ) : null}
      <DraftSection run={details} designed={designed} />
    </div>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1">{value}</dd>
    </div>
  )
}

function PipelineRow({
  item,
  step,
  calls,
}: {
  item: (typeof PIPELINE)[number]
  step: Step | undefined
  calls: LlmCall[]
}) {
  const tokens = calls.reduce(
    (sum, call) => sum + call.inputTokens + call.outputTokens,
    0
  )
  const cost = calls.reduce((sum, call) => sum + Number(call.costUsd || 0), 0)
  const status = step?.status ?? "WAITING"
  return (
    <li className="rounded-xl border px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-medium">{item.label}</p>
          <p className="text-sm text-muted-foreground">{item.who}</p>
        </div>
        <div className="flex flex-wrap items-center gap-3 text-sm">
          {step ? (
            <span className="text-muted-foreground">
              {formatDuration(step.startedAt, step.completedAt)}
            </span>
          ) : null}
          {calls.length ? (
            <span className="text-muted-foreground">
              {formatTokens(tokens)} tokens · {formatMoney(cost)}
            </span>
          ) : null}
          {step ? (
            <StatusChip status={status} />
          ) : (
            <span className="text-xs text-muted-foreground">Not started</span>
          )}
        </div>
      </div>
      {step?.error ? (
        <p className="mt-2 text-sm text-destructive">{step.error}</p>
      ) : null}
      {step?.output ? <JsonBlock value={step.output} /> : null}
    </li>
  )
}

function JsonBlock({ value }: { value: unknown }) {
  return (
    <details className="mt-3">
      <summary className="cursor-pointer text-sm text-muted-foreground">
        JSON output
      </summary>
      <pre className="mt-2 overflow-x-auto rounded-lg bg-muted/50 p-3 text-xs whitespace-pre-wrap">
        {JSON.stringify(value, null, 2)}
      </pre>
    </details>
  )
}

function ReviewCard({ review }: { review: Review }) {
  return (
    <section className="flex flex-col gap-4 rounded-xl border p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-medium">Story analyst review</h2>
        <p className="text-sm">Risk score {review.riskScore} out of 5</p>
      </div>
      {review.summary ? <p className="text-sm">{review.summary}</p> : null}
      {review.criteria && review.criteria.length > 0 ? (
        <div>
          <h3 className="text-sm font-medium">What can be tested</h3>
          <ul className="mt-2 flex flex-col gap-2 text-sm">
            {review.criteria.map((criterion) => (
              <li key={criterion.id ?? criterion.text}>
                <span className="font-medium">{criterion.id}</span>{" "}
                {criterion.text}
                {criterion.testable === false ? (
                  <span className="text-muted-foreground">
                    {" "}
                    — not testable
                    {criterion.issue ? `: ${criterion.issue}` : ""}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {review.ambiguities && review.ambiguities.length > 0 ? (
        <div>
          <h3 className="text-sm font-medium">What is unclear</h3>
          <ul className="mt-2 flex flex-col gap-1 text-sm">
            {review.ambiguities.map((item) => (
              <li key={item.question}>
                {item.question}
                {item.severity ? (
                  <span className="text-muted-foreground">
                    {" "}
                    ({item.severity})
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  )
}

function DraftSection({
  run,
  designed,
}: {
  run: Run
  designed: DesignedCase[] | null
}) {
  const cards = run.drafts.length
    ? run.drafts.map((draft) => ({
        key: draft.key,
        status: draft.status,
        qtestId: draft.externalRef,
        content: draft.content,
      }))
    : (designed ?? []).map((item) => ({
        key: item.key ?? "Draft",
        status: "DRAFT",
        qtestId: null as string | null,
        content: item,
      }))
  if (cards.length === 0) {
    return null
  }
  return (
    <section className="flex flex-col gap-3">
      <h2 className="font-medium">Test designer drafts</h2>
      <div className="grid gap-3">
        {cards.map((card) => (
          <CaseCard
            key={card.key}
            caseKey={card.key}
            status={card.status}
            qtestId={card.qtestId}
            content={card.content}
          />
        ))}
      </div>
    </section>
  )
}

function CaseCard({
  caseKey,
  status,
  qtestId,
  content,
}: {
  caseKey: string
  status: string
  qtestId: string | null
  content: TestCaseContent | null
}) {
  return (
    <article className="rounded-xl border p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-medium">
          {caseKey}
          {content?.title ? (
            <span className="font-normal"> — {content.title}</span>
          ) : null}
        </h3>
        <StatusChip status={status} />
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        {[content?.type, content?.priority].filter(Boolean).join(" · ") ||
          "Draft"}
        {qtestId ? ` · qTest case ${qtestId}` : ""}
        {content?.possibleDuplicateOf
          ? ` · possible duplicate of ${content.possibleDuplicateOf}`
          : ""}
      </p>
      {content?.steps && content.steps.length > 0 ? (
        <ol className="mt-3 flex flex-col gap-2 text-sm">
          {content.steps.map((step, index) => (
            <li key={`${step.action}-${index}`}>
              <span className="font-medium">
                {index + 1}. {step.action}
              </span>
              <span className="block text-muted-foreground">
                Expected: {step.expected}
              </span>
            </li>
          ))}
        </ol>
      ) : null}
    </article>
  )
}
