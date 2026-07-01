export type TabKey = 'home' | 'compare' | 'mission' | 'records' | 'profile'

export type SpendingCategory = {
  label: string
  amount: string
  ratio: string
  icon: 'food' | 'transport' | 'cafe' | 'more'
}

export type FinanceSignal = {
  label: string
  count: string
  ratio?: string
  icon: 'stocks' | 'saving' | 'fund' | 'pension' | 'study'
}

export type CompareMetric = {
  label: string
  caption: string
  mine: string
  group: string
  mineRatio: number
  groupRatio: number
  icon: 'saving' | 'spend' | 'stocks' | 'debt'
}

export type FilterProfile = {
  name: string
  age: string
  job: string
  income: string
  score: string
  tags: string[]
  initials: string
}

export type MissionItem = {
  title: string
  description: string
  progress: string
  points: string
  icon: 'cafe' | 'cart' | 'piggy' | 'transport'
}

export type CalendarDay = {
  day: number
  amount?: string
  state?: 'success' | 'over' | 'none' | 'selected'
}

export const homeDemo = {
  greeting: 'jinn님, 좋은 아침이에요!',
  missionTitle: '내일 식비 10,000원 이하 사용하기',
  missionProgress: 78,
  budget: {
    total: '₩10,000',
    used: '₩7,800',
    left: '₩2,200',
  },
  spending: [
    { label: '식비', amount: '-₩6,200', ratio: '62%', icon: 'food' },
    { label: '교통비', amount: '-₩1,200', ratio: '12%', icon: 'transport' },
    { label: '카페/간식', amount: '-₩600', ratio: '6%', icon: 'cafe' },
    { label: '기타', amount: '-₩1,800', ratio: '18%', icon: 'more' },
  ] satisfies SpendingCategory[],
  assets: {
    total: '₩12,450,000',
    delta: '+₩320,000',
    growth: '+2.6%',
  },
  followers: [
    { label: '주식 투자', count: '18명', icon: 'stocks' },
    { label: '적금 가입', count: '32명', icon: 'saving' },
    { label: '펀드 투자', count: '9명', icon: 'fund' },
    { label: '연금 준비', count: '21명', icon: 'pension' },
  ] satisfies FinanceSignal[],
}

export const compareDemo = {
  mineScore: '72점',
  mineRank: '상위 62%',
  groupScore: '68점',
  groupRank: '상위 48%',
  metrics: [
    {
      label: '저축',
      caption: '월 평균 저축액',
      mine: '42만원',
      group: '35만원',
      mineRatio: 86,
      groupRatio: 72,
      icon: 'saving',
    },
    {
      label: '소비',
      caption: '월 평균 소비액',
      mine: '78만원',
      group: '76만원',
      mineRatio: 88,
      groupRatio: 84,
      icon: 'spend',
    },
    {
      label: '투자',
      caption: '투자자 비율',
      mine: '32%',
      group: '27%',
      mineRatio: 72,
      groupRatio: 62,
      icon: 'stocks',
    },
    {
      label: '부채',
      caption: '대출 보유 비율',
      mine: '18%',
      group: '21%',
      mineRatio: 52,
      groupRatio: 60,
      icon: 'debt',
    },
  ] satisfies CompareMetric[],
}

export const filterDemo = {
  filters: [
    ['나이', '20대'],
    ['연 소득', '3,000만원 ~ 4,000만원'],
    ['업종', 'IT/개발'],
    ['금융 성향', '안정 추구형'],
    ['생활권', '서울 강남권'],
    ['직장 형태', '전체'],
    ['자산 규모', '전체'],
  ],
  profiles: [
    {
      name: '코딩하는 제이',
      age: '27세',
      job: 'IT 개발자',
      income: '연소득 3,600만원',
      score: '74점',
      tags: ['저테크초보', '월급방'],
      initials: 'J',
    },
    {
      name: '미니멀 라이프',
      age: '29세',
      job: '디자이너',
      income: '연소득 3,200만원',
      score: '70점',
      tags: ['소비줄거움', '여행러버'],
      initials: 'M',
    },
    {
      name: '퇴근 후 투자',
      age: '26세',
      job: '마케터',
      income: '연소득 3,800만원',
      score: '76점',
      tags: ['소액투자', '적금러'],
      initials: 'T',
    },
  ] satisfies FilterProfile[],
}

export const missionDemo = {
  heroTitle: '내일 식비 10,000원 이하 사용하기',
  progress: 78,
  dailyBudget: '₩10,000',
  left: '2,200원 / 10,000원',
  active: [
    {
      title: '카페 지출 줄이기',
      description: '이번 주 카페 2회 이하 이용',
      progress: '1 / 2',
      points: '+100P',
      icon: 'cafe',
    },
    {
      title: '충동구매 줄이기',
      description: '이번 주 비필수 지출 0원 도전',
      progress: '0 / 1',
      points: '+150P',
      icon: 'cart',
    },
    {
      title: '저축하기 습관 만들기',
      description: '3일 연속 저축 성공',
      progress: '2 / 3',
      points: '+200P',
      icon: 'piggy',
    },
  ] satisfies MissionItem[],
  completed: {
    title: '대중교통 이용하기',
    description: '교통비 3,000원 이하 사용',
    date: '24.12.08 완료',
    points: '+100P',
  },
  points: {
    total: '2,450',
    weekly: '+450P',
  },
}

export const recordDemo = {
  month: '2026년 6월',
  days: [
    { day: 1, amount: '8,200', state: 'success' },
    { day: 2, amount: '9,500', state: 'success' },
    { day: 3, amount: '7,300', state: 'none' },
    { day: 4, amount: '11,200', state: 'over' },
    { day: 5, amount: '9,800', state: 'success' },
    { day: 6, amount: '10,000', state: 'over' },
    { day: 7, amount: '6,500', state: 'success' },
    { day: 8, amount: '8,100', state: 'none' },
    { day: 9, amount: '7,800', state: 'none' },
    { day: 10, amount: '9,200', state: 'none' },
    { day: 11, amount: '10,500', state: 'none' },
    { day: 12, amount: '7,800', state: 'selected' },
    { day: 13, amount: '9,600', state: 'none' },
    { day: 14, amount: '7,200', state: 'over' },
    { day: 15, state: 'over' },
    { day: 16 },
    { day: 17 },
    { day: 18 },
    { day: 19 },
    { day: 20 },
    { day: 21 },
    { day: 22 },
    { day: 23 },
    { day: 24 },
    { day: 25, state: 'over' },
    { day: 26 },
    { day: 27 },
    { day: 28 },
    { day: 29 },
    { day: 30 },
  ] satisfies CalendarDay[],
}

export const profileDemo = {
  followers: '128명',
  summary: [
    { label: '주식 투자', count: '43%', ratio: '55명', icon: 'stocks' },
    { label: '적금 가입', count: '78%', ratio: '100명', icon: 'saving' },
    { label: '펀드 투자', count: '28%', ratio: '36명', icon: 'fund' },
    { label: '연금 준비', count: '35%', ratio: '45명', icon: 'pension' },
  ] satisfies FinanceSignal[],
  distribution: [
    { label: '주식 투자', count: '43%', ratio: '55명', icon: 'stocks' },
    { label: '적금 가입', count: '78%', ratio: '100명', icon: 'saving' },
    { label: '펀드 투자', count: '28%', ratio: '36명', icon: 'fund' },
    { label: '연금 준비', count: '35%', ratio: '45명', icon: 'pension' },
    { label: '재테크 공부', count: '62%', ratio: '79명', icon: 'study' },
  ] satisfies FinanceSignal[],
}
