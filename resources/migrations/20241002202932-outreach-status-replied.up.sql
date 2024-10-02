alter table outreach drop constraint if exists outreach_status;
--;;
alter table outreach add constraint outreach_status check (status in ('new', 'ignored', 'spam', 'starred', 'replied'));
