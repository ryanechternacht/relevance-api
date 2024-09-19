alter table gmail_sync drop column last_seen_email_id
--;;
alter table gmail_sync add column history_id int;
--;;
alter table gmail_sync add column label_id text;
