import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL;

const succeeded = new Counter('app_succeeded');
const rejected = new Counter('app_rejected');
const errored = new Counter('app_errored');

// 409는 정원 마감 같은 정상 거절이므로 실패로 세지 않는다.
http.setResponseCallback(http.expectedStatuses(201, 204, 409));

export function env(name) {
  const value = Number(__ENV[name]);
  if (__ENV[name] === undefined || Number.isNaN(value)) {
    throw new Error(`환경 변수 ${name}가 필요합니다`);
  }
  return value;
}

export function options() {
  return {
    scenarios: {
      main: {
        executor: 'shared-iterations',
        vus: env('VUS'),
        iterations: env('ITERATIONS'),
        maxDuration: __ENV.MAX_DURATION || '3m',
      },
    },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  };
}

// 로그인(BCrypt)이 CPU를 쓰지 않도록 서버와 같은 비밀키로 토큰을 직접 서명한다.
export function authParams(userId) {
  const now = Math.floor(Date.now() / 1000);
  const header = encoding.b64encode(JSON.stringify({ alg: 'HS256' }), 'rawurl');
  const payload = encoding.b64encode(
    JSON.stringify({ sub: String(userId), role: 'USER', iat: now, exp: now + 3600 }),
    'rawurl'
  );
  const signature = crypto.hmac('sha256', __ENV.JWT_SECRET, `${header}.${payload}`, 'base64rawurl');
  return { headers: { Authorization: `Bearer ${header}.${payload}.${signature}` } };
}

export function record(response) {
  if (response.status === 201 || response.status === 204) {
    succeeded.add(1);
  } else if (response.status === 409) {
    rejected.add(1);
  } else {
    errored.add(1);
  }
}

// 완료|처리량|p50|p95|p99|max|성공|409|오류
export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  const duration = data.metrics.http_req_duration.values;
  const line = [
    count('http_reqs'),
    data.metrics.http_reqs.values.rate.toFixed(1),
    duration.med.toFixed(1),
    duration['p(95)'].toFixed(1),
    duration['p(99)'].toFixed(1),
    duration.max.toFixed(1),
    count('app_succeeded'),
    count('app_rejected'),
    count('app_errored'),
  ].join('|');

  const output = { stdout: `${line}\n` };
  if (__ENV.SUMMARY) {
    output[__ENV.SUMMARY] = `${line}\n`;
  }
  return output;
}
