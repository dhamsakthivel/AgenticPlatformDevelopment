"use client";

import { useFormStatus } from "react-dom";

export function Submit({ children, secondary = false }: { children: string; secondary?: boolean }) {
  const { pending } = useFormStatus();
  return (
    <button className={secondary ? "secondary" : "copper"} disabled={pending}>
      {pending ? "Working…" : children}
    </button>
  );
}
