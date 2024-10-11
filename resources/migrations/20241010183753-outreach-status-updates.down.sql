alter table outreach add column if not exists status text default 'new';
--;;
alter table outreach add constraint outreach_status check (status in ('new', 'ignored', 'spam', 'starred', 'replied'));
--;;

-- set all to ignored, then we'll use the tags to set the status correctly
update outreach
set status = 'ignored';
--;;

update outreach
set status = 'new'
where is_new is true;
--;;

update outreach
set status = 'replied'
where has_replied is true;
--;;

update outreach
set status = 'spam'
where is_spam = true;
--;;

update outreach
set status = 'starred'
where is_saved = true;
--;;

alter table outreach drop column is_new;
--;;
alter table outreach drop column has_replied;
--;;
alter table outreach drop column is_saved;
--;;
alter table outreach drop column is_spam;
