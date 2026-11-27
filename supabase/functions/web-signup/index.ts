import { createClient } from 'npm:@supabase/supabase-js@2'
import { corsHeaders } from 'npm:@supabase/supabase-js@2/cors'

const ALLOWED_ORIGINS = new Set([
  'https://romanlyavin.github.io',
  'http://localhost:5173',
  'http://127.0.0.1:5173',
  'http://localhost:4173',
  'http://127.0.0.1:4173',
])

function json(body: unknown, status = 200, origin?: string | null) {
  const headers = new Headers(corsHeaders)
  if (origin) headers.set('Access-Control-Allow-Origin', origin)
  headers.set('Vary', 'Origin')
  return Response.json(body, { status, headers })
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

Deno.serve(async (req) => {
  const origin = req.headers.get('Origin')
  const allowedOrigin = origin && ALLOWED_ORIGINS.has(origin) ? origin : null

  if (req.method === 'OPTIONS') {
    if (origin && !allowedOrigin) return new Response('Origin not allowed', { status: 403 })
    return new Response('ok', {
      headers: {
        ...corsHeaders,
        ...(allowedOrigin ? { 'Access-Control-Allow-Origin': allowedOrigin } : {}),
        Vary: 'Origin',
      },
    })
  }

  if (origin && !allowedOrigin) return json({ error: 'Origin not allowed' }, 403)
  if (req.method !== 'POST') return json({ error: 'Method not allowed' }, 405, allowedOrigin)

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ error: 'Server is not configured' }, 500, allowedOrigin)
  }

  let payload: { email?: string; password?: string; name?: string }
  try {
    payload = await req.json()
  } catch {
    return json({ error: 'Invalid JSON' }, 400, allowedOrigin)
  }

  const email = String(payload.email || '').trim().toLowerCase()
  const password = String(payload.password || '')
  const name = String(payload.name || '').trim().slice(0, 120)

  if (!isValidEmail(email)) return json({ error: 'Введите корректный email' }, 400, allowedOrigin)
  if (password.length < 6) return json({ error: 'Пароль должен быть не короче 6 символов' }, 400, allowedOrigin)

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

  const { data, error } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: name ? { display_name: name } : undefined,
  })

  if (error) {
    const msg = String(error.message || '').toLowerCase()
    const code = String((error as { code?: string }).code || '').toLowerCase()
    if (error.status === 422 || code === 'email_exists' || code === 'user_already_exists' || msg.includes('already')) {
      return json({ error: 'Аккаунт с такой почтой уже зарегистрирован' }, 409, allowedOrigin)
    }
    return json({ error: error.message || 'Не удалось создать аккаунт' }, error.status || 400, allowedOrigin)
  }

  return json({ ok: true, userId: data.user?.id ?? null }, 200, allowedOrigin)
})
