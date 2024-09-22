alter table outreach add column linkedin_url text;
--;;
alter table outreach add column calendar_url text;
--;;
alter table outreach alter column sender drop not null;
--;;
alter table outreach add column company_name text;
--;;
alter table outreach add column company_logo_url text;
