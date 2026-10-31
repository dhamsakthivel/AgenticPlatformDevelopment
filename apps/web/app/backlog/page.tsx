import { setStoryStatus } from "@/app/actions";
import { Submit } from "@/components/Submit";
import { load, when } from "@/lib/platform";
import type { UserStory } from "@/lib/types";

const statuses = ["draft", "ready", "committed"];

export default async function BacklogPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const query = await searchParams;
  const stories = await load<UserStory[]>("/api/stories");

  return (
    <>
      <p className="kicker">System of record</p>
      <h1>Backlog</h1>
      <p className="lede">Stories the agent drafted. Move one to ready when the checks are enough to build.</p>
      {query.error ? <div className="banner">{query.error}</div> : null}
      {stories.error ? <div className="banner">{stories.error}</div> : null}
      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Story</th>
              <th>Forecast</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {(stories.data ?? []).map((story) => (
              <tr key={story.id}>
                <td>
                  <strong>{story.title}</strong>
                  <div className="meta">{story.description}</div>
                  <div className="meta">{when(story.createdAt)}</div>
                </td>
                <td>
                  {story.points} pts
                  <div className="meta">{Math.round(story.confidence * 100)}% · {story.risks.join(", ") || "no risk tags"}</div>
                </td>
                <td>
                  <form action={setStoryStatus} className="stack">
                    <input type="hidden" name="id" value={story.id} />
                    <select className="compact" name="status" defaultValue={story.status} key={`${story.id}-${story.status}`}>
                      {statuses.map((status) => (
                        <option key={status} value={status}>
                          {status}
                        </option>
                      ))}
                    </select>
                    <Submit secondary>Save</Submit>
                  </form>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(stories.data ?? []).length === 0 ? <p>The backlog is empty.</p> : null}
      </div>
    </>
  );
}
