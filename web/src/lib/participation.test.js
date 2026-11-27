import { describe, expect, it, vi } from 'vitest'
import { joinChallenge, leaveChallenge, loadParticipation, participantName } from './participation.js'

function fakeClient(result = { data: [], error: null }) {
  const builder = {
    select: vi.fn(() => Promise.resolve(result)),
    upsert: vi.fn(() => Promise.resolve(result)),
    delete: vi.fn(() => builder),
    eq: vi.fn(() => builder),
    then: (resolve, reject) => Promise.resolve(result).then(resolve, reject)
  }
  return { client: { from: vi.fn(() => builder) }, builder }
}

describe('participation helpers', () => {
  it('returns no rows without a configured client', async () => {
    expect(await loadParticipation(null)).toEqual([])
  })

  it('loads participation rows and surfaces errors', async () => {
    const rows = [{ challenge_id: 'ch-1', user_id: 'u-1', user_name: 'Ромка' }]
    const ok = fakeClient({ data: rows, error: null })
    expect(await loadParticipation(ok.client)).toEqual(rows)

    const failed = fakeClient({ data: null, error: { message: 'no table' } })
    await expect(loadParticipation(failed.client)).rejects.toMatchObject({ message: 'no table' })
  })

  it('joins only for a signed-in user', async () => {
    const { client, builder } = fakeClient()
    await expect(joinChallenge(client, '', 'Ромка', 'ch-1')).rejects.toThrow('Войдите')
    expect(await joinChallenge(client, 'u-1', 'Ромка', 'ch-1')).toBe('joined')
    expect(builder.upsert).toHaveBeenCalledWith(
      expect.objectContaining({ challenge_id: 'ch-1', user_id: 'u-1', user_name: 'Ромка' }),
      { onConflict: 'challenge_id,user_id' }
    )
  })

  it('leaves a challenge with both filters', async () => {
    const { client, builder } = fakeClient()
    expect(await leaveChallenge(client, 'u-1', 'ch-1')).toBe('left')
    expect(builder.delete).toHaveBeenCalled()
    expect(builder.eq).toHaveBeenNthCalledWith(1, 'challenge_id', 'ch-1')
    expect(builder.eq).toHaveBeenNthCalledWith(2, 'user_id', 'u-1')
  })

  it('reads the display name from user metadata', () => {
    expect(participantName({ user_metadata: { name: ' Вика ' } })).toBe('Вика')
    expect(participantName({ user_metadata: { full_name: 'Ромка' } })).toBe('Ромка')
    expect(participantName({})).toBe('')
  })
})
