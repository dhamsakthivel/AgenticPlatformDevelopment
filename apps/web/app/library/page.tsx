import { addDocument, reindexDocument, removeDocument } from "@/app/actions";
import { Submit } from "@/components/Submit";
import { load, when } from "@/lib/platform";
import type { DocumentSummary } from "@/lib/types";

export default async function LibraryPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const query = await searchParams;
  const documents = await load<DocumentSummary[]>("/api/documents");

  return (
    <>
      <p className="kicker">Document store</p>
      <h1>Library</h1>
      <p className="lede">Paste a brief, a ticket export, or a decision note. AKE chunks it and indexes it for retrieval.</p>
      {query.error ? <div className="banner">{query.error}</div> : null}
      {documents.error ? <div className="banner">{documents.error}</div> : null}
      <section className="grid split">
        <form className="card" action={addDocument}>
          <h2>Add a source</h2>
          <label htmlFor="title">Title</label>
          <input id="title" name="title" required maxLength={200} placeholder="Harbor dispatch rules" />
          <label htmlFor="source">Source</label>
          <input id="source" name="source" maxLength={120} placeholder="prd, meeting, ticket" />
          <label htmlFor="tags">Tags</label>
          <input id="tags" name="tags" placeholder="dispatch, notifications" />
          <label htmlFor="text">Text</label>
          <textarea id="text" name="text" required placeholder="Paste the document…" />
          <div className="actions">
            <Submit>Index document</Submit>
          </div>
        </form>
        <div className="card">
          <h2>Indexed sources</h2>
          <div className="list">
            {(documents.data ?? []).map((document) => (
              <div className="item" key={document.id}>
                <div>
                  <strong>{document.title}</strong>
                  <div className="meta">
                    <span className={document.ingestStatus === "indexed" ? "pill sage" : "pill warn"}>
                      {document.ingestStatus}
                    </span>
                    {document.chunks} chunks · {document.characters.toLocaleString()} characters · {when(document.createdAt)}
                  </div>
                </div>
                <div className="stack">
                  <form action={reindexDocument}>
                    <input type="hidden" name="id" value={document.id} />
                    <Submit secondary>Reindex</Submit>
                  </form>
                  <form action={removeDocument}>
                    <input type="hidden" name="id" value={document.id} />
                    <Submit secondary>Remove</Submit>
                  </form>
                </div>
              </div>
            ))}
            {(documents.data ?? []).length === 0 ? <p>The library is empty.</p> : null}
          </div>
        </div>
      </section>
    </>
  );
}
