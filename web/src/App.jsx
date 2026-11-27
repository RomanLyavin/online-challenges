import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { loadStandings, recentCheckIns } from './lib/data.js'
import { isSupabaseConfigured, supabase, authMessage, webSignUp } from './lib/supabase.js'
import {
  isValidEmail,
  isValidPassword,
  normalizeEmail,
  profileFromUser
} from './lib/auth.js'
import { REACTIONS, checkInId, loadReactions, toggleReaction } from './lib/reactions.js'
import { joinChallenge, leaveChallenge, loadParticipation, participantName } from './lib/participation.js'
import donateImage from './assets/donate.jpg'

const APP_VERSION = '1.6.5'
const SUPPORT_URL = 'https://t.tb.ru/pm_short/AOdA22MAdsS'
const TABS = [
  { id: 'home', label: 'Главная', icon: '🏠' },
  { id: 'catalog', label: 'Каталог', icon: '🎯' },
  { id: 'account', label: 'Профиль', icon: '👤' },
  { id: 'support', label: 'Поддержка', icon: '💛' },
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
  const [form, setForm] = useState({ email: '', password: '', confirm: '' })
  const [profileDraft, setProfileDraft] = useState({ name: '', code: '', email: '' })
  const [emailDraft, setEmailDraft] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [summary, setSummary] = useState({ counts: {}, mine: {} })
  const [participation, setParticipation] = useState([])
  const [participationTableMissing, setParticipationTableMissing] = useState(false)
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
      // Recovery links are not supported in web-only mode without email confirmation setup.
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
      const text = err?.message || ''
      if (!/schema cache|relation .* does not exist/i.test(text)) setMessage(authMessage(err))
    }
  }, [ids.join('|')])

  const refreshParticipation = useCallback(async () => {
    if (!supabase) {
      setParticipation([])
      return
    }
    try {
      setParticipation(await loadParticipation(supabase))
      setParticipationTableMissing(false)
    } catch (err) {
      const text = err?.message || ''
      if (/schema cache|challenge_participants|relation .* does not exist/i.test(text)) {
        setParticipationTableMissing(true)
      } else {
        setMessage(authMessage(err))
      }
    }
  }, [])

  useEffect(() => {
    refreshReactions()
  }, [refreshReactions, session?.user?.id])

  useEffect(() => {
    refreshParticipation()
  }, [refreshParticipation, session?.user?.id])

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
      await webSignUp(email, form.password, profileDraft.name || email.split('@')[0])
      const { error: signInError } = await supabase.auth.signInWithPassword({ email, password: form.password })
      if (signInError) throw signInError
      setForm({ email, password: '', confirm: '' })
      setMessage('Аккаунт создан. Вы вошли в личный кабинет.')
      navigate('account')
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

  async function changeEmail() {
    setError('Смена email на сайте недоступна, пока в Supabase включено подтверждение email. Измените email в Android-приложении.')
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

  async function react(item, type, challenge) {
    if (!session) {
      setError('Войдите, чтобы оставить реакцию')
      navigate('account')
      return
    }
    const profile = profileFromUser(session.user)
    const ownCode = profile.code
    if (ownCode && item.code === ownCode) {
      setError('Нельзя ставить реакцию на свою отметку')
      return
    }
    if (challenge?.status === 'FINISHED') {
      setError('В завершённом челлендже реакции недоступны')
      return
    }
    const challengeInfo = challenge || (standings?.challenges || []).find((c) => c.id === item.challengeId)
    const joinedFromWeb = (participation || []).some(
      (row) => row.challenge_id === item.challengeId && row.user_id === session.user.id
    )
    const joinedFromStandings = Boolean(
      profile.code && (challengeInfo?.participants || []).some((p) => p.code === profile.code)
    )
    if (!joinedFromWeb && !joinedFromStandings) {
      setError('Реакции могут ставить только участники этого челленджа')
      return
    }
    await run(async () => {
      await toggleReaction(supabase, session.user.id, checkInId(item.challengeId, item.code, item.date), type)
      await refreshReactions()
    })
  }

  async function join(challengeId) {
    if (!session) {
      setError('Войдите, чтобы присоединиться к челленджу')
      navigate('account')
      return
    }
    await run(async () => {
      const name = participantName(session.user) || normalizeEmail(session.user.email || '')
      await joinChallenge(supabase, session.user.id, name, challengeId)
      await refreshParticipation()
      setMessage('Вы присоединились к челленджу')
    })
  }

  async function leave(challengeId) {
    if (!session) {
      setError('Войдите, чтобы выйти из челленджа')
      navigate('account')
      return
    }
    await run(async () => {
      await leaveChallenge(supabase, session.user.id, challengeId)
      await refreshParticipation()
      setMessage('Вы вышли из челленджа')
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
            participation={participation}
            participationTableMissing={participationTableMissing}
            sessionUser={session?.user}
            onReact={react}
            onJoin={join}
            onLeave={leave}
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
            handlers={{ register, login, changeEmail, saveProfile, savePassword, signOut }}
          />
        )}
        {tab === 'support' && <SupportView />}
        {tab === 'about' && <AboutView repoUrl={repoUrl} pagesUrl={pagesUrl} onSupport={() => navigate('support')} />}
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

function HomeView({ standings, checkIns, summary, participation, participationTableMissing, sessionUser, onReact, onJoin, onLeave, onAccount }) {
  const [challengeFilter, setChallengeFilter] = useState('all')
  if (!standings) return <Loading />
  const users = [...(standings.users || [])].sort((a, b) => number(b.points) - number(a.points)).slice(0, 10)
  const totals = standings.totals || {}
  const totalPoints = (standings.users || []).reduce((sum, user) => sum + number(user.points), 0)
  const challengeFilters = [
    { id: 'all', label: 'Все' },
    { id: 'active', label: 'Идут сейчас' },
    { id: 'finished', label: 'Завершены' }
  ]
  const challenges = (standings.challenges || []).filter((challenge) => {
    if (challengeFilter === 'active') return challenge.status === 'ACTIVE'
    if (challengeFilter === 'finished') return challenge.status === 'FINISHED'
    return true
  })

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
        <div className="filter-row" role="group" aria-label="Отбор челленджей">
          {challengeFilters.map((filter) => (
            <button
              key={filter.id}
              type="button"
              className={challengeFilter === filter.id ? 'filter-button active' : 'filter-button'}
              onClick={() => setChallengeFilter(filter.id)}
            >
              {filter.label}
            </button>
          ))}
        </div>
        <div className="challenge-grid">
          {challenges.map((challenge) => (
            <ChallengeCard
              key={challenge.id}
              challenge={challenge}
              summary={summary}
              participation={participation}
              participationTableMissing={participationTableMissing}
              sessionUser={sessionUser}
              onReact={onReact}
              onJoin={onJoin}
              onLeave={onLeave}
            />
          ))}
          {!challenges.length && <Empty text="Челленджей по этому отбору нет" />}
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
                participation={participation}
                participationTableMissing={participationTableMissing}
                sessionUser={sessionUser}
                challenge={(standings.challenges || []).find((c) => c.id === item.challengeId)}
                onReact={onReact}
              />
            ))}
          </div>
        </section>
      )}
    </>
  )
}

function ChallengeCard({ challenge, summary, participation, participationTableMissing, sessionUser, onReact, onJoin, onLeave }) {
  const isPrivate = challenge.visibility === 'PRIVATE'
  const joinedWeb = (participation || []).filter((item) => item.challenge_id === challenge.id)
  const isJoined = Boolean(sessionUser && joinedWeb.some((item) => item.user_id === sessionUser.id))
  const participantCount = (challenge.participantCount ?? (challenge.participants || []).length) + joinedWeb.length
  return (
    <article className="card challenge-card">
      <div className="challenge-head">
        <div>
          <span className={isPrivate ? 'chip chip-private' : 'chip'}>{isPrivate ? 'Приватный' : challenge.statusLabel || challenge.status}</span>
          <h3>{challenge.title}</h3>
          {challenge.description && <p>{challenge.description}</p>}
        </div>
        <b className="points">{formatNumber(participantCount)} уч.</b>
      </div>
      <div className="join-row">
        {participationTableMissing ? (
          <small className="muted">Вступление с сайта появится после настройки Supabase.</small>
        ) : isJoined ? (
          <>
            <span className="chip">Вы участвуете</span>
            <button className="text-button" type="button" onClick={() => onLeave(challenge.id)}>Выйти</button>
          </>
        ) : (
          <button className="secondary-button" type="button" onClick={() => onJoin(challenge.id)}>
            {sessionUser ? 'Присоединиться' : 'Войти, чтобы присоединиться'}
          </button>
        )}
        {!participationTableMissing && !!joinedWeb.length && (
          <small className="muted">На сайте: {joinedWeb.map((item) => item.user_name || 'Участник').join(', ')}</small>
        )}
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
                participation={participation}
                participationTableMissing={participationTableMissing}
                sessionUser={sessionUser}
                challenge={challenge}
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

function RecentRow({ item, summary, participation, participationTableMissing, sessionUser, challenge, onReact, compact }) {
  const id = item.checkInId || checkInId(item.challengeId, item.code, item.date)
  const counts = summary.counts[id] || {}
  const mine = summary.mine[id]
  const ownCode = profileFromUser(sessionUser).code
  const joinedFromWeb = Boolean(sessionUser && (participation || []).some(
    (row) => row.challenge_id === item.challengeId && row.user_id === sessionUser.id
  ))
  const joinedFromStandings = Boolean(
    ownCode && (challenge?.participants || []).some((p) => p.code === ownCode)
  )
  const joined = joinedFromWeb || joinedFromStandings
  const isOwn = Boolean(ownCode && item.code === ownCode)
  const canReact = !participationTableMissing && joined && !isOwn && challenge?.status !== 'FINISHED'
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
            disabled={!canReact}
            className={mine === reaction.key ? 'reaction selected' : 'reaction'}
            onClick={() => onReact(item, reaction.key, challenge)}
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
    newPassword, setNewPassword, handlers
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
        <div className="card muted" style={{ padding: 12, marginBottom: 12 }}>
          Регистрация на сайте создаёт аккаунт без письма подтверждения через защищённую функцию Supabase. Сброс пароля и смена email на сайте недоступны, пока подтверждение email в Supabase включено для Android.
        </div>
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
          <input type="email" value={emailDraft} disabled onChange={(event) => setEmailDraft(event.target.value)} />
        </label>
        <button className="secondary-button" type="button" disabled onClick={handlers.changeEmail}>Изменить email в приложении</button>
        <small className="muted">Смена email на сайте отключена, чтобы не ломать подтверждение почты в Android.</small>
        <button className="text-button" type="button" onClick={handlers.signOut}>Выйти из аккаунта</button>
      </div>
    </section>
  )
}

function AuthPanel({ mode, setMode, form, updateForm, busy, handlers }) {
  const tabs = [
    { id: 'login', label: 'Вход' },
    { id: 'register', label: 'Регистрация' }
  ]

  return (
    <section className="card account-card">
      <h2>{mode === 'register' ? 'Регистрация' : 'Вход'}</h2>
      <div className="auth-tabs">
        {tabs.map((tab) => (
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
      <div className="card muted" style={{ padding: 12, marginBottom: 12 }}>
        Регистрация на сайте создаёт аккаунт без письма подтверждения через защищённую функцию Supabase. Сброс пароля на сайте недоступен.
      </div>

      {mode === 'login' && (
        <>
          <Field label="Email" type="email" value={form.email} onChange={(value) => updateForm('email', value)} />
          <Field label="Пароль" type="password" value={form.password} onChange={(value) => updateForm('password', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.login}>Войти</button>
        </>
      )}

      {mode === 'register' && (
        <>
          <Field label="Email" type="email" value={form.email} onChange={(value) => updateForm('email', value)} />
          <Field label="Пароль" type="password" value={form.password} onChange={(value) => updateForm('password', value)} />
          <Field label="Повторите пароль" type="password" value={form.confirm} onChange={(value) => updateForm('confirm', value)} />
          <button className="primary-button" type="button" disabled={busy} onClick={handlers.register}>Зарегистрироваться</button>
        </>
      )}
    </section>
  )
}

function SupportView() {
  return (
    <section className="card about-card support-card">
      <span className="chip">Онлайн челленджи</span>
      <h2>Поддержать проект</h2>
      <p>Спасибо за поддержку «Онлайн челленджей»!</p>
      <a className="support-image" href={SUPPORT_URL} target="_blank" rel="noreferrer">
        <img src={donateImage} alt="Картинка для поддержки проекта" />
      </a>
      <p className="muted">Нажмите на картинку, чтобы перейти к оплате.</p>
      <a className="primary-button support-button" href={SUPPORT_URL} target="_blank" rel="noreferrer">
        Перейти к оплате
      </a>
    </section>
  )
}

function AboutView({ repoUrl, pagesUrl, onSupport }) {
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
        <button type="button" onClick={onSupport}>Поддержать проект</button>
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
