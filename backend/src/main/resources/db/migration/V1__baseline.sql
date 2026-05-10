-- Baseline of the Day 1 - Day 4 schema, generated from a live Postgres 16
-- via `pg_dump --schema-only --no-owner --no-privileges --no-comments` and
-- trimmed of psql-only directives (\restrict, SET headers, search_path
-- pre-amble) that Flyway does not need.
--
-- Constraint and index names are reproduced verbatim from the dump because
-- they appear in the live development database. `fk3sp86rbymfj7ir0weclara7kk`,
-- `fkq5v9097ori4mccgtimbob54qs`, and `ukq6rly4ss4hss18pkkcoug7bi` are
-- Hibernate-generated names from `ddl-auto: update` that we adopt as the
-- canonical names so fresh-DB and existing-DB schemas converge.
--
-- `replicas_status_check` includes CRASHLOOP_BACKOFF — Hibernate's original
-- 5-value CHECK was manually dropped and recreated during Day 4 to admit
-- the new status, and that is the constraint definition this baseline
-- captures.
--
-- application.yml ships `spring.flyway.baseline-on-migrate=true` and
-- `baseline-version=1`, so when Flyway sees an existing populated database
-- with no `flyway_schema_history` table it records V1 as already applied
-- without executing it. On a fresh database both V1 and the migrations
-- after it run normally.

CREATE TABLE public.deployment_events (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    message character varying(1024) NOT NULL,
    type character varying(32) NOT NULL,
    deployment_id uuid NOT NULL
);

CREATE TABLE public.deployments (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    desired_replicas integer NOT NULL,
    env jsonb,
    image character varying(255) NOT NULL,
    name character varying(40) NOT NULL,
    ports jsonb,
    status character varying(16) NOT NULL,
    tag character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    last_observed_status character varying(16),
    probe jsonb,
    CONSTRAINT deployments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'FAILED'::character varying, 'DELETING'::character varying])::text[])))
);

CREATE TABLE public.replicas (
    id uuid NOT NULL,
    container_id character varying(64),
    container_name character varying(80) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_error character varying(1024),
    replica_index integer NOT NULL,
    status character varying(32) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    deployment_id uuid NOT NULL,
    last_inspected_at timestamp(6) with time zone,
    last_restart_at timestamp(6) with time zone,
    restart_count integer DEFAULT 0 NOT NULL,
    consecutive_failures integer DEFAULT 0 NOT NULL,
    failure_window jsonb,
    last_probe_at timestamp(6) with time zone,
    last_probe_result character varying(16),
    probe_details character varying(1024),
    CONSTRAINT replicas_last_probe_result_check CHECK (((last_probe_result)::text = ANY ((ARRAY['NOT_PROBED'::character varying, 'PASSING'::character varying, 'FAILING'::character varying])::text[]))),
    CONSTRAINT replicas_status_check CHECK (((status)::text = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'EXITED'::text, 'FAILED'::text, 'REMOVED'::text, 'CRASHLOOP_BACKOFF'::text])))
);

ALTER TABLE ONLY public.deployment_events
    ADD CONSTRAINT deployment_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.deployments
    ADD CONSTRAINT deployments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.replicas
    ADD CONSTRAINT replicas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.replicas
    ADD CONSTRAINT uk_replica_deployment_index UNIQUE (deployment_id, replica_index);

ALTER TABLE ONLY public.deployments
    ADD CONSTRAINT ukq6rly4ss4hss18pkkcoug7bi UNIQUE (name);

CREATE INDEX idx_event_deployment_created ON public.deployment_events USING btree (deployment_id, created_at);

ALTER TABLE ONLY public.replicas
    ADD CONSTRAINT fk3sp86rbymfj7ir0weclara7kk FOREIGN KEY (deployment_id) REFERENCES public.deployments(id);

ALTER TABLE ONLY public.deployment_events
    ADD CONSTRAINT fkq5v9097ori4mccgtimbob54qs FOREIGN KEY (deployment_id) REFERENCES public.deployments(id);
