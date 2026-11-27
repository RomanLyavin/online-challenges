export const REACTIONS = [
  { key: 'like', emoji: '👍', label: 'Класс' },
  { key: 'fire', emoji: '🔥', label: 'Огонь' },
  { key: 'dislike', emoji: '👎', label: 'Не получилось' },
  { key: 'heart', emoji: '❤️', label: 'Поддержка' }
]

export function checkInId(challengeId, code, date) {
  return `${challengeId}|${code}|${date}`
}

export function reactionSummary(rows, userId) {
  const counts = {}
  const mine = {}
  for (const row of rows || []) {
    const id = row.checkin_id
    const type = row.reaction
    if (!id || !REACTIONS.some((reaction) => reaction.key === type)) continue
    if (!counts[id]) counts[id] = {}
    counts[id][type] = (counts[id][type] || 0) + 1
    if (userId && row.user_id === userId) mine[id] = type
  }
  return { counts, mine }
}

export async function loadReactions(client, ids) {
  if (!client || !ids.length) return { counts: {}, mine: {} }
  const { data, error } = await client
    .from('checkin_reactions')
    .select('checkin_id,user_id,reaction')
    .in('checkin_id', ids)
  if (error) throw error
  const { data: authData } = await client.auth.getUser()
  return reactionSummary(data || [], authData?.user?.id)
}

export async function toggleReaction(client, userId, id, type) {
  if (!client) throw new Error('Реакции недоступны')
  if (!userId) throw new Error('Войдите, чтобы оставить реакцию')

  const { data: existing } = await client
    .from('checkin_reactions')
    .select('reaction')
    .eq('checkin_id', id)
    .eq('user_id', userId)
    .maybeSingle()

  if (existing?.reaction === type) {
    const { error } = await client
      .from('checkin_reactions')
      .delete()
      .eq('checkin_id', id)
      .eq('user_id', userId)
    if (error) throw error
    return 'removed'
  }

  const { error } = await client
    .from('checkin_reactions')
    .upsert(
      { checkin_id: id, user_id: userId, reaction: type, updated_at: new Date().toISOString() },
      { onConflict: 'checkin_id,user_id' }
    )
  if (error) throw error
  return 'saved'
}
