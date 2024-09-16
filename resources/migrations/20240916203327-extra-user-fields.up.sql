alter table user_account add column if not exists image text;
--;;
alter table user_account add column public_link text;
--;;
alter table user_account drop constraint if exists user_account_public_link;
--;;
alter table user_account add constraint user_account_public_link unique (public_link);
