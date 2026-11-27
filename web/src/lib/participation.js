export async function loadParticipation(client) {
  if (!client) return []
  const { data, error } = await client
    .from('challenge_participants')
    .select('challenge_id,user_id,user_name')
  if (error) throw error
  return data || []
}

export async function joinChallenge(client, userId, userName, challengeId) {
  if (!client) throw new Error('Участие недоступно')
  if (!userId) throw new Error('Войдите, чтобы присоединиться к челленджу')
  const { error } = await client
    .from('challenge_participants')
    .upsert(
      {
        challenge_id: challengeId,
        user_id: userId,
        user_name: String(userName || '').slice(0, 120),
        joined_at: new Date().toISOString()
      },
      { onConflict: 'challenge_id,user_id' }
    )
  if (error) throw error
  return 'joined'
}

export async function leaveChallenge(client, userId, challengeId) {
  if (!client) throw new Error('Участие недоступно')
  if (!userId) throw new Error('Войдите, чтобы выйти из челленджа')
  const { error } = await client
    .from('challenge_participants')
    .delete()
    .eq('challenge_id', challengeId)
    .eq('user_id', userId)
  if (error) throw error
  return 'left'
}

export function participantName(user) {
  return String(user?.user_metadata?.name || user?.user_metadata?.full_name || '').trim()
}
