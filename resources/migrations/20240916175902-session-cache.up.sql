create table session_cache (
  stytch_session_id text primary key,
  stytch_member_json jsonb not null,
  valid_until timestamp with time zone not null
);
