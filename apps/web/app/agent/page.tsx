import { generateStory } from "@/app/actions";
import { StorySheet } from "@/components/StorySheet";
import { Submit } from "@/components/Submit";
import { api, load } from "@/lib/platform";
import type { DocumentSummary, QueryResult, UserStory } from "@/lib/types";

export default async function AgentPage({
  searchParams,
}: {
  searchParams: Promise<{ story?: string; q?: string; error?: string }>;
}) {
  const query = await searchParams;
  const documents = await load<DocumentSummary[]>("/api/documents");
  let story: UserStory | null = null;
  let storyError: string | null = null;
  if (query.story) {
    try {
      story = await api.story(query.story);
    } catch (error) {
      storyError = error instanceof Error ? error.message : "Story not found.";
    }
  }
  let hits: QueryResult | null = null;
  let searchError: string | null = null;
  if (query.q) {
    try {
      hits = await api.query(query.q);
    } catch (error) {
      searchError = error instanceof Error ? error.message : "Search failed.";
    }
  }

  return (
    <>
      <p className="kicker">LangGraph user story agent</p>
      <h1>Draft one story.</h1>
      <p className="lede">
        Ask for the change in plain language. The agent retrieves library excerpts, writes the story, checks that it
        is testable, and attaches an effort forecast.
      </p>
      {query.error ? <div className="banner">{query.error}</div> : null}
      {storyError ? <div className="banner">{storyError}</div> : null}
      <section className="grid split">
        <div className="stack">
          <form className="card" action={generateStory}>
            <h2>Request</h2>
            <label htmlFor="prompt">What should the story cover?</label>
            <textarea
              id="prompt"
              name="prompt"
              required
              defaultValue="to be notified, with the new window and the site, when my job window changes by more than 30 minutes"
            />
            <label htmlFor="documentId">Limit retrieval to one document</label>
            <select id="documentId" name="documentId" defaultValue="">
              <option value="">Entire library</option>
              {(documents.data ?? []).map((document) => (
                <option key={document.id} value={document.id}>
                  {document.title}
                </option>
              ))}
            </select>
            <div className="actions">
              <Submit>Draft with the agent</Submit>
            </div>
          </form>
          <form className="card" action="/agent">
            <h2>Search the library</h2>
            <label htmlFor="q">Retrieval check, without drafting</label>
            <input id="q" name="q" defaultValue={query.q ?? ""} placeholder="offline completion duplicate visit" />
            <div className="actions">
              <button className="secondary" type="submit">
                Show excerpts
              </button>
            </div>
            {searchError ? <div className="banner">{searchError}</div> : null}
            {hits ? (
              <div className="list">
                {hits.citations.length === 0 ? <p>Nothing in the library matched.</p> : null}
                {hits.citations.map((citation) => (
                  <div key={`${citation.section}-${citation.score}`} className="excerpt">
                    <strong>
                      {citation.section} · {citation.score}
                    </strong>
                    <div>{citation.excerpt}</div>
                  </div>
                ))}
              </div>
            ) : null}
          </form>
        </div>
        <div>{story ? <StorySheet story={story} /> : <article className="card"><h2>Draft</h2><p>The story will land here, with the excerpts that justified it.</p></article>}</div>
      </section>
    </>
  );
}
