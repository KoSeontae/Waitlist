#!/usr/bin/env bash
# 부하 테스트 1회 실행: 앱 기동 → 시드 → 워밍업 → 측정 → 불변식 확인 → 앱 종료
#
# 사용법: loadtest/run.sh <reserve|cancel> <hot|spread> <동시 사용자 수> [perf|limit]
#   hot    : 회차 1개에 집중
#   spread : 회차 100개에 분산
#   perf   : (reserve) 정원이 넉넉해 모든 요청이 성공. 처리 성능 측정용 (기본값)
#   limit  : (reserve) 정원이 작아 대부분 거절. 정원 초과 확인용
#
# 환경 변수: ITERATIONS(측정 요청 수), BUILD=0(jar 빌드 생략), POOL(커넥션 풀 크기)
set -euo pipefail

cd "$(dirname "$0")/.."

SCENARIO=${1:?"시나리오(reserve|cancel)를 지정하세요"}
DIST=${2:?"분포(hot|spread)를 지정하세요"}
VUS=${3:?"동시 사용자 수를 지정하세요"}
PROFILE=${4:-perf}

DB_NAME=waitlist_load
DB_CONTAINER=waitlist-postgres
PORT=18080
OUT=build/loadtest
export JWT_SECRET=${JWT_SECRET:-loadtest-only-secret-not-for-production-0123456789}
export BASE_URL=http://localhost:$PORT

case "$DIST" in
  hot) SLOTS=1 ;;
  spread) SLOTS=100 ;;
  *) echo "분포는 hot 또는 spread입니다: $DIST" >&2; exit 2 ;;
esac

case "$SCENARIO" in
  reserve)
    R_PER=0; W_PER=0
    WARM_CAPACITY=1000000; WARM_R=0; WARM_W=0; WARM_ITERATIONS=3000; WARM_VUS=10
    case "$PROFILE" in
      perf) CAPACITY=1000000; ITERATIONS=${ITERATIONS:-5000} ;;
      limit) CAPACITY=$([ "$DIST" = hot ] && echo 100 || echo 10); ITERATIONS=${ITERATIONS:-3000} ;;
      *) echo "프로필은 perf 또는 limit입니다: $PROFILE" >&2; exit 2 ;;
    esac
    if [ "$ITERATIONS" -gt 10000 ]; then echo "reserve의 ITERATIONS는 10000 이하여야 합니다" >&2; exit 2; fi
    MEASURE_ENV=(-e SLOT_OFFSET=0 -e SLOTS=$SLOTS -e USER_OFFSET=0)
    WARM_ENV=(-e SLOT_OFFSET=$SLOTS -e SLOTS=10 -e USER_OFFSET=10000)
    ;;
  cancel)
    PROFILE=perf
    R_PER=$([ "$DIST" = hot ] && echo 1000 || echo 10); W_PER=$R_PER
    CAPACITY=$R_PER
    ITERATIONS=${ITERATIONS:-1000}
    # 워밍업은 커넥션 풀보다 적은 동시 사용자로 실행한다.
    WARM_CAPACITY=50; WARM_R=50; WARM_W=50; WARM_ITERATIONS=500; WARM_VUS=4
    if [ "$ITERATIONS" -gt $((SLOTS * R_PER)) ]; then echo "cancel의 ITERATIONS는 $((SLOTS * R_PER)) 이하여야 합니다" >&2; exit 2; fi
    MEASURE_ENV=(-e RESERVATION_OFFSET=0 -e USER_OFFSET=0)
    WARM_ENV=(-e RESERVATION_OFFSET=$((SLOTS * R_PER)) -e USER_OFFSET=10000)
    ;;
  *) echo "시나리오는 reserve 또는 cancel입니다: $SCENARIO" >&2; exit 2 ;;
esac

mkdir -p "$OUT"
export JAVA_HOME=${JAVA21_HOME:-$(/usr/libexec/java_home -v 21)}

psql_load() { docker exec -i "$DB_CONTAINER" psql -U slot -d "$DB_NAME" -v ON_ERROR_STOP=1 -q "$@"; }
db_cpu_usec() { docker exec "$DB_CONTAINER" awk '/^usage_usec/ {print $2}' /sys/fs/cgroup/cpu.stat; }
app_cpu_sec() { ps -o cputime= -p "$APP_PID" | awk -F: '{ s = 0; for (i = 1; i <= NF; i++) s = s * 60 + $i; print s }'; }

if lsof -nP -iTCP:$PORT -sTCP:LISTEN >/dev/null 2>&1; then
  echo "포트 $PORT를 이미 사용 중입니다. 다른 앱을 측정하지 않도록 중단합니다." >&2
  exit 1
fi

docker compose up -d >/dev/null 2>&1
until docker exec "$DB_CONTAINER" pg_isready -U slot -d waitlist >/dev/null 2>&1; do sleep 1; done
if [ "$(docker exec "$DB_CONTAINER" psql -U slot -d waitlist -Atc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")" != 1 ]; then
  docker exec "$DB_CONTAINER" createdb -U slot "$DB_NAME"
fi

if [ "${BUILD:-1}" = 1 ]; then
  ./gradlew bootJar -q
fi
JAR=$(ls build/libs/*.jar | grep -v -- '-plain' | head -1)

DB_URL=jdbc:postgresql://localhost:5433/$DB_NAME \
  SERVER_PORT=$PORT \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=${POOL:-10} \
  "$JAVA_HOME/bin/java" -Xms1g -Xmx1g -jar "$JAR" >"$OUT/app.log" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null || true' EXIT

for _ in $(seq 1 90); do
  kill -0 $APP_PID 2>/dev/null || { echo "앱이 시작 중에 종료됐습니다. $OUT/app.log를 확인하세요" >&2; exit 1; }
  curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1 && break
  sleep 1
done
curl -sf "$BASE_URL/actuator/health" >/dev/null || { echo "앱이 90초 안에 준비되지 않았습니다" >&2; exit 1; }

psql_load -v m_slots=$SLOTS -v m_capacity=$CAPACITY -v r_per=$R_PER -v w_per=$W_PER \
  -v warm_capacity=$WARM_CAPACITY -v warm_r=$WARM_R -v warm_w=$WARM_W <loadtest/seed.sql

k6 run --quiet -e VUS=$WARM_VUS -e ITERATIONS=$WARM_ITERATIONS "${WARM_ENV[@]}" \
  "loadtest/$SCENARIO.js" >"$OUT/warmup.log" 2>&1
sleep 3

ERRORS_BEFORE=$(grep -c 'Connection is not available' "$OUT/app.log" || true)
rm -f "$OUT/summary.txt"
APP_CPU_START=$(app_cpu_sec)
DB_CPU_START=$(db_cpu_usec)

TIMEFORMAT='%R %U %S'
{ time k6 run --quiet -e VUS="$VUS" -e ITERATIONS="$ITERATIONS" -e SUMMARY="$OUT/summary.txt" "${MEASURE_ENV[@]}" \
    "loadtest/$SCENARIO.js" >"$OUT/k6.log" 2>&1; } 2>"$OUT/k6.time"

APP_CPU_END=$(app_cpu_sec)
DB_CPU_END=$(db_cpu_usec)
sleep 3

read -r WALL K6_USER K6_SYS <"$OUT/k6.time"
CPU=$(awk -v w="$WALL" -v a0="$APP_CPU_START" -v a1="$APP_CPU_END" -v d0="$DB_CPU_START" -v d1="$DB_CPU_END" \
  -v ku="$K6_USER" -v ks="$K6_SYS" \
  'BEGIN { printf "%.2f / %.2f / %.2f", (a1 - a0) / w, (d1 - d0) / 1000000 / w, (ku + ks) / w }')

CHECK=$(psql_load -At -F'|' -v m_slots=$SLOTS <loadtest/check.sql)
SUMMARY=$(cat "$OUT/summary.txt")
POOL_TIMEOUTS=$(( $(grep -c 'Connection is not available' "$OUT/app.log" || true) - ERRORS_BEFORE ))

RESULTS=$OUT/results.md
if [ ! -f "$RESULTS" ]; then
  echo '| 시각 | 시나리오 | 분포 | 프로필 | 풀 | VU | 요청 | 완료 | 처리량(req/s) | p50(ms) | p95(ms) | p99(ms) | max(ms) | 성공 | 409 | 오류 | 정원 초과 | 미발급 빈자리 | 알림 누락 | 중복 | 풀 타임아웃 로그 | CPU 코어 app / db / k6 |' >"$RESULTS"
  echo '|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|' >>"$RESULTS"
fi
ROW="| $(date '+%m-%d %H:%M') | $SCENARIO | $DIST | $PROFILE | ${POOL:-10} | $VUS | $ITERATIONS | ${SUMMARY//|/ | } | ${CHECK//|/ | } | $POOL_TIMEOUTS | $CPU |"
echo "$ROW" >>"$RESULTS"
echo "$ROW"
