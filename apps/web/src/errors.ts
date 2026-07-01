import { ApiError } from './api'

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'INVALID_CREDENTIALS') {
      return '이메일 또는 비밀번호를 다시 확인해주세요.'
    }
    if (error.code === 'UNAUTHORIZED') {
      return '로그인이 필요해요. 다시 로그인해주세요.'
    }
    if (error.code === 'MISSION_NOT_FOUND') {
      return '미션을 찾을 수 없어요. 홈에서 다시 선택해주세요.'
    }
    if (error.code === 'PORTFOLIO_NOT_AVAILABLE') {
      return '해당 사례 카드는 더 이상 공개되지 않아요.'
    }
    if (error.code === 'VALIDATION_ERROR') {
      return error.fieldErrors?.[0]?.message ?? '입력한 내용을 다시 확인해주세요.'
    }
    if (error.status >= 500) {
      return '잠시 후 다시 시도해주세요.'
    }
    return '요청을 처리하지 못했어요.'
  }
  if (error instanceof Error) {
    return '네트워크 상태를 확인하고 다시 시도해주세요.'
  }
  return '화면을 불러오지 못했어요.'
}

export function isUnauthorized(error: unknown) {
  return error instanceof ApiError && error.code === 'UNAUTHORIZED'
}
