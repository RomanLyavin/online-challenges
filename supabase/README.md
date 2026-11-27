# Supabase: настройка для 1.6.x

## 0. Веб-регистрация без письма (1.6.3)

Чтобы **Android подтверждение email продолжало работать** (`Confirm email = ON`), веб-регистрация идёт через Edge Function `web-signup`.

1. Оставьте **Authentication → Providers → Email → Confirm email = ON**.
2. Задеплойте функцию из репозитория:

```bash
supabase functions deploy web-signup --project-ref <PROJECT_REF> --no-verify-jwt
```

3. В функции используются встроенные секреты Supabase Edge Runtime: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`. Добавлять их вручную обычно не нужно.
4. Функция создаёт пользователя через Admin API с `email_confirm: true`, поэтому письмо не отправляется. Сайт после этого выполняет обычный `signInWithPassword`.
5. Смена email и сброс пароля на сайте отключены, потому что при `Confirm email = ON` они требуют почтовый поток. В Android эти сценарии работают через существующее подтверждение.

Проверка:

```bash
curl -X POST "https://<PROJECT_REF>.supabase.co/functions/v1/web-signup" \
  -H "Content-Type: application/json" \
  -H "apikey: <PUBLISHABLE_KEY>" \
  -d '{"email":"test@example.com","password":"123456","name":"Test"}'
```

Ожидаемый ответ: `{"ok":true,"userId":"..."}`. Повторный запрос с тем же email должен вернуть 409.

## 1. Таблица реакций

Выполните `migrations/20261127000000_checkin_reactions.sql` в **SQL Editor**
или через `supabase db push`. Проверка политик — `tests/checkin_reactions_rls_test.sql`
(команды `supabase test new checkin_reactions_rls`, `supabase test db`).

RLS-политики разрешают всем читать счётчики и только автору — создавать, менять
и удалять свою реакцию. В базе хранится одна реакция на пару
`(checkin_id, user_id)`; идентификатор отметки имеет вид
`"<challengeId>|<userCode>|<date>"` и вычисляется одинаково в Android и вебе.

## 2. Подтверждение email кодом (Android)

Android-приложение подтверждает почту кодом/ссылкой из письма Supabase. Веб с версии 1.6.3
не использует email-подтверждение: регистрация идёт через Edge Function `web-signup`, чтобы
можно было оставить `Confirm email = ON` для Android.

Если настраиваете письма для Android, шаблоны должны содержать **и код, и ссылку** (`{{ .Token }}` и `{{ .ConfirmationURL }}`).

В **Authentication → Email Templates** замените шаблоны:

### Confirm signup

Тема: `Код подтверждения Онлайн челленджей`

```html
<h2>Подтверждение регистрации</h2>
<p>Ваш код: <strong>{{ .Token }}</strong></p>
<p>Код действует один час. Введите его на сайте или в приложении.</p>
<p><a href="{{ .ConfirmationURL }}">Подтвердить по ссылке</a></p>
```

### Magic Link / OTP (для сброса пароля в вебе)

Тема: `Восстановление пароля Онлайн челленджей`

```html
<h2>Восстановление пароля</h2>
<p>Ваш код: <strong>{{ .Token }}</strong></p>
<p>Введите код на странице сброса пароля.</p>
<p><a href="{{ .ConfirmationURL }}">Сбросить по ссылке</a></p>
```

### Reset password

Тема: `Новый пароль Онлайн челленджей`

```html
<h2>Новый пароль</h2>
<p>Ваш код: <strong>{{ .Token }}</strong></p>
<p>Введите код и новый пароль на странице восстановления.</p>
<p><a href="{{ .ConfirmationURL }}">Сменить пароль по ссылке</a></p>
```

### Change email address

Тема: `Подтверждение нового email`

```html
<h2>Подтверждение нового email</h2>
<p>Новый адрес: {{ .NewEmail }}</p>
<p>Ваш код: <strong>{{ .Token }}</strong></p>
<p><a href="{{ .ConfirmationURL }}">Подтвердить по ссылке</a></p>
```

### Reauthentication

Тема: `Код подтверждения Онлайн челленджей`

```html
<p>Ваш код: <strong>{{ .Token }}</strong></p>
```

## 3. Таблица участия в челленджах (1.6.1)

Для кнопки «Присоединиться» на сайте выполните
`migrations/20261127010000_challenge_participants.sql` в **SQL Editor**.
RLS разрешает всем читать список участников, а добавлять/удалять запись —
только авторизованному пользователю от своего имени.

## 4. URL

В **Authentication → URL Configuration**:

- Site URL: `https://romanlyavin.github.io/online-challenges/`
- Redirect URLs: тот же адрес, `https://romanlyavin.github.io/online-challenges/*`
  и `com.onlinechallenges.app://auth`.

Ссылки из писем для Android по-прежнему открывают приложение через
`com.onlinechallenges.app://auth`; веб использует коды и потому не зависит от
deep link на компьютере.

Секретный ключ (`service_role`) в приложение и на сайт не добавляется.
Используется только publishable key (`sb_publishable_...`).
