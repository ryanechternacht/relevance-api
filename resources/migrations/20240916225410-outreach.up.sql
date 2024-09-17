create table outreach (
  uuid uuid primary key,
  recipient text not null references user_account (email),
  sender text not null,
  snippet text,
  body text,
  company_type text,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint outreach_company_type check (company_type in ('software', 'services'))
);
--;;
create trigger outreach_insert_timestamp
before insert on outreach
for each row execute procedure trigger_insert_timestamps();
--;;
create trigger outreach_update_timestamp
before update on outreach
for each row execute procedure trigger_update_timestamp();
