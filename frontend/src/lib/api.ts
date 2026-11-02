export class ApiError extends Error {
  readonly status: number
  readonly runId?: string

  constructor(message: string, status: number, runId?: string) {
    super(message)
    this.status = status
    this.runId = runId
  }
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set("Accept", "application/json")
  if (
    init?.body &&
    !(init.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json")
  }
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    let message = `The request failed (${response.status}).`
    let runId: string | undefined
    try {
      const body = (await response.json()) as {
        message?: unknown
        runId?: unknown
      }
      if (typeof body.message === "string" && body.message) {
        message = body.message
      }
      if (typeof body.runId === "string") {
        runId = body.runId
      }
    } catch {
      // The body was not JSON.
    }
    throw new ApiError(message, response.status, runId)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export type Step = {
  id: string
  stepName: string
  agent: string | null
  status: string
  input: unknown
  output: unknown
  error: string | null
  startedAt: string | null
  completedAt: string | null
}

export type TestStep = {
  action: string
  expected: string
}

export type TestCaseContent = {
  key?: string
  title?: string
  type?: string
  priority?: string
  preconditions?: string[]
  steps?: TestStep[]
  dataRequirements?: string[]
  linkedCriteria?: string[]
  possibleDuplicateOf?: string | null
}

export type Draft = {
  id: string
  key: string
  externalRef: string | null
  status: string
  content: TestCaseContent | null
}

export type Run = {
  id: string
  storyId: string
  status: string
  triggeredBy: string
  temporalWorkflowId: string | null
  startedAt: string
  completedAt: string | null
  summary: {
    error?: string
    riskScore?: number
    approved?: number
    rejected?: number
    edited?: number
  } | null
  steps: Step[]
  drafts: Draft[]
  dataSets: unknown[]
}

export type Approval = {
  id: string
  runId: string
  storyId: string
  status: string
  requestedAt: string
  decidedAt: string | null
  decidedBy: string | null
  drafts: Draft[]
}

export type LlmCall = {
  stepId: string | null
  purpose: string | null
  model: string | null
  inputTokens: number
  outputTokens: number
  costUsd: number | string
  latencyMs: number
  success: boolean
}

export type DocumentRow = {
  id: string
  title: string | null
  source: string | null
  uri: string | null
  app: string | null
  module: string | null
  chunks: number
  ingestedAt: string | null
}

export type SearchHit = {
  chunkId: string
  documentId: string
  title: string | null
  source: string | null
  app: string | null
  module: string | null
  text: string
  score: number
}

export type CostReport = {
  byDay: {
    day: string
    inputTokens: number
    outputTokens: number
    costUsd: number | string
  }[]
  byPurpose: {
    purpose: string
    calls: number
    inputTokens: number
    outputTokens: number
    costUsd: number | string
  }[]
  byRun: {
    runId: string
    inputTokens: number
    outputTokens: number
    costUsd: number | string
  }[]
  averageTokensPerRun: number
  runsWithModelCalls: number
  approved: number
  rejected: number
  edited: number
  acceptanceRate: number
}

export type Review = {
  summary?: string
  riskScore?: number
  riskReasons?: string[]
  criteria?: {
    id?: string
    text?: string
    testable?: boolean
    issue?: string | null
  }[]
  ambiguities?: { question?: string; severity?: string }[]
  missingScenarios?: string[]
}

export type DesignedCase = TestCaseContent & { key?: string }

export function asReview(value: unknown): Review | null {
  if (!value || typeof value !== "object" || !("riskScore" in value)) {
    return null
  }
  return value as Review
}

export function asDesign(value: unknown): DesignedCase[] | null {
  if (!value || typeof value !== "object" || !("testCases" in value)) {
    return null
  }
  const cases = (value as { testCases?: unknown }).testCases
  return Array.isArray(cases) ? (cases as DesignedCase[]) : null
}

export function asComment(value: unknown): string | null {
  if (!value || typeof value !== "object" || !("markdown" in value)) {
    return null
  }
  const markdown = (value as { markdown?: unknown }).markdown
  return typeof markdown === "string" ? markdown : null
}
