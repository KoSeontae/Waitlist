// 예약 취소: 대기자가 있는 회차의 예약을 본인이 취소해 승계를 일으킨다.
import http from 'k6/http';
import exec from 'k6/execution';
import { BASE_URL, env, options as baseOptions, authParams, record } from './lib.js';

export { handleSummary } from './lib.js';
export const options = baseOptions();

const RESERVATION_OFFSET = env('RESERVATION_OFFSET');
const USER_OFFSET = env('USER_OFFSET');

export default function () {
  const i = exec.scenario.iterationInTest;
  const params = authParams(USER_OFFSET + i + 1);
  record(http.del(`${BASE_URL}/reservations/${RESERVATION_OFFSET + i + 1}`, null, { ...params, tags: { name: 'cancel' } }));
}
