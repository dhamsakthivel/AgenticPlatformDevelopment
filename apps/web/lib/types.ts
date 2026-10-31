export type DocumentSummary = {
  id: string;
  title: string;
  source: string;
  tags: string[];
  characters: number;
  ingestStatus: string;
  chunks: number;
  createdAt: string;
};

export type KnowledgeDocument = DocumentSummary & { text: string };

export type Citation = {
  documentId: string;
  title: string;
  section: string;
  excerpt: string;
  score: number;
};

export type Prediction = {
  points: number;
  confidence: number;
  risks: string[];
  rationale: string[];
  neighbors: number;
};

export type UserStory = {
  id: string;
  title: string;
  persona: string;
  action: string;
  benefit: string;
  description: string;
  acceptanceCriteria: string[];
  priority: string;
  status: string;
  points: number;
  confidence: number;
  risks: string[];
  rationale: string[];
  citations: Citation[];
  notes: string[];
  model: string;
  createdAt: string;
};

export type Insights = {
  stories: number;
  averagePoints: number;
  averageConfidence: number;
  risks: { risk: string; count: number }[];
  readySlice: UserStory[];
  watchlist: UserStory[];
  notes: string[];
};

export type SystemReport = {
  platform: string;
  storage: string;
  agent: {
    status: string;
    llm: string;
    embeddings: string;
    vector: string;
    chunks: number;
    langsmith: boolean;
    langsmithProject: string;
    mcp: string[];
  };
  security: {
    apiKeyRequired: boolean;
    header: string;
    browserHoldsKey: string;
    controls: string[];
  };
  audit: { at: string; action: string; subject: string; detail: string }[];
};

export type QueryResult = { citations: Citation[]; backend: string };

export type CoachNote = { enabled: boolean; suggestion: string };
