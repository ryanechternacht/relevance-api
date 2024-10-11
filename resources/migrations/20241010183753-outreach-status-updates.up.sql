alter table outreach add column is_new boolean default true;
--;;
update outreach
set is_new = false
where status <> 'new';
--;;


alter table outreach add column has_replied boolean default false;
--;;
update outreach
set has_replied = true
where status = 'replied';
--;;

alter table outreach add column is_spam boolean default false;
--;;
update outreach
set is_spam = true
where status = 'spam';
--;;

alter table outreach add column is_saved boolean default false;
--;;
update outreach
set is_saved = true
where status = 'starred';
--;;

alter table outreach drop column status;
