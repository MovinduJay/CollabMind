DO $$
DECLARE
    constraint_record record;
BEGIN
    FOR constraint_record IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'ai_request_logs'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%agent_type%'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.ai_request_logs DROP CONSTRAINT IF EXISTS %I',
            constraint_record.conname
        );
    END LOOP;
END $$;

ALTER TABLE public.ai_request_logs
ADD CONSTRAINT chk_ai_request_logs_agent_type
CHECK (
    agent_type IN (
        'PLANNER',
        'CRITIC',
        'SUMMARIZER',
        'RESEARCHER',
        'SHOPPING',
        'GITHUB'
    )
);
