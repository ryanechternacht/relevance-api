alter table user_account add column if not exists public_link_message text;
--;;
update user_account
set public_link_message =
$$Hey,

Thanks for reaching out! Can you tell me more about how this matches what I'm working on?

Here's the link to share more info: {{ profileLink }}.

Thanks!$$
where public_link_message is null;
