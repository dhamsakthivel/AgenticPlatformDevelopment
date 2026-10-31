import { Coach } from "./Coach";
import { when } from "@/lib/platform";
import type { UserStory } from "@/lib/types";

export function StorySheet({ story }: { story: UserStory }) {
  return (
    <article className="card sheet">
      <div className="meta">
        <span className="pill">{story.status}</span>
        <span className="pill sage">{story.points} pts</span>
        <span className="pill">{story.priority}</span>
        <span className="pill">{Math.round(story.confidence * 100)}% confidence</span>
      </div>
      <h2 style={{ marginTop: 12 }}>{story.title}</h2>
      <p className="description">{story.description}</p>
      <h3>Acceptance checks</h3>
      <ol className="checks">
        {story.acceptanceCriteria.map((criterion) => (
          <li key={criterion}>{criterion}</li>
        ))}
      </ol>
      {story.risks.length > 0 ? (
        <p className="meta" style={{ marginTop: 12 }}>
          Risk signals: {story.risks.join(", ")}
        </p>
      ) : null}
      <div className="list">
        {story.rationale.map((line) => (
          <p key={line} className="meta">
            {line}
          </p>
        ))}
      </div>
      {story.citations.length > 0 ? (
        <>
          <h3 style={{ marginTop: 18 }}>Grounded in</h3>
          {story.citations.map((citation) => (
            <div key={`${citation.documentId}-${citation.section}`} className="excerpt">
              <strong>
                {citation.title} · {citation.section}
              </strong>
              <div>{citation.excerpt}</div>
            </div>
          ))}
        </>
      ) : (
        <p className="meta">No library excerpts matched this prompt.</p>
      )}
      {story.notes.length > 0 ? (
        <p className="meta" style={{ marginTop: 14 }}>
          {story.notes.join(" ")} Model: {story.model}. {when(story.createdAt)}.
        </p>
      ) : null}
      <div className="actions">
        <Coach id={story.id} />
      </div>
    </article>
  );
}
