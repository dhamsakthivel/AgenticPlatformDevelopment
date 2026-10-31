import type {
  DocumentSummary,
  Insights,
  KnowledgeDocument,
  QueryResult,
  SystemReport,
  UserStory,
} from "./types";

const base = process.env.PLATFORM_API_URL ?? "http://localhost:8080";
const key = process.env.PLATFORM_API_KEY ?? "dev-ake-key";

export class PlatformError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function platform<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": key,
      ...(init?.headers ?? {}),
    },
    cache: "no-store",
  });
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = body?.error || response.statusText || "Platform request failed";
    throw new PlatformError(message, response.status);
  }
  return body as T;
}

export async function load<T>(path: string): Promise<{ data: T | null; error: string | null }> {
  try {
    return { data: await platform<T>(path), error: null };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Platform is unreachable";
    return { data: null, error: message };
  }
}

export const api = {
  documents: () => platform<DocumentSummary[]>("/api/documents"),
  document: (id: string) => platform<KnowledgeDocument>(`/api/documents/${id}`),
  stories: () => platform<UserStory[]>("/api/stories"),
  story: (id: string) => platform<UserStory>(`/api/stories/${id}`),
  insights: () => platform<Insights>("/api/insights"),
  system: () => platform<SystemReport>("/api/system"),
  query: (query: string, documentIds: string[] = []) =>
    platform<QueryResult>("/api/rag/query", {
      method: "POST",
      body: JSON.stringify({ query, topK: 4, documentIds }),
    }),
};

export function when(iso: string): string {
  return (
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/Halifax",
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }).format(new Date(iso)) + " AT"
  );
}
