// Local research desk. It serves the page and proxies to this agent.
import { spawn } from "node:child_process";
import { createReadStream, existsSync } from "node:fs";
import { createServer } from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(here, "..");
const publicDir = path.join(here, "public");
const deskPort = Number(process.env.DESK_PORT || 4173);
const agentPort = Number(process.env.AGENT_PORT || 3921);
const agentUrl = (process.env.AGENT_URL || `http://127.0.0.1:${agentPort}`).replace(/\/$/, "");

let agentChild = null;
let agentLog = "";

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "content-length": Buffer.byteLength(payload),
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > 32_000) {
        reject(Object.assign(new Error("Body is too large."), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(Object.assign(new Error("Send a JSON object."), { status: 400 }));
      }
    });
    req.on("error", reject);
  });
}

async function agentReachable() {
  try {
    const response = await fetch(`${agentUrl}/v1/info`, { signal: AbortSignal.timeout(1500) });
    return response.ok;
  } catch {
    return false;
  }
}

async function ensureAgent() {
  if (await agentReachable()) return;
  if (process.env.AGENT_URL) {
    throw new Error(`The agent at ${agentUrl} is not responding.`);
  }
  if (agentChild) return waitForAgent();
  const bin = path.join(projectDir, "node_modules", ".bin", "agent-sdk");
  const command = existsSync(bin) ? bin : "npx";
  const args = existsSync(bin)
    ? []
    : ["--yes", "@grok/agent-sdk@0.6.0"];
  args.push(
    "serve",
    "--dir",
    projectDir,
    "--mode",
    "single",
    "--dev",
    "--host",
    "127.0.0.1",
    "--port",
    String(agentPort),
    "--no-playground",
    "--no-docs",
  );
  agentChild = spawn(command, args, {
    cwd: projectDir,
    stdio: ["ignore", "pipe", "pipe"],
    env: process.env,
  });
  const remember = (chunk) => {
    agentLog = (agentLog + chunk.toString()).slice(-2000);
  };
  agentChild.stdout.on("data", remember);
  agentChild.stderr.on("data", remember);
  agentChild.on("exit", () => {
    agentChild = null;
  });
  await waitForAgent();
}

async function waitForAgent() {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    if (await agentReachable()) return;
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
  throw new Error(`The agent did not open its HTTP port in time. ${agentLog.trim()}`.trim());
}

async function callTool(name, symbol) {
  const response = await fetch(`${agentUrl}/v1/tools/${name}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ input: { symbol } }),
    signal: AbortSignal.timeout(30_000),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message = body?.message || body?.error || `The agent returned HTTP ${response.status}.`;
    throw Object.assign(new Error(message), { status: response.status });
  }
  return body;
}

async function proxyStream(req, res, sessionId, startIndex) {
  const upstream = await fetch(
    `${agentUrl}/v1/session/${encodeURIComponent(sessionId)}/stream?startIndex=${startIndex}`,
    { headers: { accept: "application/x-ndjson" } },
  );
  res.writeHead(upstream.status, {
    "content-type": "application/x-ndjson; charset=utf-8",
    "cache-control": "no-cache",
  });
  if (!upstream.body) {
    res.end();
    return;
  }
  const reader = upstream.body.getReader();
  req.on("close", () => {
    reader.cancel().catch(() => {});
  });
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    if (!res.write(Buffer.from(value))) {
      await new Promise((resolve) => res.once("drain", resolve));
    }
  }
  res.end();
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", "http://127.0.0.1");
    if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/index.html")) {
      res.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      createReadStream(path.join(publicDir, "index.html")).pipe(res);
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/health") {
      sendJson(res, 200, { ok: await agentReachable(), agentUrl });
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/research") {
      await ensureAgent();
      const body = await readBody(req);
      const symbol = typeof body.symbol === "string" ? body.symbol.trim() : "";
      if (!symbol) {
        sendJson(res, 400, { ok: false, error: "Enter a symbol." });
        return;
      }
      const [market, results] = await Promise.all([
        callTool("market_technicals", symbol),
        callTool("published_results", symbol),
      ]);
      sendJson(res, 200, { ok: true, market, results });
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/session") {
      await ensureAgent();
      const body = await readBody(req);
      const message = typeof body.message === "string" ? body.message.trim() : "";
      if (!message) {
        sendJson(res, 400, { ok: false, error: "Enter a question." });
        return;
      }
      const sessionId = typeof body.sessionId === "string" ? body.sessionId : "";
      const target = sessionId
        ? `${agentUrl}/v1/session/${encodeURIComponent(sessionId)}`
        : `${agentUrl}/v1/session`;
      const payload = { message };
      if (typeof body.continuationToken === "string" && body.continuationToken) {
        payload.continuationToken = body.continuationToken;
      }
      const response = await fetch(target, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(120_000),
      });
      const text = await response.text();
      res.writeHead(response.status, {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
      });
      res.end(text);
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/stream") {
      await ensureAgent();
      const sessionId = url.searchParams.get("sessionId") || "";
      const startIndex = url.searchParams.get("startIndex") || "0";
      if (!/^ses_[A-Za-z0-9_-]+$/.test(sessionId)) {
        sendJson(res, 400, { ok: false, error: "Missing session." });
        return;
      }
      if (!/^\d+$/.test(startIndex)) {
        sendJson(res, 400, { ok: false, error: "Bad start index." });
        return;
      }
      await proxyStream(req, res, sessionId, startIndex);
      return;
    }
    sendJson(res, 404, { ok: false, error: "Not found." });
  } catch (error) {
    const status = error && typeof error.status === "number" ? error.status : 502;
    sendJson(res, status, { ok: false, error: error instanceof Error ? error.message : "Desk request failed." });
  }
});

function stopAgent() {
  if (agentChild && !agentChild.killed) agentChild.kill("SIGTERM");
}

process.on("SIGINT", () => {
  stopAgent();
  process.exit(0);
});
process.on("SIGTERM", () => {
  stopAgent();
  process.exit(0);
});

server.listen(deskPort, "127.0.0.1", () => {
  console.log(`NSE F&O desk  http://127.0.0.1:${deskPort}`);
  console.log(`Agent         ${agentUrl}`);
});
