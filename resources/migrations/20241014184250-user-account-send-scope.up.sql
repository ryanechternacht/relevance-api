alter table user_account add column if not exists has_send_scope boolean default false;
--;;
alter table user_account add column if not exists refresh_token text;
