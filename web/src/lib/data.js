export async function loadStandings() {
  const url = `${import.meta.env.BASE_URL}data/standings.json`
  const response = await fetch(url, { cache: 'no-store' })
  if (!response.ok) throw new Error(`Не удалось загрузить данные (${response.status})`)
  const data = await response.json()
  if (!data || !Array.isArray(data.challenges)) throw new Error('Некорректный файл результатов')
  return data
}

export function recentCheckIns(standings) {
  return (standings?.challenges || [])
    .filter((challenge) => challenge.visibility !== 'PRIVATE')
    .flatMap((challenge) =>
      (challenge.recent || []).map((item) => ({ ...item, challengeId: challenge.id, challengeTitle: challenge.title }))
    )
    .sort((a, b) => String(b.date).localeCompare(String(a.date)))
    .slice(0, 30)
}
