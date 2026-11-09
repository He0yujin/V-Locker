"""
카카오 로컬 API 연동 서비스 (Places & EV Chargers Search)

[기능]
1. 목적지 키워드/주소 검색 -> 위도(lat), 경도(lng) 좌표 변환
2. [사용 연장] 기능용 현 위치 반경 내 가장 가까운 실제 전기차 충전소 검색
"""

import logging
import httpx
from typing import List, Dict, Any

from backend.app.core.config import KAKAO_REST_API_KEY, API_TIMEOUT_SECONDS

logger = logging.getLogger("uvicorn.error")


KAKAO_KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"


async def search_places(query: str, size: int = 5) -> Dict[str, Any]:
    """
    목적지 명칭(예: '강남역', '대관령 안반데기')을 카카오 로컬 API로 검색해 좌표 목록을 반환합니다.
    """
    if not KAKAO_REST_API_KEY:
        return {
            "places": [
                {
                    "name": query,
                    "address": "기본 검색 위치 (API 키 미설정)",
                    "lat": 37.4979,
                    "lng": 127.0276,
                }
            ],
            "is_fallback": True,
        }

    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {"query": query, "size": size}

    try:
        async with httpx.AsyncClient(timeout=API_TIMEOUT_SECONDS) as client:
            resp = await client.get(KAKAO_KEYWORD_SEARCH_URL, headers=headers, params=params)
            resp.raise_for_status()
            data = resp.json()

        documents = data.get("documents", [])
        places: List[Dict[str, Any]] = []
        for doc in documents:
            places.append({
                "name": doc.get("place_name"),
                "address": doc.get("road_address_name") or doc.get("address_name"),
                "category": doc.get("category_group_name") or "",
                "lat": float(doc.get("y")),
                "lng": float(doc.get("x")),
            })

        return {
            "places": places,
            "is_fallback": False,
            "source": "Kakao Local Keyword API",
        }
    except Exception as e:
        logger.warning(f"[Kakao Places Fallback] query={query!r} err={repr(e)}")
        return {
            "places": [],
            "is_fallback": True,
            "error": str(e),
            "source": "Fallback",
        }


async def search_nearby_ev_chargers(
    lat: float,
    lng: float,
    radius_m: int = 20000,
    size: int = 3,
) -> Dict[str, Any]:
    """
    현재 차량 GPS 좌표 기준으로 반경 20km 내 가장 가까운 실제 전기차 충전소를 거리순으로 조회합니다.
    30분 전 알림 [사용 연장] 클릭 시 실제 근처 충전소로 마진을 재계산하는 데 사용됩니다.
    """
    fallback_charger = [
        {
            "name": "인근 급속 충전소 (안전 기본값)",
            "address": "반경 내 최단거리 충전소",
            "distance_km": 12.0,
            "lat": lat + 0.05,
            "lng": lng + 0.05,
        }
    ]

    if not KAKAO_REST_API_KEY:
        return {"chargers": fallback_charger, "is_fallback": True}

    headers = {"Authorization": f"KakaoAK {KAKAO_REST_API_KEY}"}
    params = {
        "query": "전기차충전소",
        "x": str(lng),
        "y": str(lat),
        "radius": min(20000, radius_m),
        "sort": "distance",
        "size": size,
    }

    try:
        async with httpx.AsyncClient(timeout=API_TIMEOUT_SECONDS) as client:
            resp = await client.get(KAKAO_KEYWORD_SEARCH_URL, headers=headers, params=params)
            resp.raise_for_status()
            data = resp.json()

        documents = data.get("documents", [])
        if not documents:
            return {"chargers": fallback_charger, "is_fallback": True}

        chargers = []
        for doc in documents:
            dist_m = float(doc.get("distance", 12000))
            chargers.append({
                "name": doc.get("place_name"),
                "address": doc.get("road_address_name") or doc.get("address_name"),
                "distance_km": round(dist_m / 1000.0, 2),
                "lat": float(doc.get("y")),
                "lng": float(doc.get("x")),
            })

        return {
            "chargers": chargers,
            "is_fallback": False,
            "source": "Kakao Local EV Charger Search",
        }
    except Exception as e:
        logger.warning(f"[Kakao EV Chargers Fallback] lat={lat}, lng={lng} err={repr(e)}")
        return {
            "chargers": fallback_charger,
            "is_fallback": True,
            "error": str(e),
        }

