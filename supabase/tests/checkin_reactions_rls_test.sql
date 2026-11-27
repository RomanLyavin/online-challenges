-- Проверка RLS для public.checkin_reactions.
-- Создать: supabase test new checkin_reactions_rls
-- Запустить: supabase test db

begin;
select plan(13);

insert into auth.users (id, email)
values
    ('11111111-1111-1111-1111-111111111111', 'owner@example.com'),
    ('22222222-2222-2222-2222-222222222222', 'other@example.com');

select ok(
    not has_table_privilege('anon', 'public.checkin_reactions', 'insert,update,delete'),
    'anon has no write grants'
);
select ok(
    not has_table_privilege('authenticated', 'public.checkin_reactions', 'truncate,references,trigger'),
    'authenticated has no administrative grants'
);

set local role anon;
select lives_ok(
    $$select checkin_id from public.checkin_reactions$$,
    'anon can read reactions'
);
select throws_ok(
    $$insert into public.checkin_reactions (checkin_id, user_id, reaction)
      values ('c|u|2026-11-27', '11111111-1111-1111-1111-111111111111', 'like')$$,
    '42501', null, 'anon cannot insert'
);
select throws_ok(
    $$update public.checkin_reactions set reaction = 'fire'$$,
    '42501', null, 'anon cannot update'
);
select throws_ok(
    $$delete from public.checkin_reactions$$,
    '42501', null, 'anon cannot delete'
);

set local role authenticated;
set local request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
select results_eq(
    $$insert into public.checkin_reactions (checkin_id, user_id, reaction)
      values ('c|u|2026-11-27', '11111111-1111-1111-1111-111111111111', 'fire')
      returning reaction$$,
    array['fire'], 'owner can insert own reaction'
);
select results_eq(
    $$select reaction from public.checkin_reactions
      where checkin_id = 'c|u|2026-11-27'$$,
    array['fire'], 'owner can read own reaction'
);
select results_eq(
    $$update public.checkin_reactions set reaction = 'heart'
      where checkin_id = 'c|u|2026-11-27' returning reaction$$,
    array['heart'], 'owner can change own reaction'
);

set local request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
select throws_ok(
    $$insert into public.checkin_reactions (checkin_id, user_id, reaction)
      values ('c|u|2026-11-27', '11111111-1111-1111-1111-111111111111', 'like')$$,
    '42501', null, 'other user cannot insert for owner'
);
select is_empty(
    $$update public.checkin_reactions set reaction = 'dislike'
      where checkin_id = 'c|u|2026-11-27' returning reaction$$,
    'other user cannot update owner reaction'
);
select is_empty(
    $$delete from public.checkin_reactions
      where checkin_id = 'c|u|2026-11-27' returning checkin_id$$,
    'other user cannot delete owner reaction'
);

set local request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
select results_eq(
    $$select reaction from public.checkin_reactions
      where checkin_id = 'c|u|2026-11-27'$$,
    array['heart'], 'denied writes left owner reaction intact'
);
select results_eq(
    $$delete from public.checkin_reactions
      where checkin_id = 'c|u|2026-11-27' returning reaction$$,
    array['heart'], 'owner can delete own reaction'
);

select * from finish();
rollback;
