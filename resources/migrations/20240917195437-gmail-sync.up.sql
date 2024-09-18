create table gmail_sync (
  user_email text primary key references user_account(email),
  status text not null default 'setup',
  token jsonb,
  last_seen_email_id text,
  oauth_state text,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint gmail_sync_status check (status in ('setup', 'active', 'error'))
);
--;;
create trigger gmail_sync_insert_timestamp
before insert on gmail_sync
for each row execute procedure trigger_insert_timestamps();
--;;
create trigger gmail_sync_update_timestamp
before update on gmail_sync
for each row execute procedure trigger_update_timestamp();