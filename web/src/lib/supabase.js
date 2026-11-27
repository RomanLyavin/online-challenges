import { createClient } from '@supabase/supabase-js'

const url = (import.meta.env.VITE_SUPABASE_URL || '').trim()
const key = (
  import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY ||
  import.meta.env.VITE_SUPABASE_ANON_KEY ||
  ''
).trim()

export const isSupabaseConfigured = Boolean(url && key)

export const supabase = isSupabaseConfigured
  ? createClient(url, key, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true
      }
    })
  : null

export async function webSignUp(email, password, name) {
  if (!isSupabaseConfigured) throw new Error('Личный кабинет не настроен на этом сайте')
  const response = await fetch(`${url}/functions/v1/web-signup`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: key,
    },
    body: JSON.stringify({ email, password, name }),
  })

  let data = null
  try {
    data = await response.json()
  } catch {
    data = null
  }

  if (!response.ok) {
    const message = data?.error || data?.message
    if (response.status === 404) {
      throw new Error('Регистрация на сайте пока не настроена. Выполните деплой функции web-signup в Supabase.')
    }
    throw new Error(message || `Не удалось создать аккаунт (${response.status})`)
  }
  return data
}

export function siteUrl() {
  return new URL('.', window.location.href).toString()
}

export function authMessage(error) {
  if (!error) return 'Неизвестная ошибка'
  const text = error.message || String(error)
  if (/invalid login credentials/i.test(text)) return 'Неверный email или пароль'
  if (/email not confirmed/i.test(text)) return 'Email не подтверждён. На сайте подтверждение почты не требуется; проверьте настройки Supabase Auth.'
  if (/user already registered/i.test(text)) {
    return 'Аккаунт с такой почтой уже есть — войдите или восстановите пароль'
  }
  if (/rate limit|too many requests/i.test(text)) {
    return 'Слишком много попыток. Подождите минуту и попробуйте снова'
  }
  if (/failed to fetch|networkerror/i.test(text)) return 'Нет связи с сервером. Проверьте интернет'
  if (/schema cache|relation .* does not exist/i.test(text)) {
    return 'Функция пока недоступна: проверьте настройку Supabase'
  }
  return text
}
