export function numberFromData(data: Record<string, unknown> | null | undefined, key: string): number | null {
  const value = data?.[key]
  return typeof value === 'number' ? value : null
}

export function arrayFromData(data: Record<string, unknown> | null | undefined, key: string): number[] {
  const value = data?.[key]
  return Array.isArray(value) ? value.filter((item): item is number => typeof item === 'number') : []
}
