import Link from "next/link";
import { load, when } from "@/lib/platform";
import type { DocumentSummary, Insights, UserStory } from "@/lib/types";

export default async function OverviewPage() {
  const [documents, stories, insights] = await Promise.all([
    load<DocumentSummary[]>("/api/documents"),
    load<UserStory[]>("/api/stories"),
    load<Insights>("/api/insights"),
  ]);
  const error = documents.error || stories.error || insights.error;

  return (
    <>
      <p className="kicker">Agentic Knowledge Engine</p>
      <h1>Stories from the documents you already trust.</h1>
      <p className="lede">
        AKE keeps a library, retrieves the passages that match a request, and drafts one user story with an effort
        forecast. Spring Boot holds the records. The LangGraph agent does the drafting.
      </p>
      {error ? <div className="banner">Platform is not reachable. {error}</div> : null}
      <section className="grid stats">
        <article className="card stat">
          <strong>{documents.data?.length ?? "–"}</strong>
          <span>Documents in the library</span>
        </article>
        <article className="card stat">
          <strong>{stories.data?.length ?? "–"}</strong>
          <span>Stories in the backlog</span>
        </article>
        <article className="card stat">
          <strong>
            {insights.data ? `${Math.round(insights.data.averageConfidence * 100)}%` : "–"}
          </strong>
          <span>Mean forecast confidence</span>
        </article>
      </section>
      <section className="grid split">
        <article className="card">
          <h2>Recent stories</h2>
          <div className="list">
            {(stories.data ?? []).slice(0, 4).map((story) => (
              <div className="item" key={story.id}>
                <div>
                  <Link href={`/agent?story=${story.id}`}>
                    <strong>{story.title}</strong>
                  </Link>
                  <div className="meta">
                    {story.points} pts · {story.status} · {when(story.createdAt)}
                  </div>
                </div>
              </div>
            ))}
            {(stories.data ?? []).length === 0 ? <p>No stories yet. The agent can draft the first one.</p> : null}
          </div>
        </article>
        <article className="card">
          <h2>How a draft is made</h2>
          <div className="flow">
            <div>
              <strong>1. Library</strong>
              Mongo or memory stores the source. Chunks land in vector search.
            </div>
            <div>
              <strong>2. Agent</strong>
              LangGraph retrieves, drafts, critiques, then predicts points.
            </div>
            <div>
              <strong>3. Backlog</strong>
              Spring records the story, the audit line, and the forecast.
            </div>
          </div>
          <div className="actions">
            <Link className="button copper" href="/agent">
              Draft a story
            </Link>
            <Link className="button secondary" href="/library">
              Open the library
            </Link>
          </div>
        </article>
      </section>
    </>
  );
}
