ALTER TABLE public.ai_request_logs
DROP CONSTRAINT IF EXISTS chk_ai_request_logs_agent_type;

ALTER TABLE public.ai_request_logs
ADD CONSTRAINT chk_ai_request_logs_agent_type
CHECK (agent_type IN ('PLANNER', 'CRITIC', 'SUMMARIZER', 'RESEARCHER', 'SHOPPING', 'GITHUB', 'KAPRUKA'));
