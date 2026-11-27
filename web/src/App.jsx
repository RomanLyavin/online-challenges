import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { loadStandings, recentCheckIns } from './lib/data.js'
import { isSupabaseConfigured, siteUrl, supabase, authMessage } from './lib/supabase.js'
import {
  isValidCode,
  isValidEmail,
  isValidPassword,
  normalizeEmail,
  profileFromUser
} from './lib/auth.js'
import { REACTIONS, checkInId, loadReactions, toggleReaction } from './lib/reactions.js'

const APP_VERSION = '1.6.0'
const TABS = [
  { id: 'home', label: 'Главная', icon: '🏠' },
  { id: 'catalog', label: 'Каталог', icon: '🎯' },
  { id: 'account', label: 'Профиль', icon: '👤' },
  { id: 'about', label: 'О программе', icon: 'ℹ️' }
]

function number(value) {
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

function formatNumber(value) {
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(number(value))
}

function App() {
  const [standings, setStandings] = useState(null)
  const [dataError, setDataError] = useState('')
  const [session, setSession] = useState(null)
  const [authReady, setAuthReady] = useState(!isSupabaseConfigured)
  const [route, setRoute] = useState(() => window.location.hash.slice(1) || 'home')
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: '', password: '', confirm: '', code: '' })
  const [pendingEmail, setPendingEmail] = useState('')
  const [pendingNewEmail, setPendingNewEmail] = useState('')
  const [profileDraft, setProfileDraft] = useState({ name: '', code: '', email: '' })
  const [emailDraft, setEmailDraft] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [summary, setSummary] = useState({ counts: {}, mine: {} })
  const [theme, setTheme] = useState(() => localStorage.getItem('online-challenges-theme') || 'light')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('online-challenges-theme', theme)
  }, [theme])

  useEffect(() => {
    loadStandings()
      .then(setStandings)
      .catch((err) => setDataError(err.message || 'Не удалось загрузить данные'))
  }, [])

  useEffect(() => {
    const onHash = () => setRoute(window.location.hash.slice(1) || 'home')
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => {
    if (!supabase) {
      setAuthReady(true)
      return undefined
    }
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session)
      setAuthReady(true)
      if (data.session && /type=recovery/.test(window.location.href)) {
        setMode('reset')
        setPendingEmail(data.session.user.email || '')
      }
    })
    const { data: listener } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)
      setAuthReady(true)
    })
    return () => listener.subscription.unsubscribe()
  }, [])

  useEffect(() => {
    if (session) setProfileDraft(profileFromUser(session.user))
  }, [session?.user?.id])

  const checkIns = useMemo(() => recentCheckIns(standings), [standings])
  const ids = useMemo(
    () => checkIns.map((item) => checkInId(item.challengeId, item.code, item.date)),
    [checkIns]
  )

  const refreshReactions = useCallback(async () => {
    if (!ids.length) return
    try {
      setSummary(await loadReactions(supabase, ids))
    } catch (err) {
      setMessage(authMessage(err))
    }
  }, [ids.join('|')])

  useEffect(() => {
    refreshReactions()
  }, [refreshReactions, session?.user?.id])

  function navigate(tab) {
    window.location.hash = tab
    setRoute(tab)
    setError('')
    setMessage('')
  }

  function updateForm(key, value) {
    setForm((current) => ({ ...current, [key]: value }))
    setError('')
  }

  async function run(action) {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (err) {
      setError(authMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function register() {
    const email = normalizeEmail(form.email)
    if (!isValidEmail(email)) return setError('Введите корректный email')
    if (!isValidPassword(form.password)) return setError('Пароль должен быть не короче 6 символов')
    if (form.password !== form.confirm) return setError('Пароли не совпадают')
    if (!supabase) return setError('Личный кабинет не настроен на этом сайте')
    await run(async () => {
      const { data, error: signUpError } = await supabase.auth.signUp({
        email,
        password: form.password,
        options: {
          emailRedirectTo: siteUrl(),
          data: { display_name: profileDraft.name || email.split('@')[0] }
        }
      })
      if (signUpError) throw signUpError
      if (data.user && Array.isArray(data.user.identities) && data.user.identities.length === 0) {
        throw new Error('Аккаунт с такой почтой уже есть — войдите или восстановите пароль')
      }
      setPendingEmail(email)
      setMode('verify')
      setMessage(`Отправили код подтверждения на ${email}`)
    })
  }

  async function verifySignup() {
    if (!isValidCode(form.code)) return setError('Введите шестизначный код из письма')
    await run(async () => {
      const { error: verifyError } = await supabase.auth.verifyOtp({
        email: pendingEmail,
        token: form.code.trim(),
        type: 'email'
      })
      if (verifyError) throw verifyError
      setForm((current) => ({ ...current, code: '' }))
      setMessage('Email подтверждён')
      setMode('login')
    })
  }

  async function login() {
    const email = normalizeEmail(form.email)
    if (!isValidEmail(email)) return setError('Введите корректный email')
    if (!supabase) return setError('Личный кабинет не настроен на этом сайте')
    await run(async () => {
      const { error: signInError } = await supabase.auth.signInWithPassword({
        email,
        password: form.password
      })
      if (signInError) throw signInError
      setMessage('Вы вошли в аккаунт')
      setForm((current) => ({ ...current, password: '', confirm: '' }))
    })
  }

  async function requestReset() {
    const email = normalizeEmail(form.email)
    if (!isValidEmail(email)) return setError('Введите корректный email')
    await run(async () => {
      const { error: resetError } = await supabase.auth.resetPasswordForEmail(email, {
        redirectTo: siteUrl()
      })
      if (resetError) throw resetError
      setPendingEmail(email)
      setMode('reset')
      setMessage(`Отправили код для сброса на ${email}`)
    })
  }

  async function resetPassword() {
    if (!isValidCode(form.code)) return setError('Введите шестизначный код из письма')
    if (!isValidPassword(form.password)) return setError('Пароль должен быть не короче 6 символов')
    if (form.password !== form.confirm) return setError('Пароли не совпадают')
    await run(async () => {
      const { error: verifyError } = await supabase.auth.verifyOtp({
        email: pendingEmail,
        token: form.code.trim(),
        type: 'recovery'
      })
      if (verifyError) throw verifyError
      const { error: updateError } = await supabase.auth.updateUser({ password: form.password })
      if (updateError) throw updateError
      setForm({ email: '', password: '', confirm: '', code: '' })
      setMessage('Пароль обновлён')
      setMode('login')
    })
  }

  async function changeEmail() {
    const email = normalizeEmail(emailDraft)
    if (!isValidEmail(email)) return setError('Введите корректный email')
    await run(async () => {
      const { error: updateError } = await supabase.auth.updateUser({ email })
      if (updateError) throw updateError
      setPendingNewEmail(email)
      setMode('confirm-email')
      setMessage(`Отправили код подтверждения на ${email}`)
    })
  }

  async function confirmEmailChange() {
    if (!isValidCode(form.code)) return setError('Введите шестизначный код из письма')
    await run(async () => {
      const { error: verifyError } = await supabase.auth.verifyOtp({
        email: pendingNewEmail,
        token: form.code.trim(),
        type: 'email_change'
      })
      if (verifyError) throw verifyError
      setForm((current) => ({ ...current, code: '' }))
      setPendingNewEmail('')
      setMessage('Email изменён')
      setMode('account')
    })
  }

  async function saveProfile() {
    await run(async () => {
      const { error: updateError } = await supabase.auth.updateUser({
        data: {
          display_name: profileDraft.name.trim(),
          participant_code: profileDraft.code.trim().toUpperCase()
        }
      })
      if (updateError) throw updateError
      setMessage('Профиль сохранён')
    })
  }

  async function savePassword() {
    if (!isValidPassword(newPassword)) return setError('Пароль должен быть не короче 6 символов')
    await run(async () => {
      const { error: updateError } = await supabase.auth.updateUser({ password: newPassword })
      if (updateError) throw updateError
      setNewPassword('')
      setMessage('Пароль обновлён')
    })
  }

  async function signOut() {
    await run(async () => {
      await supabase.auth.signOut()
      setProfileDraft({ name: '', code: '', email: '' })
      setMessage('Вы вышли из аккаунта')
    })
  }

  async function react(item, type) {
    if (!session) {
      setError('Войдите, чтобы оставить реакцию')
      navigate('account')
      return
    }
    await run(async () => {
      await toggleReaction(supabase, session.user.id, checkInId(item.challengeId, item.code, item.date), type)
      await refreshReactions()
    })
  }

  const tab = TABS.some((item) => item.id === route) ? route : 'home'
  const repoUrl = standings?.repoUrl || 'https://github.com/RomanLyavin/online-challenges'
  const pagesUrl = standings?.pagesUrl || 'https://romanlyavin.github.io/online-challenges/'

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-inner">
          <button className="brand" onClick={() => navigate('home')} type="button">
            <span className="brand-mark">✓</span>
            <span>
              <strong>Онлайн челленджи</strong>
              <small>версия {APP_VERSION}</small>
            </span>
          </button>
          <div className="topbar-actions">
            <button
              className="icon-button"
              type="button"
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              aria-label="Переключить тему"
            >
              {theme === 'dark' ? '☀️' : '🌙'}
            </button>
            <a className="icon-button" href={repoUrl} target="_blank" rel="noreferrer" aria-label="GitHub">⌘</a>
          </div>
        </div>
      </header>

      <main className="content">
        {error && <div className="alert error" role="alert">{error}</div>}
        {message && <div className="alert info" role="status">{message}</div>}
        {dataError && <div className="alert error" role="alert">{dataError}</div>}

        {tab === 'home' && (
          <HomeView
            standings={standings}
            checkIns={checkIns}
            summary={summary}
            onReact={react}
            onAccount={() => navigate('account')}
          />
        )}
        {tab === 'catalog' && <CatalogView standings={standings} />}
        {tab === 'account' && (
          <AccountView
            session={session}
            authReady={authReady}
            mode={mode}
            setMode={(next) => { setMode(next); setError(''); setMessage('') }}
            form={form}
            updateForm={updateForm}
            busy={busy}
            profileDraft={profileDraft}
            setProfileDraft={setProfileDraft}
            emailDraft={emailDraft}
            setEmailDraft={setEmailDraft}
            newPassword={newPassword}
            setNewPassword={setNewPassword}
            pendingEmail={pendingEmail}
            pendingNewEmail={pendingNewEmail}
            handlers={{ register, verifySignup, login, requestReset, resetPassword, changeEmail, confirmEmailChange, saveProfile, savePassword, signOut }}
          />
        )}
        {tab === 'about' && <AboutView repoUrl={repoUrl} pagesUrl={pagesUrl} />}
      </main>

      <nav className="bottom-nav" aria-label="Разделы">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={tab === item.id ? 'nav-item active' : 'nav-item'}
            onClick={() => navigate(item.id)}
          >
            <span aria-hidden="true">{item.icon}</span>
            <small>{item.label}</small>
          </button>
        ))}
      </nav>
    </div>
  )
}

function HomeView({ standings, checkIns, summary, onReact, onAccount }) {
  if (!standings) return <Loading />
  const users = [...(standings.users || [])].sort((a, b) => number(b.points) - number(a.points)).slice(0, 10)
  const totals = standings.totals || {}
  const totalPoints = (standings.users || []).reduce((sum, user) => sum + number(user.points), 0)

  return (
    <>
      <section className="hero-card">
        <div>
          <span className="chip">Общий рейтинг</span>
          <h1>Каждый день — шаг к цели</h1>
          <p>Ставьте измеримые цели, отмечайте результат и поддерживайте других участников.</p>
          <button className="primary-button" type="button" onClick={onAccount}>
            Личный кабинет
          </button>
        </div>
        <div className="hero-stats">
          <Stat value={number(totals.users)} label="участников" />
          <Stat value={number(totals.challenges)} label="челленджей" />
          <Stat value={formatNumber(totalPoints)} label="баллов" />
        </div>
      </section>

      <section>
        <SectionTitle title="Рейтинг" subtitle="По баллам за все челленджи" />
        <div className="card list-card">
          {users.map((user, index) => (
            <div className="leader-row" key={user.code}>
              <span className="place">{index + 1}</span>
              <Avatar name={user.name} url={user.avatarUrl} />
              <div className="grow">
                <strong>{user.name}</strong>
                <small>{user.challenges || 0} челленджей · {user.gold || 0}🥇 {user.silver || 0}🥈 {user.bronze || 0}🥉</small>
              </div>
              <b>{formatNumber(user.points)}</b>
            </div>
          ))}
          {!users.length && <Empty text="Рейтинг пока пуст" />}
        </div>
      </section>

      <section>
        <SectionTitle title="Челленджи" subtitle="Открытые и приватные" />
        <div className="challenge-grid">
          {(standings.challenges || []).map((challenge) => (
            <ChallengeCard
              key={challenge.id}
              challenge={challenge}
              summary={summary}
              onReact={onReact}
            />
          ))}
          {!standings.challenges?.length && <Empty text="Челленджей пока нет" />}
        </div>
      </section>

      {!!checkIns.length && (
        <section>
          <SectionTitle title="Последние отметки" subtitle="Реакции доступны после входа" />
          <div className="card list-card">
            {checkIns.map((item, index) => (
              <RecentRow
                key={`${item.challengeId}-${item.code}-${item.date}-${index}`}
                item={item}
                summary={summary}
                onReact={onReact}
              />
            ))}
          </div>
        </section>
      )}
    </>
  )
}

function ChallengeCard({ challenge, summary, onReact }) {
  const isPrivate = challenge.visibility === 'PRIVATE'
  return (
    <article className="card challenge-card">
      <div className="challenge-head">
        <div>
          <span className={isPrivate ? 'chip chip-private' : 'chip'}>{isPrivate ? 'Приватный' : challenge.statusLabel || challenge.status}</span>
          <h3>{challenge.title}</h3>
          {challenge.description && <p>{challenge.description}</p>}
        </div>
        <b className="points">{formatNumber(challenge.participantCount ?? (challenge.participants || []).length)} уч.</b>
      </div>
      {!isPrivate && (
        <>
          <div className="goal-row">
            {(challenge.goals || []).map((goal, index) => (
              <span className="goal-chip" key={`${goal.name}-${index}`}>
                {goal.emoji} {goal.name} · {formatNumber(goal.target)} {goal.unit}
              </span>
            ))}
          </div>
          <div className="podium">
            {(challenge.podium || []).map((entry) => (
              <span key={entry.code}>{entry.medal} {entry.name} · {formatNumber(entry.points)}</span>
            ))}
          </div>
          <div className="recent-block">
            {(challenge.recent || []).slice(0, 3).map((item, index) => (
              <RecentRow
                key={`${item.code}-${item.date}-${index}`}
                item={{ ...item, challengeId: challenge.id }}
                summary={summary}
                onReact={onReact}
                compact
              />
            ))}
          </div>
        </>
      )}
      {isPrivate && <p className="muted">Видно только имя, очки и место участников.</p>}
    </article>
  )
}

function RecentRow({ item, summary, onReact, compact }) {
  const id = item.checkInId || checkInId(item.challengeId, item.code, item.date)
  const counts = summary.counts[id] || {}
  const mine = summary.mine[id]
  return (
    <div className={compact ? 'recent-row compact' : 'recent-row'}>
      <div className="grow">
        <strong>{item.name || item.code}</strong>
        <small>{item.date}{item.challengeTitle ? ` · ${item.challengeTitle}` : ''}</small>
        <div className="result-row">
          {(item.results || []).map((result, index) => (
            <span className={result.completed ? 'result done' : 'result'} key={`${result.goal}-${index}`}>
              {result.emoji} {result.goal}: {formatNumber(result.actual)}/{formatNumber(result.target)} {result.unit}
            </span>
          ))}
        </div>
      </div>
      <div className="reactions">
        {REACTIONS.map((reaction) => (
          <button
            key={reaction.key}
            type="button"
            title={reaction.label}
            className={mine === reaction.key ? 'reaction selected' : 'reaction'}
            onClick={() => onReact(item, reaction.key)}
          >
            {reaction.emoji}<small>{counts[reaction.key] || 0}</small>
          </button>
        ))}
      </div>
    </div>
  )
}

function CatalogView({ standings }) {
  if (!standings) return <Loading />
  const goals = new Map()
  for (const challenge of standings.challenges || []) {
    if (challenge.visibility === 'PRIVATE') continue
    for (const goal of challenge.goals || []) {
      if (!goals.has(goal.name)) goals.set(goal.name, goal)
    }
  }
  return (
    <section>
      <SectionTitle title="Каталог активностей" subtitle="Цели, которые используют участники" />
      <div className="catalog-grid">
        {[...goals.values()].map((goal, index) => (
          <div className="card catalog-item" key={`${goal.name}-${index}`}>
            <span className="catalog-emoji">{goal.emoji || '🎯'}</span>
            <div>
              <strong>{goal.name}</strong>
              <small>Цель: {formatNumber(goal.target)} {goal.unit}</small>
            </div>
          </div>
        ))}
      </div>
      {!goals.size && <Empty text="Активностей пока нет" />}
    </section>
  )
}

function AccountView(props) {
  const {
    session, authReady, mode, setMode, form, updateForm, busy,
    profileDraft, setProfileDraft, emailDraft, setEmailDraft,
    newPassword, setNewPassword, pendingEmail, pendingNewEmail, handlers
  } = props

  if (!authReady) return <Loading />
  if (!isSupabaseConfigured) {
    return (
      <section className="card account-card">
        <h2>Личный кабинет</h2>
        <p className="muted">На этом сайте не задан адрес Supabase. Публичный рейтинг доступен без входа.</p>
      </section>
    )
  }

  if (!session) {
    return (
      <AuthPanel
        mode={mode}
        setMode={setMode}
        form={form}
        updateForm={updateForm}
        busy={busy}
        pendingEmail={pendingEmail}
        handlers={handlers}
      />
    )
  }

  return (
    <section className="account-layout">
      <div className="card profile-card">
        <div className="profile-head">
          <Avatar name={profileDraft.name || session.user.email} large />
          <div className="grow">
            <h2>{profileDraft.name || 'Участник'}</h2>
            <p className="muted">{session.user.email}</p>
            {!!profileDraft.code && <span className="chip">Код: {profileDraft.code}</span>}
          </div>
        </div>
        <h3>Профиль</h3>
        <label>Имя
          <input value={profileDraft.name} onChange={(event) => setProfileDraft({ ...profileDraft, name: event.target.value })} />
        </label>
        <label>Код участника
          <input value={profileDraft.code} onChange={(event) => setProfileDraft({ ...profileDraft, code: event.target.value.toUpperCase() })} />
        </label>
        <button className="primary-button" type="button" disabled={busy} onClick={handlers.saveProfile}>Сохранить профиль</button>
        <hr />
        <h3>Безопасность</h3>
        <label>Новый пароль
          <input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
        </label>
        <button className="secondary-button" type="button" disabled={busy} onClick={handlers.savePassword}>Обновить пароль</button>
        <label>Новый email
          <input type="email" value={emailDraft} onChange={(event) => setEmailDraft(event.target.value)} />
        </label>
        <button className="secondary-button" type="button" disabled={busy} onClick={handlers.changeEmail}>Отправить код на новый email</button>
        <button className="text-button" type="button" onClick={handlers.signOut}>Выйти из аккаунта</button>
      </div>

      {(mode === 'confirm-email' || pendingNewEmail) && (
        <div className="card account-card">
          <h2>Подтверждение email</h2>
          <p>Введите код, отправленный на {pendingNewEmail || 'новый адрес'}.</p>
          <CodeField value={form.code} onChange={(value) => updateForm('code', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.confirmEmailChange}>Подтвердить код</button>
          <button className="text-button" type="button" onClick={() => setMode('account')}>Отменить</button>
        </div>
      )}
    </section>
  )
}

function AuthPanel({ mode, setMode, form, updateForm, busy, pendingEmail, handlers }) {
  const tabs = [
    { id: 'login', label: 'Вход' },
    { id: 'register', label: 'Регистрация' },
    { id: 'forgot', label: 'Забыл пароль' }
  ]
  const visibleTabs = ['verify', 'reset'].includes(mode) ? [] : tabs

  return (
    <section className="card account-card">
      <h2>{mode === 'register' ? 'Регистрация' : mode === 'forgot' ? 'Восстановление пароля' : mode === 'verify' ? 'Подтверждение email' : mode === 'reset' ? 'Новый пароль' : 'Вход'}</h2>
      {visibleTabs.length > 0 && (
        <div className="auth-tabs">
          {visibleTabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={mode === tab.id ? 'auth-tab active' : 'auth-tab'}
              onClick={() => setMode(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}

      {mode === 'login' && (
        <>
          <Field label="Email" type="email" value={form.email} onChange={(value) => updateForm('email', value)} />
          <Field label="Пароль" type="password" value={form.password} onChange={(value) => updateForm('password', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.login}>Войти</button>
        </>
      )}

      {mode === 'register' && (
        <>
          <p className="muted">Подтверждение регистрации — шестизначным кодом из письма. Код можно ввести на компьютере.</p>
          <Field label="Email" type="email" value={form.email} onChange={(value) => updateForm('email', value)} />
          <Field label="Пароль" type="password" value={form.password} onChange={(value) => updateForm('password', value)} />
          <Field label="Повторите пароль" type="password" value={form.confirm} onChange={(value) => updateForm('confirm', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.register}>Зарегистрироваться</button>
        </>
      )}

      {mode === 'verify' && (
        <>
          <p>Код отправлен на {pendingEmail}. Введите шестизначный код из письма.</p>
          <CodeField value={form.code} onChange={(value) => updateForm('code', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.verifySignup}>Подтвердить код</button>
          <button className="text-button" type="button" onClick={() => setMode('login')}>Вернуться ко входу</button>
        </>
      )}

      {mode === 'forgot' && (
        <>
          <p>Отправим код для сброса пароля на вашу почту.</p>
          <Field label="Email" type="email" value={form.email} onChange={(value) => updateForm('email', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.requestReset}>Отправить код</button>
          <button className="text-button" type="button" onClick={() => setMode('login')}>Вернуться ко входу</button>
        </>
      )}

      {mode === 'reset' && (
        <>
          <p>Код для сброса отправлен на {pendingEmail}. Введите код и новый пароль.</p>
          <CodeField value={form.code} onChange={(value) => updateForm('code', value)} />
          <Field label="Новый пароль" type="password" value={form.password} onChange={(value) => updateForm('password', value)} />
          <Field label="Повторите пароль" type="password" value={form.confirm} onChange={(value) => updateForm('confirm', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.resetPassword}>Сохранить пароль</button>
        </>
      )}
    </section>
  )
}

function AboutView({ repoUrl, pagesUrl }) {
  return (
    <section className="card about-card">
      <span className="chip">Онлайн челленджи</span>
      <h2>О программе</h2>
      <p>Приложение и сайт для челленджей по активности: шаги, пробежки, сон, отжимания, приседания, планка и другие цели.</p>
      <p>Участники каждый день вносят результат. Фото, видео или подтверждение другого участника даёт балл. В рейтинге видны очки, места и медали.</p>
      <h3>Приватность</h3>
      <p>В публичных данных находятся только имя, аватар, очки и хэш email. Приватные челленджи шифруются; на сайте по ним видны только имена, очки и места.</p>
      <h3>Ссылки</h3>
      <div className="link-list">
        <a href={pagesUrl} target="_blank" rel="noreferrer">Сайт</a>
        <a href={repoUrl} target="_blank" rel="noreferrer">Исходный код</a>
        <a href={`${repoUrl}/releases/latest`} target="_blank" rel="noreferrer">Скачать APK</a>
      </div>
      <p className="muted">Версия {APP_VERSION}. Веб-версия всегда соответствует последней публикации.</p>
    </section>
  )
}

function Field({ label, type = 'text', value, onChange }) {
  return (
    <label className="field">
      {label}
      <input type={type} value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  )
}

function CodeField({ value, onChange }) {
  return (
    <Field
      label="Код из письма"
      value={value}
      onChange={(next) => onChange(next.replace(/\D/g, '').slice(0, 6))}
    />
  )
}

function SectionTitle({ title, subtitle }) {
  return (
    <div className="section-title">
      <h2>{title}</h2>
      {subtitle && <small>{subtitle}</small>}
    </div>
  )
}

function Stat({ value, label }) {
  return (
    <div className="stat">
      <strong>{value}</strong>
      <small>{label}</small>
    </div>
  )
}

function Avatar({ name, url, large }) {
  const initial = String(name || '?').trim().charAt(0).toUpperCase()
  if (url) return <img className={large ? 'avatar large' : 'avatar'} src={url} alt="" />
  return <span className={large ? 'avatar large' : 'avatar'}>{initial}</span>
}

function Loading() {
  return <div className="card empty">Загрузка…</div>
}

function Empty({ text }) {
  return <div className="empty">{text}</div>
}

export default App
