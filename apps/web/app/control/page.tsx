import { load, when } from "@/lib/platform";
import type { SystemReport } from "@/lib/types";

export default async function ControlPage() {
  const system = await load<SystemReport>("/api/system");
  const report = system.data;

  return (
    <>
      <p className="kicker">Observability and security</p>
      <h1>Control</h1>
      <p className="lede">
        LangSmith receives traces only when a key is configured. The browser never sees the platform API key; Next.js
        server actions attach it.
      </p>
      {system.error ? <div className="banner">{system.error}</div> : null}
      {report ? (
        <section className="grid split">
          <article className="card">
            <h2>Runtime</h2>
            <p>Platform {report.platform}. Records in {report.storage}.</p>
            <p style={{ marginTop: 8 }}>
              Agent {report.agent.status}. Chat: {report.agent.llm || "n/a"}. Embeddings: {report.agent.embeddings || "n/a"}.
              Vectors: {report.agent.vector || "n/a"} ({report.agent.chunks} chunks).
            </p>
            <p style={{ marginTop: 8 }}>
              LangSmith {report.agent.langsmith ? `on, project ${report.agent.langsmithProject}` : "off — traces stay in the process"}.
            </p>
            <p style={{ marginTop: 8 }}>MCP tools live on the agent at {(report.agent.mcp || []).join(", ") || "/mcp"}.</p>
          </article>
          <article className="card">
            <h2>Security</h2>
            <p>Header {report.security.header}. Browser holds the key: {report.security.browserHoldsKey}.</p>
            <ul>
              {report.security.controls.map((control) => (
                <li key={control}>{control}</li>
              ))}
            </ul>
          </article>
          <article className="card" style={{ gridColumn: "1 / -1" }}>
            <h2>Audit</h2>
            <div className="list">
              {report.audit.length === 0 ? <p>No audited actions yet.</p> : null}
              {report.audit.map((entry) => (
                <div className="item" key={`${entry.at}-${entry.action}-${entry.subject}`}>
                  <div>
                    <strong>{entry.action}</strong>
                    <div className="meta">{entry.detail}</div>
                  </div>
                  <span className="meta">{when(entry.at)}</span>
                </div>
              ))}
            </div>
          </article>
        </section>
      ) : null}
    </>
  );
}
