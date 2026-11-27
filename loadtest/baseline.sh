#!/usr/bin/env bash
# 기준선 측정: 정해진 조건 전체를 REPEAT회(기본 3) 측정한다. 약 1시간 걸린다.
#
# 한 조건을 연달아 재면 그 시간대의 편차가 한 조건에 몰리므로, 회차를 바깥 반복으로 둔다.
set -uo pipefail

cd "$(dirname "$0")/.."

if [ "${BUILD:-1}" = 1 ]; then
  ./gradlew bootJar -q && ./gradlew --stop -q || exit 1
fi

failed=0
run() {
  BUILD=0 loadtest/run.sh "$@" || { echo "실패: $*" >&2; failed=$((failed + 1)); }
}

for rep in $(seq 1 "${REPEAT:-3}"); do
  echo "### 회차 $rep ($(date '+%H:%M'))"
  # A. 예약 처리 성능
  for dist in spread hot; do
    for vus in 1 10 100; do run reserve "$dist" "$vus" perf; done
  done
  # B. 정원 초과
  for dist in spread hot; do
    for vus in 10 100; do run reserve "$dist" "$vus" limit; done
  done
  # C. 취소와 승계: 커넥션 풀(10)보다 적은, 같은, 많은 동시 사용자
  export ITERATIONS=200
  for vus in 5 10 20; do run cancel hot "$vus"; done
  unset ITERATIONS
done

echo "### 끝 ($(date '+%H:%M')), 실패 $failed건"
