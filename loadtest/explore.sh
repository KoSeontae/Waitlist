#!/usr/bin/env bash
# 동시 사용자 수를 바꿔 가며 run.sh를 반복 실행한다.
#
# 사용법: loadtest/explore.sh <reserve|cancel> <hot|spread> [perf|limit]
# 환경 변수: VUS_LIST(기본 "1 5 10 20 50 100"), REPEAT(조건당 반복 횟수, 기본 1)
set -euo pipefail

cd "$(dirname "$0")/.."

SCENARIO=${1:?"시나리오(reserve|cancel)를 지정하세요"}
DIST=${2:?"분포(hot|spread)를 지정하세요"}
PROFILE=${3:-perf}

if [ "${BUILD:-1}" = 1 ]; then
  ./gradlew bootJar -q
fi

for vus in ${VUS_LIST:-1 5 10 20 50 100}; do
  for _ in $(seq 1 "${REPEAT:-1}"); do
    BUILD=0 loadtest/run.sh "$SCENARIO" "$DIST" "$vus" "$PROFILE"
  done
done
