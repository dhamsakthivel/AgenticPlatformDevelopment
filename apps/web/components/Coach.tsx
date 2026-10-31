"use client";

import { useState } from "react";

export function Coach({ id }: { id: string }) {
  const [text, setText] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function ask() {
    setPending(true);
    try {
      const response = await fetch("/api/coach", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id }),
      });
      const body = await response.json();
      setText(body.suggestion || body.error || "No suggestion returned.");
    } catch {
      setText("The coach request did not complete.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div>
      <button type="button" className="secondary" onClick={ask} disabled={pending}>
        {pending ? "Asking coach…" : "Ask Spring AI coach"}
      </button>
      {text ? <p style={{ marginTop: 12 }}>{text}</p> : null}
    </div>
  );
}
