import { describe, expect, it } from 'vitest'
import { checkInId, reactionSummary } from './reactions.js'

describe('reaction helpers', () => {
  const me = '11111111-1111-1111-1111-111111111111'
  const other = '22222222-2222-2222-2222-222222222222'
  const id = checkInId('challenge-1', 'USER-1', '2026-11-27')

  it('uses the same deterministic key as Android', () => {
    expect(id).toBe('challenge-1|USER-1|2026-11-27')
  })

  it('groups counts and marks the current user reaction', () => {
    const summary = reactionSummary([
      { checkin_id: id, user_id: me, reaction: 'fire' },
      { checkin_id: id, user_id: other, reaction: 'fire' },
      { checkin_id: id, user_id: other, reaction: 'heart' },
      { checkin_id: id, user_id: other, reaction: 'unknown' }
    ], me)

    expect(summary.counts[id]).toEqual({ fire: 2, heart: 1 })
    expect(summary.mine[id]).toBe('fire')
  })

  it('handles empty and malformed rows', () => {
    expect(reactionSummary([], me)).toEqual({ counts: {}, mine: {} })
    expect(reactionSummary([{ checkin_id: '', reaction: 'like' }], me)).toEqual({ counts: {}, mine: {} })
  })
})
