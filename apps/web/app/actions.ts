"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { platform, PlatformError } from "@/lib/platform";
import type { UserStory } from "@/lib/types";

function message(error: unknown): string {
  return error instanceof PlatformError ? error.message : "The platform could not complete that.";
}

export async function addDocument(formData: FormData) {
  const title = String(formData.get("title") || "").trim();
  const text = String(formData.get("text") || "").trim();
  const source = String(formData.get("source") || "").trim();
  const tags = String(formData.get("tags") || "")
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
  let error: string | null = null;
  try {
    await platform("/api/documents", {
      method: "POST",
      body: JSON.stringify({ title, text, source, tags }),
    });
  } catch (caught) {
    error = message(caught);
  }
  if (error) redirect(`/library?error=${encodeURIComponent(error)}`);
  revalidatePath("/library");
  revalidatePath("/");
  redirect("/library");
}

export async function removeDocument(formData: FormData) {
  const id = String(formData.get("id") || "");
  let error: string | null = null;
  try {
    await platform(`/api/documents/${id}`, { method: "DELETE" });
  } catch (caught) {
    error = message(caught);
  }
  if (error) redirect(`/library?error=${encodeURIComponent(error)}`);
  revalidatePath("/library");
  redirect("/library");
}

export async function reindexDocument(formData: FormData) {
  const id = String(formData.get("id") || "");
  let error: string | null = null;
  try {
    await platform(`/api/documents/${id}/reindex`, { method: "POST" });
  } catch (caught) {
    error = message(caught);
  }
  if (error) redirect(`/library?error=${encodeURIComponent(error)}`);
  revalidatePath("/library");
  redirect("/library");
}

export async function generateStory(formData: FormData) {
  const prompt = String(formData.get("prompt") || "").trim();
  const documentId = String(formData.get("documentId") || "").trim();
  let storyId = "";
  let error: string | null = null;
  try {
    const story = await platform<UserStory>("/api/stories/generate", {
      method: "POST",
      body: JSON.stringify({ prompt, documentIds: documentId ? [documentId] : [] }),
    });
    storyId = story.id;
  } catch (caught) {
    error = message(caught);
  }
  if (error || !storyId) redirect(`/agent?error=${encodeURIComponent(error || "No story returned.")}`);
  revalidatePath("/");
  revalidatePath("/backlog");
  revalidatePath("/forecast");
  redirect(`/agent?story=${storyId}`);
}

export async function setStoryStatus(formData: FormData) {
  const id = String(formData.get("id") || "");
  const status = String(formData.get("status") || "");
  let error: string | null = null;
  try {
    await platform(`/api/stories/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
  } catch (caught) {
    error = message(caught);
  }
  if (error) redirect(`/backlog?error=${encodeURIComponent(error)}`);
  revalidatePath("/backlog");
  revalidatePath("/forecast");
  revalidatePath("/");
  redirect("/backlog");
}

export async function estimateStory(formData: FormData) {
  const title = String(formData.get("title") || "").trim();
  const description = String(formData.get("description") || "").trim();
  const acceptance = String(formData.get("acceptance") || "")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  let params = "";
  let error: string | null = null;
  try {
    const prediction = await platform<{
      points: number;
      confidence: number;
      risks: string[];
      rationale: string[];
    }>("/api/stories/estimate", {
      method: "POST",
      body: JSON.stringify({ title, description, acceptanceCriteria: acceptance }),
    });
    params = new URLSearchParams({
      points: String(prediction.points),
      confidence: String(prediction.confidence),
      risks: prediction.risks.join(","),
      rationale: prediction.rationale.join(" "),
      title,
    }).toString();
  } catch (caught) {
    error = message(caught);
  }
  if (error || !params) redirect(`/forecast?error=${encodeURIComponent(error || "No estimate returned.")}`);
  redirect(`/forecast?${params}`);
}
