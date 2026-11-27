export function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase()
}

export function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizeEmail(value))
}

export function isValidPassword(value) {
  return String(value || '').length >= 6
}

export function isValidCode(value) {
  return /^\d{6}$/.test(String(value || '').trim())
}

export function profileFromUser(user) {
  const metadata = user?.user_metadata || {}
  return {
    name: metadata.display_name || metadata.name || String(user?.email || '').split('@')[0],
    code: metadata.participant_code || metadata.code || '',
    email: user?.email || ''
  }
}
