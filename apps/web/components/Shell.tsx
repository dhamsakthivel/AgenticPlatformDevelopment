"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const links = [
  ["/", "Overview"],
  ["/library", "Library"],
  ["/agent", "Story agent"],
  ["/backlog", "Backlog"],
  ["/forecast", "Forecast"],
  ["/control", "Control"],
];

export function Shell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  return (
    <div className="app">
      <aside className="rail">
        <Link href="/" className="mark">
          <strong>AKE</strong>
          <span>Cloud 3.0 · knowledge engine</span>
        </Link>
        <nav className="nav">
          {links.map(([href, label]) => {
            const active = href === "/" ? path === "/" : path.startsWith(href);
            return (
              <Link key={href} href={href} className={active ? "active" : ""}>
                {label}
              </Link>
            );
          })}
        </nav>
        <p className="rail-note">
          Documents stay in the library. The agent drafts one story at a time, with the excerpts it used.
        </p>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
