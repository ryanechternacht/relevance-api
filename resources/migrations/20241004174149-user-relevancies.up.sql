alter table user_account add column if not exists relevancies jsonb default '[]'::jsonb;
--;;
alter table outreach add column if not exists relevant_emoji text;
--;;
alter table outreach add column if not exists relevant_description text;
