# FinMate Design System

## Atmosphere
FinMate should feel like a real Korean mobile finance app with a social layer: white surfaces, precise data hierarchy, deep teal trust cues, and evidence-led coaching. The app should read closer to a trusted account book and verified peer feed than a playful demo. Teal/green is the product signal. Violet is only a restrained coach/comparison accent when it clarifies AI or peer insight.

## Color
- `#F5F6FA` `--color-page` app canvas and desktop background.
- `#FFFFFF` `--color-app` phone app surface.
- `#FFFFFF` `--color-card` primary card surface.
- `#101114` `--color-ink` primary text, near-black.
- `#3F4050` `--color-ink-soft` secondary strong text.
- `#696C7D` `--color-muted` helper text.
- `#8F92A3` `--color-muted-soft` quiet labels.
- `#E7E8EF` `--color-border` default border.
- `#D9DBE7` `--color-border-strong` selected border.
- `#007A68` `--color-primary` FinMate teal CTA and selected state.
- `#005D51` `--color-primary-strong` pressed teal.
- `#E7F6F2` `--color-primary-soft` subtle teal surface.
- `#CBECE4` `--color-primary-border` teal border.
- `#149E61` `--color-success` budget/progress success.
- `#E8F7F0` `--color-success-soft` success surface.
- `#026B3F` `--color-success-strong` success text.
- `#6D5DF2` `--color-coach` AI coach and peer comparison accent.
- `#F0EEFF` `--color-coach-soft` AI coach subtle surface.
- `#DDD8FF` `--color-coach-border` AI coach border.
- `#D97706` `--color-warning` warning state.
- `#FFF5E7` `--color-warning-soft` warning surface.
- `#B42318` `--color-danger` destructive state.
- `#FFF0F0` `--color-danger-soft` destructive surface.
- `#EEF0F6` `--color-track` progress track.
- `#BFC3D4` `--color-track-strong` secondary progress.
- `rgba(0, 122, 104, 0.16)` `--color-focus` focus ring.

## Typography
- Font stack: `Pretendard`, `Apple SD Gothic Neo`, `Noto Sans KR`, system sans-serif. This is intentional for Korean financial UI clarity.
- Display: `28px / 700 / 1.22 / 0`.
- Page title: `20px / 700 / 1.28 / 0`.
- Section title: `17px / 700 / 1.35 / 0`.
- Card title: `16px / 700 / 1.45 / 0`.
- Body: `14px / 500 / 1.58 / 0`.
- Caption: `12px / 500 / 1.45 / 0`.
- Micro: `11px / 600 / 1.35 / 0`.
- Numbers: use the same stack with `font-variant-numeric: tabular-nums`.

## Spacing
- Base unit: `4px`.
- Tokens: `--space-1 4px`, `--space-2 8px`, `--space-3 12px`, `--space-4 16px`, `--space-5 20px`, `--space-6 24px`, `--space-7 28px`, `--space-8 32px`, `--space-10 40px`, `--space-12 48px`.
- App horizontal padding: `20px`.
- Card padding: `16px` to `20px`.
- Screen section gap: `14px`.

## Components
- Phone shell: max-width `430px`, white surface, desktop radius `28px`, mobile radius `0`.
- Card: white, `1px solid --color-border`, radius `18px`, soft shadow `--shadow-card`.
- Primary button: teal background, white text, height at least `48px`, radius `14px`, pressed transform `translateY(1px)`.
- Secondary button: white or primary-soft surface, teal text, `1px` primary border or no border depending on density.
- Bottom tab: fixed bottom, white, hairline border, active violet icon with soft violet active marker.
- Evidence card: soft violet surface with white card interior, used for onboarding, compare, coach, and mission proof.
- Data row: icon badge, text, compact progress, trailing value.

## Motion
- Duration: `150ms` for press/focus, `220ms` for card and route affordances.
- Easing: `cubic-bezier(.2, .8, .2, 1)`.
- GPU-only motion: transform, opacity, filter.
- Reduced motion: remove transforms and nonessential transitions.

## Depth
- Strategy: borders first, whisper shadows second.
- `--shadow-card`: `0 1px 2px rgba(16, 17, 20, 0.04), 0 10px 30px rgba(16, 17, 20, 0.06)`.
- `--shadow-button`: `0 8px 18px rgba(0, 122, 104, 0.18)`.
- `--shadow-shell`: `0 28px 90px rgba(18, 18, 31, 0.14)`.

## Do / Don't
- Do keep teal/green as the single product signal and use violet only for AI coach or peer-comparison emphasis.
- Do prefer crisp borders, tabular numbers, and compact data rows.
- Do make SNS elements feel verified and financial, not casual comments pasted into a banking app.
- Don't use forced purple gradients, glass panels, oversized playful cards, or generic placeholder copy.
- Don't introduce new backend data, competitor logos, or unverified financial claims.
