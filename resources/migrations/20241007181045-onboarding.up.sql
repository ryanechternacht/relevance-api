alter table user_account add column if not exists onboarding_step text default 'new';
--;;
alter table user_account drop constraint if exists user_account_onboarding_step;
--;;
alter table user_account add constraint user_account_onboarding_step check (onboarding_step in ('new', 'relevancies', 'linkedin', 'done'));
