# Supabase: настройка для 1.6.0

## 1. Таблица реакций

Выполните `migrations/20261127000000_checkin_reactions.sql` в **SQL Editor**
или через `supabase db push`. Проверка политик — `tests/checkin_reactions_rls_test.sql`
(команды `supabase test new checkin_reactions_rls`, `supabase test db`).

RLS-политики разрешают всем читать счётчики и только автору — создавать, менять
и удалять свою реакцию. В базе хранится одна реакция на пару
`(checkin_id, user_id)`; идентификатор отметки имеет вид
`"<challengeId>|<userCode>|<date>"` и вычисляется одинаково в Android и вебе.

## 2. Подтверждение email кодом

Личный кабинет на сайте подтверждает почту шестизначным кодом. Supabase
поддерживает переменную `{{ .Token }}` в шаблонах писем; она содержит 6-значный
OTP. Чтобы старый Android-поток по ссылке продолжал работать, шаблоны должны
содержать **и код, и ссылку**.

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

## 3. URL

В **Authentication → URL Configuration**:

- Site URL: `https://romanlyavin.github.io/online-challenges/`
- Redirect URLs: тот же адрес, `https://romanlyavin.github.io/online-challenges/*`
  и `com.onlinechallenges.app://auth`.

Ссылки из писем для Android по-прежнему открывают приложение через
`com.onlinechallenges.app://auth`; веб использует коды и потому не зависит от
deep link на компьютере.

Секретный ключ (`service_role`) в приложение и на сайт не добавляется.
Используется только publishable key (`sb_publishable_...`).
