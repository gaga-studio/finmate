# FinMate Design System

## Atmosphere
FinMate should feel like a real Korean mobile finance app with a social layer. The home screen follows the reference structure: daily mission, budget, spending summary, asset status, and following finance updates. The product should read as precise and trustworthy first, then lightly social through verified peer activity.

Purple is the reference-led product accent for active navigation, missions, and primary actions. Green is reserved for budget, progress, and success states. Avoid generic AI-purple glow, decorative gradients, and playful density that makes the app feel like a demo.

## Color
- `#F5F6FA` `--color-page` app canvas and desktop background.
- `#FFFFFF` `--color-app` phone app surface.
- `#FFFFFF` `--color-card` primary card surface.
- `#101114` `--color-ink` primary text.
- `#3F4050` `--color-ink-soft` secondary strong text.
- `#696C7D` `--color-muted` helper text.
- `#8F92A3` `--color-muted-soft` quiet labels.
- `#E7E8EF` `--color-border` default border.
- `#D9DBE7` `--color-border-strong` selected border.
- `#6546E8` `--color-primary` active tab, mission, and primary CTA.
- `#4E34C4` `--color-primary-strong` pressed primary.
- `#F1EEFF` `--color-primary-soft` mission and selected surfaces.
- `#DED7FF` `--color-primary-border` primary soft border.
- `#149E61` `--color-success` budget and success progress.
- `#E8F7F0` `--color-success-soft` success surface.
- `#026B3F` `--color-success-strong` success text.
- `#6D5DF2` `--color-coach` AI coach and comparison accent.
- `#F0EEFF` `--color-coach-soft` AI coach subtle surface.
- `#DDD8FF` `--color-coach-border` AI coach border.
- `#D97706` `--color-warning` spending and warning state.
- `#FFF5E7` `--color-warning-soft` spending surface.
- `#B42318` `--color-danger` destructive state.
- `#FFF0F0` `--color-danger-soft` destructive surface.
- `#EEF0F6` `--color-track` progress track.
- `#BFC3D4` `--color-track-strong` secondary progress.
- `rgba(101, 70, 232, 0.16)` `--color-focus` focus ring.

## Typography
- Font stack: `Pretendard`, `Apple SD Gothic Neo`, `Noto Sans KR`, system sans-serif.
- Home greeting: `22px / 900 / 1.3 / 0`.
- Auth hero title: `26px / 900 / 1.22 / 0`, reduced to `24px` on narrow screens.
- Page title: `18px / 800 / 1.3 / 0`.
- Section title: `16px / 900 / 1.35 / 0`.
- Card title: `16px / 800 / 1.45 / 0`.
- Body: `14px / 500 / 1.58 / 0`.
- Caption: `12px / 700 / 1.45 / 0`.
- Numbers: use `font-variant-numeric: tabular-nums`.

## Spacing
- Base unit: `4px`.
- Tokens: `--space-1 4px`, `--space-2 8px`, `--space-3 12px`, `--space-4 16px`, `--space-5 20px`, `--space-6 24px`, `--space-7 28px`, `--space-8 32px`, `--space-10 40px`, `--space-12 48px`.
- Home horizontal padding: `20px`.
- Card padding: `16px`.
- Home section gap: `12px`.
- Bottom safe area: keep at least `32px` scroll padding above the tab bar.

## Components
- Phone shell: max-width `430px`, desktop radius `28px`, mobile radius `0`.
- Home card: white, `1px solid --color-border`, radius `18px`, soft shadow.
- Mission card: `--color-primary-soft`, purple progress, right character asset.
- Budget card: white, three number columns, green progress.
- Spending summary: four circular category icons with compact values.
- Asset status: total asset number plus right-aligned sparkline.
- Following summary: four compact stats with social-finance icon badges.
- Primary button: purple background, white text, minimum height `48px`, radius `14px`.
- Bottom tab: fixed 5 tabs, active purple state, safe-area padding.
- Empty state card: dashed border, muted background, clear next action.
- Auth hero: two-column title and character composition, with supporting copy spanning the full card width.

## Motion
- Duration: `150ms` for press/focus, `220ms` for card and route affordances.
- Easing: `cubic-bezier(.2, .8, .2, 1)`.
- GPU-only motion: transform and opacity.
- Reduced motion: remove nonessential transitions.

## Do / Don't
- Do match the reference home information order: mission, budget, optional birthday event, spending, assets, following.
- Do keep green limited to budget and successful progress.
- Do separate real user state from seeded demo state.
- Do make SNS elements feel like verified financial activity, not casual comments.
- Don't auto-fill new accounts with fake missions, assets, friends, or birthday funds.
- Don't use competitor logos, unverified claims, heavy gradients, glass panels, or decorative glow.
