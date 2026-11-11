
from concurrent.futures import ThreadPoolExecutor
import time

from sqlalchemy import text

from backend.app.db.database import engine


def test_concurrent_database_reads():
    """동시 DB 조회 20건이 정상 처리되는지 확인"""

    def read_database(_):
        start = time.perf_counter()

        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT COUNT(*) FROM users")
            ).scalar_one()

        elapsed = time.perf_counter() - start

        return result, elapsed

    # 요청 20건, 동시 작업자 5명
    with ThreadPoolExecutor(max_workers=5) as executor:
        results = list(executor.map(read_database, range(20)))

    assert len(results) == 20
    assert all(count >= 1 for count, _ in results)

    elapsed_times = [elapsed for _, elapsed in results]

    print(f"\n총 조회 요청: {len(results)}건")
    print(f"최대 응답 시간: {max(elapsed_times):.3f}초")
    print(f"평균 응답 시간: {sum(elapsed_times)/len(elapsed_times):.3f}초")


