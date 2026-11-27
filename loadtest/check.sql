-- 측정 대상 회차(id <= m_slots)의 불변식 위반을 센다.
-- 출력: 정원 초과 좌석 | 발급되지 않은 빈자리 | 알림 없는 기회 | 중복 참여
WITH per_slot AS (
    SELECT s.id,
           s.capacity,
           (SELECT count(*) FROM reservation r WHERE r.slot_id = s.id AND r.status = 'CONFIRMED')
         + (SELECT count(*) FROM waitlist_entry w WHERE w.slot_id = s.id AND w.status = 'OFFERED') AS occupied,
           (SELECT count(*) FROM waitlist_entry w WHERE w.slot_id = s.id AND w.status = 'WAITING') AS waiting
    FROM slot s
    WHERE s.id <= :m_slots
),
active AS (
    SELECT slot_id, user_id FROM reservation WHERE status = 'CONFIRMED' AND slot_id <= :m_slots
    UNION ALL
    SELECT slot_id, user_id FROM waitlist_entry WHERE status IN ('WAITING', 'OFFERED') AND slot_id <= :m_slots
)
SELECT
    -- 불변식 1: 확정 + 기회 <= 정원
    (SELECT coalesce(sum(greatest(occupied - capacity, 0)), 0) FROM per_slot) AS oversold,
    -- 빈자리와 대기자가 함께 남아 있으면 승계가 누락된 것
    (SELECT coalesce(sum(greatest(least(capacity - occupied, waiting), 0)), 0) FROM per_slot) AS not_offered,
    (SELECT count(*) FROM waitlist_entry w
      WHERE w.slot_id <= :m_slots AND w.status = 'OFFERED'
        AND NOT EXISTS (SELECT 1 FROM notification n
                        WHERE n.waitlist_entry_id = w.id AND n.type = 'OFFER_GRANTED')) AS missing_notification,
    -- 불변식 2: 사용자당 회차 1개에 활성 상태 1건
    (SELECT count(*) FROM (SELECT 1 FROM active GROUP BY slot_id, user_id HAVING count(*) > 1) d) AS duplicated;
