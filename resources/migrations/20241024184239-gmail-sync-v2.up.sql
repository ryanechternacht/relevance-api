alter table gmail_sync drop column token;
--;;
alter table gmail_sync drop column oauth_state;
--;;
alter table user_account add column if not exists has_gmail_modify_scope 
  boolean default false;
--;;
alter table gmail_sync add column if not exists is_enabled boolean default true;
