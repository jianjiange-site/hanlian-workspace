"""Configuration placeholder for ai-chat."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    service_name: str = "ai-chat"
    grpc_listen_addr: str = "0.0.0.0:50051"


def load_settings() -> Settings:
    return Settings()
