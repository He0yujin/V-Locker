import os
from pathlib import Path
from dotenv import load_dotenv

# 실행 위치(CWD)와 무관하게 프로젝트 루트의 .env를 정확히 로드
ENV_PATH = Path(__file__).resolve().parents[3] / ".env"
load_dotenv(dotenv_path=ENV_PATH)

KMA_API_KEY: str = os.getenv("KMA_API_KEY", "")
KAKAO_REST_API_KEY: str = os.getenv("KAKAO_REST_API_KEY", "")
API_TIMEOUT_SECONDS: float = 3.0

