import { estimateStory } from "@/app/actions";
import { Submit } from "@/components/Submit";
import { load } from "@/lib/platform";
import type { Insights } from "@/lib/types";

export default async function ForecastPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; points?: string; confidence?: string; risks?: string; rationale?: string; title?: string }>;
}) {
  const query = await searchParams;
  const insights = await load<Insights>("/api/insights");

  return (
    <>
      <p className="kicker">Predictive analysis</p>
      <h1>Forecast</h1>
      <p className="lede">
        Effort comes from a visible rubric: length, acceptance checks, risk language, and similar stories. It snaps to
        Fibonacci points. It is a planning prior, not a trained model.
      </p>
      {query.error ? <div className="banner">{query.error}</div> : null}
      {insights.error ? <div className="banner">{insights.error}</div> : null}
      <section className="grid split">
        <article className="card">
          <h2>Backlog readout</h2>
          {(insights.data?.notes ?? []).map((note) => (
            <p key={note} style={{ marginTop: 10 }}>
              {note}
            </p>
          ))}
          <div className="list">
            {(insights.data?.risks ?? []).map((risk) => (
              <div className="item" key={risk.risk}>
                <strong>{risk.risk}</strong>
                <span className="meta">{risk.count}</span>
              </div>
            ))}
          </div>
          <h3 style={{ marginTop: 18 }}>Watchlist</h3>
          {(insights.data?.watchlist ?? []).length === 0 ? <p>Nothing looks under-specified right now.</p> : null}
          {(insights.data?.watchlist ?? []).map((story) => (
            <p key={story.id} className="meta">
              {story.title} · {story.points} pts · {Math.round(story.confidence * 100)}%
            </p>
          ))}
        </article>
        <form className="card" action={estimateStory}>
          <h2>Estimate a story before saving it</h2>
          <label htmlFor="title">Title</label>
          <input id="title" name="title" required defaultValue={query.title ?? "Customer window change notice"} />
          <label htmlFor="description">Description</label>
          <textarea id="description" name="description" defaultValue="Notify the customer when a promised arrival window moves, without exposing technician phone numbers." />
          <label htmlFor="acceptance">Acceptance checks, one per line</label>
          <textarea id="acceptance" name="acceptance" defaultValue={"The notice states the new window.\nThe notice has no crew phone number.\nDispatch sees the send result."} />
          <div className="actions">
            <Submit>Estimate</Submit>
          </div>
          {query.points ? (
            <div style={{ marginTop: 16 }}>
              <p className="description">
                {query.points} points · {Math.round(Number(query.confidence) * 100)}% confidence
              </p>
              <p>{query.risks ? `Risks: ${query.risks}. ` : ""}{query.rationale}</p>
            </div>
          ) : null}
        </form>
      </section>
    </>
  );
}
