set timezone = 'Etc/UTC';
--;;

-- credit https://x-team.com/blog/automatic-timestamps-with-postgresql/
create or replace function trigger_insert_timestamps()
returns trigger as $$
begin 
  new.created_at = CURRENT_TIMESTAMP;
  new.updated_at = CURRENT_TIMESTAMP;
  return new;
end;
$$ LANGUAGE plpgsql;
--;;

create or replace function trigger_update_timestamp()
returns trigger as $$
begin 
  new.updated_at = CURRENT_TIMESTAMP;
  return new;
end;
$$ LANGUAGE plpgsql;
--;;

create table user_account (
  email text primary key,
  first_name text,
  last_name text,
  mail_sync_status text default 'setup-required',
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint user_account_mail_sync_status check (mail_sync_status in ('setup-required', 'ready', 'error'))
);
--;;

create trigger user_account_insert_timestamp
before insert on user_account
for each row execute procedure trigger_insert_timestamps();
--;;
create trigger user_account_update_timestamp
before update on user_account
for each row execute procedure trigger_update_timestamp();
