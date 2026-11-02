import { useQuery } from "@tanstack/react-query"
import { NavLink, Outlet } from "react-router-dom"

import { Button } from "@/components/ui/button"

const NAV_ITEMS = [
  { to: "/runs", label: "Runs" },
  { to: "/approvals", label: "Approvals" },
  { to: "/knowledge", label: "Knowledge" },
  { to: "/metrics", label: "Metrics" },
] as const

type HealthResponse = {
  status: string
}

function useApiHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: async (): Promise<HealthResponse> => {
      const response = await fetch("/api/health")
      if (!response.ok) {
        throw new Error(`Health check returned ${response.status}`)
      }
      return (await response.json()) as HealthResponse
    },
    retry: false,
    refetchOnWindowFocus: false,
  })
}

export function AppLayout() {
  const health = useApiHealth()
  const apiLabel = health.isSuccess
    ? `API ${health.data.status}`
    : health.isError
      ? "API unreachable"
      : "Checking API"

  return (
    <div className="flex min-h-svh flex-col">
      <header className="border-b">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-3 px-6 py-3">
          <span className="font-medium">QA Agentic Platform</span>
          <nav className="flex flex-wrap items-center gap-1">
            {NAV_ITEMS.map((item) => (
              <Button
                key={item.to}
                asChild
                variant="ghost"
                className="aria-[current=page]:bg-muted"
              >
                <NavLink to={item.to}>{item.label}</NavLink>
              </Button>
            ))}
          </nav>
          <span className="ml-auto text-xs text-muted-foreground">
            {apiLabel}
          </span>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
        <Outlet />
      </main>
    </div>
  )
}
