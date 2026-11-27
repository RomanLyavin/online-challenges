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

export function siteUrl() {
  return new URL('.', window.location.href).toString()
}

export function authMessage(error) {
  if (!error) return 'Неизвестная ошибка'
  const text = error.message || String(error)
  if (/invalid login credentials/i.test(text)) return 'Неверный email или пароль'
  if (/email not confirmed/i.test(text)) return 'Подтвердите email кодом из письма'
  if (/user already registered/i.test(text)) {
    return 'Аккаунт с такой почтой уже есть — войдите или восстановите пароль'
  }
  if (/rate limit|too many requests/i.test(text)) {
    return 'Слишком много попыток. Подождите минуту и попробуйте снова'
  }
  if (/failed to fetch|networkerror/i.test(text)) return 'Нет связи с сервером. Проверьте интернет'
  return text
}
