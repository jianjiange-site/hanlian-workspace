"""Minimal ai-chat service entrypoint placeholder."""

from ai_chat.config import load_settings


def main() -> None:
    settings = load_settings()
    print(f"{settings.service_name} skeleton listening at {settings.grpc_listen_addr}")


if __name__ == "__main__":
    main()
