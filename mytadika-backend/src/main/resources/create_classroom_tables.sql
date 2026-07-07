-- Run this in the Supabase SQL Editor (Dashboard > SQL Editor > New query)
-- Only needed if you cannot restart the Spring Boot server

CREATE TABLE IF NOT EXISTS classrooms (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    section     VARCHAR(255),
    description TEXT,
    teacher_account_id VARCHAR(255) NOT NULL,
    color       VARCHAR(30) DEFAULT 'indigo',
    class_code  VARCHAR(20) UNIQUE,
    created_at  TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS class_members (
    id           BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    account_id   VARCHAR(255) NOT NULL,
    role         VARCHAR(20) NOT NULL DEFAULT 'student',
    joined_at    TIMESTAMP,
    UNIQUE (classroom_id, account_id)
);

CREATE TABLE IF NOT EXISTS announcements (
    id                BIGSERIAL PRIMARY KEY,
    classroom_id      BIGINT NOT NULL,
    author_account_id VARCHAR(255) NOT NULL,
    content           TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS assignments (
    id           BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    due_date     VARCHAR(10),
    points       INTEGER,
    topic        VARCHAR(100),
    created_at   TIMESTAMP NOT NULL
);
