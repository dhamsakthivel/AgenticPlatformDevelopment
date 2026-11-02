import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { Button } from "@/components/ui/button"
import { Loading, Notice } from "@/components/status-chip"
import { api, type DocumentRow, type SearchHit } from "@/lib/api"
import { formatWhen } from "@/lib/format"

export function KnowledgePage() {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState("spouse dependent")
  const [submitted, setSubmitted] = useState("")
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [title, setTitle] = useState("")

  const documents = useQuery({
    queryKey: ["documents"],
    queryFn: () => api<DocumentRow[]>("/api/knowledge/documents"),
  })
  const search = useQuery({
    queryKey: ["knowledge-search", submitted],
    queryFn: () =>
      api<{ query: string; hits: SearchHit[] }>(
        `/api/knowledge/search?q=${encodeURIComponent(submitted)}`
      ),
    enabled: submitted.length > 0,
  })
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const body = new FormData()
      body.set("file", file)
      if (title.trim()) {
        body.set("title", title.trim())
      }
      return api<DocumentRow>("/api/knowledge/documents", {
        method: "POST",
        body,
      })
    },
    onSuccess: async () => {
      setUploadError(null)
      setTitle("")
      await queryClient.invalidateQueries({ queryKey: ["documents"] })
    },
    onError: (error) => {
      setUploadError(
        error instanceof Error
          ? error.message
          : "The file could not be uploaded."
      )
    },
  })
  const reindex = useMutation({
    mutationFn: () =>
      api<{ ingested: number; chunks: number; warnings: string[] }>(
        "/api/knowledge/reindex",
        { method: "POST" }
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["documents"] })
    },
  })

  return (
    <div className="flex flex-col gap-8">
      <div className="max-w-2xl">
        <h1 className="text-2xl font-medium">Knowledge</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          These notes are the rule book both agents search before they write.
          The notes are invented for the Example Benefit Plan.
        </p>
      </div>

      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          setSubmitted(query.trim())
        }}
      >
        <label className="flex min-w-64 flex-1 flex-col gap-1 text-sm">
          Search
          <input
            className="h-9 rounded-lg border bg-background px-2"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <Button type="submit">Search</Button>
      </form>

      {search.isFetching ? <Loading>Searching…</Loading> : null}
      {search.isError ? (
        <Notice>
          {search.error instanceof Error
            ? search.error.message
            : "Search failed."}
        </Notice>
      ) : null}
      {search.data ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">
            {search.data.hits.length} match
            {search.data.hits.length === 1 ? "" : "es"} for “{search.data.query}
            ”
          </p>
          {search.data.hits.map((hit) => (
            <article key={hit.chunkId} className="rounded-xl border p-4">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-medium">{hit.title || "Untitled note"}</h2>
                <p className="text-xs text-muted-foreground">
                  {hit.source} · score {hit.score.toFixed(3)}
                </p>
              </div>
              <p className="mt-2 text-sm whitespace-pre-wrap">{hit.text}</p>
            </article>
          ))}
        </div>
      ) : null}

      <section className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-medium">Notes</h2>
          <Button
            type="button"
            variant="outline"
            disabled={reindex.isPending}
            onClick={() => reindex.mutate()}
          >
            {reindex.isPending ? "Reloading…" : "Reload the rule book"}
          </Button>
        </div>
        {reindex.isError ? (
          <Notice>
            {reindex.error instanceof Error
              ? reindex.error.message
              : "The rule book could not be reloaded."}
          </Notice>
        ) : null}
        {reindex.data ? (
          <p className="text-sm text-muted-foreground">
            Reloaded {reindex.data.ingested} notes into {reindex.data.chunks}{" "}
            chunks.
            {reindex.data.warnings.length
              ? ` ${reindex.data.warnings.join(" ")}`
              : ""}
          </p>
        ) : null}
        {documents.isLoading ? <Loading>Loading notes…</Loading> : null}
        {documents.isError ? (
          <Notice>The note list could not be loaded.</Notice>
        ) : null}
        {documents.data && documents.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No notes yet. Reload the rule book to index the seed files.
          </p>
        ) : null}
        {documents.data && documents.data.length > 0 ? (
          <div className="overflow-x-auto rounded-xl border">
            <table className="w-full min-w-[36rem] text-left text-sm">
              <thead className="border-b bg-muted/50 text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Title</th>
                  <th className="px-4 py-3 font-medium">Source</th>
                  <th className="px-4 py-3 font-medium">Chunks</th>
                  <th className="px-4 py-3 font-medium">Indexed</th>
                </tr>
              </thead>
              <tbody>
                {documents.data.map((document) => (
                  <tr key={document.id} className="border-b last:border-b-0">
                    <td className="px-4 py-3">
                      {document.title || document.uri || "Untitled"}
                    </td>
                    <td className="px-4 py-3">{document.source}</td>
                    <td className="px-4 py-3">{document.chunks}</td>
                    <td className="px-4 py-3">
                      {formatWhen(document.ingestedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="font-medium">Add a note</h2>
        <form
          className="flex flex-col gap-3"
          onSubmit={(event) => {
            event.preventDefault()
            const form = event.currentTarget
            const file = new FormData(form).get("file")
            if (!(file instanceof File) || file.size === 0) {
              setUploadError("Choose a Markdown or PDF file.")
              return
            }
            upload.mutate(file)
            form.reset()
          }}
        >
          <label className="flex max-w-md flex-col gap-1 text-sm">
            Title
            <input
              className="h-9 rounded-lg border bg-background px-2"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            File
            <input
              name="file"
              type="file"
              accept=".md,.markdown,.pdf,text/markdown,application/pdf"
            />
          </label>
          {uploadError ? <Notice>{uploadError}</Notice> : null}
          {upload.isSuccess ? (
            <p className="text-sm text-muted-foreground">The note was added.</p>
          ) : null}
          <div>
            <Button type="submit" disabled={upload.isPending}>
              {upload.isPending ? "Uploading…" : "Upload"}
            </Button>
          </div>
        </form>
      </section>
    </div>
  )
}
