-- 부하 테스트 시드. 모든 테이블을 비우므로 waitlist_load DB에서만 실행한다.
--
-- psql 변수
--   m_slots, m_capacity : 측정 대상 회차 수와 정원 (id 1..m_slots)
--   r_per, w_per        : 측정 대상 회차당 확정 예약 수, 대기 수
--   warm_capacity, warm_r, warm_w : 워밍업 회차 10개의 설정 (id m_slots+1..m_slots+10)
--
-- 사용자 id: 1..10000 측정용, 10001..15000 워밍업용, 15001 운영자
-- 측정 대상 예약은 예약 id = 사용자 id = 1..(m_slots * r_per) 이고, id 순서대로 회차를 돌아가며 배정한다.

DO $$
BEGIN
    IF current_database() <> 'waitlist_load' THEN
        RAISE EXCEPTION 'seed.sql은 waitlist_load DB에서만 실행할 수 있습니다 (현재: %)', current_database();
    END IF;
END $$;

BEGIN;

TRUNCATE notification, reservation, waitlist_entry, slot, users RESTART IDENTITY CASCADE;

INSERT INTO users (email, password_hash, name, role)
SELECT 'load' || n || '@example.test', 'not-used', 'load' || n, 'USER'
FROM generate_series(1, 15000) AS n
ORDER BY n;

INSERT INTO users (email, password_hash, name, role)
VALUES ('operator@example.test', 'not-used', 'operator', 'OPERATOR');

-- 측정 대상 회차
INSERT INTO slot (owner_id, title, start_at, capacity, waitlist_limit)
SELECT 15001, '측정 ' || n, now() + interval '30 days', :m_capacity, 100000
FROM generate_series(1, :m_slots) AS n
ORDER BY n;

-- 워밍업 회차
INSERT INTO slot (owner_id, title, start_at, capacity, waitlist_limit)
SELECT 15001, '워밍업 ' || n, now() + interval '30 days', :warm_capacity, 100000
FROM generate_series(1, 10) AS n
ORDER BY n;

-- 배경 데이터용 회차 (테이블이 너무 작으면 실행 계획이 실제와 달라진다)
INSERT INTO slot (owner_id, title, start_at, capacity)
SELECT 15001, '배경 ' || n, now() + interval '30 days' + n * interval '1 minute', 10
FROM generate_series(1, 2000) AS n
ORDER BY n;

-- 측정 대상 예약과 대기
INSERT INTO reservation (slot_id, user_id, status)
SELECT s, (k - 1) * :m_slots + s, 'CONFIRMED'
FROM generate_series(1, :r_per) AS k, generate_series(1, :m_slots) AS s
ORDER BY k, s;

INSERT INTO waitlist_entry (slot_id, user_id, status, created_at)
SELECT s, :m_slots * :r_per + (k - 1) * :m_slots + s, 'WAITING',
       now() - interval '1 day' + k * interval '1 millisecond'
FROM generate_series(1, :w_per) AS k, generate_series(1, :m_slots) AS s
ORDER BY k, s;

-- 워밍업 예약과 대기
INSERT INTO reservation (slot_id, user_id, status)
SELECT :m_slots + s, 10000 + (k - 1) * 10 + s, 'CONFIRMED'
FROM generate_series(1, :warm_r) AS k, generate_series(1, 10) AS s
ORDER BY k, s;

INSERT INTO waitlist_entry (slot_id, user_id, status, created_at)
SELECT :m_slots + s, 10000 + 10 * :warm_r + (k - 1) * 10 + s, 'WAITING',
       now() - interval '1 day' + k * interval '1 millisecond'
FROM generate_series(1, :warm_w) AS k, generate_series(1, 10) AS s
ORDER BY k, s;

-- 배경 예약 20,000건, 배경 대기 10,000건
INSERT INTO reservation (slot_id, user_id, status)
SELECT :m_slots + 10 + f, ((f * 15 + k) % 15000) + 1, 'CONFIRMED'
FROM generate_series(1, 2000) AS f, generate_series(1, 10) AS k;

INSERT INTO waitlist_entry (slot_id, user_id, status)
SELECT :m_slots + 10 + f, ((f * 15 + 10 + k) % 15000) + 1, 'WAITING'
FROM generate_series(1, 2000) AS f, generate_series(1, 5) AS k;

COMMIT;

ANALYZE;
