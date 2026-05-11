-- Day 5a: users table for register/login. password_hash is sized for bcrypt
-- (60 chars) plus headroom for future hash-format prefix changes; the role
-- column carries an explicit CHECK so the enum is enforced at the DB level
-- alongside the entity-side @Enumerated mapping.

CREATE TABLE public.users (
    id uuid NOT NULL,
    username varchar(30) NOT NULL,
    password_hash varchar(72) NOT NULL,
    role varchar(16) NOT NULL DEFAULT 'USER',
    created_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);
