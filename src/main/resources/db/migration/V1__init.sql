CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'OPERATOR'))
);

CREATE TABLE slot (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id              BIGINT       NOT NULL REFERENCES users (id),
    title                 VARCHAR(200) NOT NULL,
    start_at              TIMESTAMPTZ  NOT NULL,
    capacity              INT          NOT NULL,
    blocked_manual        BOOLEAN      NOT NULL DEFAULT FALSE,
    blocked_over_capacity BOOLEAN      NOT NULL DEFAULT FALSE,
    accept_limit_min      INT          NOT NULL DEFAULT 30,
    waitlist_limit        INT          NOT NULL DEFAULT 20,
    cancel_before_min     INT          NOT NULL DEFAULT 60,
    offer_stop_before_min INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_slot_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_slot_accept_limit CHECK (accept_limit_min BETWEEN 5 AND 1440),
    CONSTRAINT ck_slot_waitlist_limit CHECK (waitlist_limit >= 0),
    CONSTRAINT ck_slot_offer_stop CHECK (offer_stop_before_min >= 0),
    CONSTRAINT ck_slot_cancel_ge_stop CHECK (cancel_before_min >= offer_stop_before_min)
);

CREATE TABLE waitlist_entry (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slot_id     BIGINT      NOT NULL REFERENCES slot (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    status      VARCHAR(20) NOT NULL,
    offered_at  TIMESTAMPTZ,
    expires_at  TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_waitlist_status CHECK (status IN ('WAITING', 'OFFERED', 'ACCEPTED', 'EXPIRED', 'CANCELED'))
);

CREATE TABLE reservation (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slot_id           BIGINT      NOT NULL REFERENCES slot (id),
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    waitlist_entry_id BIGINT UNIQUE REFERENCES waitlist_entry (id),
    status            VARCHAR(20) NOT NULL,
    canceled_by       VARCHAR(20),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    canceled_at       TIMESTAMPTZ,
    CONSTRAINT ck_reservation_status CHECK (status IN ('CONFIRMED', 'CANCELED')),
    CONSTRAINT ck_reservation_canceled_by CHECK (canceled_by IS NULL OR canceled_by IN ('USER', 'OPERATOR'))
);

CREATE TABLE notification (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    type              VARCHAR(50) NOT NULL,
    slot_id           BIGINT REFERENCES slot (id),
    waitlist_entry_id BIGINT REFERENCES waitlist_entry (id),
    channel           VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count       INT         NOT NULL DEFAULT 0,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP', 'SMS')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- 불변식 2: 사용자당 회차 1개에 활성 상태 1건 (테이블 내 중복 방지)
CREATE UNIQUE INDEX ux_reservation_active ON reservation (slot_id, user_id) WHERE status = 'CONFIRMED';
CREATE UNIQUE INDEX ux_waitlist_active ON waitlist_entry (slot_id, user_id) WHERE status IN ('WAITING', 'OFFERED');

CREATE INDEX ix_waitlist_queue ON waitlist_entry (slot_id, status, created_at, id);
CREATE INDEX ix_waitlist_expiry ON waitlist_entry (status, expires_at);
CREATE INDEX ix_waitlist_user ON waitlist_entry (user_id, status);
CREATE INDEX ix_reservation_slot ON reservation (slot_id, status);
CREATE INDEX ix_reservation_user ON reservation (user_id, status);
CREATE INDEX ix_slot_owner_start ON slot (owner_id, start_at);
CREATE INDEX ix_slot_start ON slot (start_at);
CREATE INDEX ix_notification_user ON notification (user_id, created_at);
