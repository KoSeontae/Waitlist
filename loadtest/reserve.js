// 예약 신청: 서로 다른 사용자가 회차에 한 번씩 예약한다.
import http from 'k6/http';
import exec from 'k6/execution';
import { BASE_URL, env, options as baseOptions, authParams, record } from './lib.js';

export { handleSummary } from './lib.js';
export const options = baseOptions();

const SLOT_OFFSET = env('SLOT_OFFSET');
const SLOTS = env('SLOTS');
const USER_OFFSET = env('USER_OFFSET');

export default function () {
  const i = exec.scenario.iterationInTest;
  const slotId = SLOT_OFFSET + (i % SLOTS) + 1;
  const params = authParams(USER_OFFSET + i + 1);
  record(http.post(`${BASE_URL}/slots/${slotId}/reservations`, null, { ...params, tags: { name: 'reserve' } }));
}
