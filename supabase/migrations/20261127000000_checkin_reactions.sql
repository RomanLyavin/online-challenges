-- Реакции на отметки участников.
-- Применяется в Supabase Dashboard → SQL Editor или через `supabase db push`.
--
-- Идентификатор отметки детерминированный: "<challengeId>|<userCode>|<date>".
-- Он совпадает в Android и веб-версии, поэтому не требует миграции старых данных.

create table if not exists public.checkin_reactions (
    checkin_id text not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    reaction text not null check (reaction in ('like', 'fire', 'dislike', 'heart')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (checkin_id, user_id)
);

comment on table public.checkin_reactions is
    'Одна реакция пользователя на отметку участника: like, fire, dislike, heart.';

alter table public.checkin_reactions enable row level security;

-- Политики не отменяют grant'ы: сначала убираем все права клиентских ролей.
revoke all on table public.checkin_reactions from anon, authenticated;

-- Счётчики видны всем, включая неавторизованных посетителей сайта.
grant select on table public.checkin_reactions to anon, authenticated;
-- Изменять можно только свою реакцию.
grant insert, update, delete on table public.checkin_reactions to authenticated;

drop policy if exists "Anyone can read check-in reactions" on public.checkin_reactions;
create policy "Anyone can read check-in reactions"
on public.checkin_reactions for select
to anon, authenticated
using (true);

drop policy if exists "Users can add their own reaction" on public.checkin_reactions;
create policy "Users can add their own reaction"
on public.checkin_reactions for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "Users can change their own reaction" on public.checkin_reactions;
create policy "Users can change their own reaction"
on public.checkin_reactions for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "Users can remove their own reaction" on public.checkin_reactions;
create policy "Users can remove their own reaction"
on public.checkin_reactions for delete
to authenticated
using ((select auth.uid()) = user_id);

create index if not exists checkin_reactions_checkin_id_idx
on public.checkin_reactions using btree (checkin_id);

create index if not exists checkin_reactions_user_id_idx
on public.checkin_reactions using btree (user_id);
