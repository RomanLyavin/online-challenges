-- Вступление в челлендж с веб-сайта.
-- Применяется в Supabase Dashboard → SQL Editor или через `supabase db push`.
-- Участие хранится в Supabase, поэтому видно на всех устройствах после входа.

create table if not exists public.challenge_participants (
    challenge_id text not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    user_name text not null default '',
    joined_at timestamptz not null default now(),
    primary key (challenge_id, user_id)
);

comment on table public.challenge_participants is
    'Участие пользователя в челлендже: веб-версия «Онлайн челленджей».';

alter table public.challenge_participants enable row level security;

-- Политики не отменяют grant'ы: сначала убираем все права клиентских ролей.
revoke all on table public.challenge_participants from anon, authenticated;

-- Список участников виден всем, включая неавторизованных посетителей сайта.
grant select on table public.challenge_participants to anon, authenticated;
-- Вступать и выходить можно только от своего имени.
grant insert, update, delete on table public.challenge_participants to authenticated;

drop policy if exists "Anyone can read challenge participants" on public.challenge_participants;
create policy "Anyone can read challenge participants"
on public.challenge_participants for select
to anon, authenticated
using (true);

drop policy if exists "Users can join challenges" on public.challenge_participants;
create policy "Users can join challenges"
on public.challenge_participants for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "Users can update their participation" on public.challenge_participants;
create policy "Users can update their participation"
on public.challenge_participants for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "Users can leave challenges" on public.challenge_participants;
create policy "Users can leave challenges"
on public.challenge_participants for delete
to authenticated
using ((select auth.uid()) = user_id);

create index if not exists challenge_participants_challenge_id_idx
on public.challenge_participants using btree (challenge_id);
