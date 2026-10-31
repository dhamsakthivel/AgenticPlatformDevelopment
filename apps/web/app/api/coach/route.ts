import { platform, PlatformError } from "@/lib/platform";
import type { CoachNote } from "@/lib/types";

export async function POST(request: Request) {
  const body = (await request.json()) as { id?: string };
  if (!body.id) {
    return Response.json({ error: "Story id is required." }, { status: 400 });
  }
  try {
    const note = await platform<CoachNote>(`/api/stories/${body.id}/coach`, { method: "POST" });
    return Response.json(note);
  } catch (error) {
    const message = error instanceof PlatformError ? error.message : "Coach is unavailable.";
    const status = error instanceof PlatformError ? error.status : 503;
    return Response.json({ error: message }, { status });
  }
}
