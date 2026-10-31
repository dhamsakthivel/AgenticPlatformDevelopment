from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

_DEFAULT_SEED = Path(__file__).resolve().parent / "seed" / "harbor-field-prd.md"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", populate_by_name=True)

    api_key: str = Field(default="dev-ake-key", validation_alias=AliasChoices("AKE_API_KEY", "API_KEY"))
    database_url: str = ""
    openai_api_key: str = ""
    openai_base_url: str = ""
    chat_model: str = Field(default="gpt-4o-mini", validation_alias=AliasChoices("AKE_CHAT_MODEL", "CHAT_MODEL"))
    embed_model: str = Field(
        default="text-embedding-3-small",
        validation_alias=AliasChoices("AKE_EMBED_MODEL", "EMBED_MODEL"),
    )
    embed_dim: int = 256
    langsmith_api_key: str = ""
    langsmith_project: str = "ake-user-story"
    langsmith_endpoint: str = "https://api.smith.langchain.com"
    seed_path: str = str(_DEFAULT_SEED)

    def llm_enabled(self) -> bool:
        return bool(self.openai_api_key.strip())

    def langsmith_enabled(self) -> bool:
        return bool(self.langsmith_api_key.strip())
