import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom"

import { AppLayout } from "@/components/app-layout"
import { ApprovalsPage } from "@/pages/approvals-page"
import { KnowledgePage } from "@/pages/knowledge-page"
import { MetricsPage } from "@/pages/metrics-page"
import { RunsPage } from "@/pages/runs-page"

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/runs" replace />} />
          <Route path="/runs" element={<RunsPage />} />
          <Route path="/approvals" element={<ApprovalsPage />} />
          <Route path="/knowledge" element={<KnowledgePage />} />
          <Route path="/metrics" element={<MetricsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
