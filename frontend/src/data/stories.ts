export type StoryOption = {
  id: string
  title: string
  vague: boolean
}

export const STORIES: StoryOption[] = [
  { id: "ENR-001", title: "Add a spouse as a dependent", vague: false },
  { id: "ENR-002", title: "Add a child dependent under age 26", vague: false },
  {
    id: "ENR-003",
    title: "Reject a child dependent aged 26 or older",
    vague: false,
  },
  {
    id: "ENR-004",
    title: "Terminate all coverage for a subscriber",
    vague: false,
  },
  {
    id: "ENR-005",
    title: "Terminate one dependent and keep the subscriber",
    vague: false,
  },
  {
    id: "ENR-006",
    title: "Accept a retroactive effective date inside 30 days",
    vague: false,
  },
  {
    id: "ENR-007",
    title: "Reject a retroactive effective date older than 30 days",
    vague: false,
  },
  { id: "ENR-008", title: "Reject an invalid relationship code", vague: false },
  {
    id: "ENR-009",
    title: "Reject a second active subscriber with the same member identifier",
    vague: false,
  },
  {
    id: "ENR-010",
    title: "Change the subscriber mailing address without changing the plan",
    vague: false,
  },
  {
    id: "ENR-011",
    title: "Change the plan from HMO to PPO mid-year",
    vague: false,
  },
  {
    id: "ENR-012",
    title: "Apply maintenance type CHANGE to an existing enrollment",
    vague: false,
  },
  { id: "ENR-013", title: "Apply maintenance type TERM", vague: false },
  {
    id: "ENR-014",
    title: "Reject an 834 that is missing the member identifier",
    vague: false,
  },
  {
    id: "ENR-015",
    title: "Reject an 834 date that is not CCYYMMDD",
    vague: false,
  },
  { id: "ENR-016", title: "Make family enrollment easier", vague: true },
  { id: "ENR-017", title: "Handle coverage changes", vague: true },
  { id: "ENR-018", title: "Fix problems in the 834 file", vague: true },
]

export function storyTitle(id: string) {
  return STORIES.find((story) => story.id === id)?.title
}
