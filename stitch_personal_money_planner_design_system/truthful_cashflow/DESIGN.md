---
name: Truthful Cashflow
colors:
  surface: '#f9f9ff'
  surface-dim: '#cfdaf2'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f3ff'
  surface-container: '#e7eeff'
  surface-container-high: '#dee8ff'
  surface-container-highest: '#d8e3fb'
  on-surface: '#111c2d'
  on-surface-variant: '#464651'
  inverse-surface: '#263143'
  inverse-on-surface: '#ecf1ff'
  outline: '#767683'
  outline-variant: '#c6c5d3'
  surface-tint: '#4e56ab'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#010766'
  on-primary-container: '#747cd3'
  inverse-primary: '#bdc2ff'
  secondary: '#515f74'
  on-secondary: '#ffffff'
  secondary-container: '#d5e3fc'
  on-secondary-container: '#57657a'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#331200'
  on-tertiary-container: '#cf6721'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e0e0ff'
  primary-fixed-dim: '#bdc2ff'
  on-primary-fixed: '#010766'
  on-primary-fixed-variant: '#353e91'
  secondary-fixed: '#d5e3fc'
  secondary-fixed-dim: '#b9c7df'
  on-secondary-fixed: '#0d1c2e'
  on-secondary-fixed-variant: '#3a485b'
  tertiary-fixed: '#ffdbca'
  tertiary-fixed-dim: '#ffb68e'
  on-tertiary-fixed: '#331200'
  on-tertiary-fixed-variant: '#763300'
  background: '#f9f9ff'
  on-background: '#111c2d'
  surface-variant: '#d8e3fb'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  title-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.02em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  none: 0rem
  2xs: 0.125rem
  xs: 0.25rem
  sm: 0.5rem
  md: 0.75rem
  base: 1rem
  lg: 1.25rem
  xl: 1.5rem
  2xl: 2rem
  3xl: 3rem
  margin-mobile: 1rem
  gutter-mobile: 0.75rem
  margin-tablet: 1.5rem
  gutter-tablet: 1rem
---

## Brand & Style

This design system is tailored for managing Indian household cash flows. The brand emotional profile is anchored in composure, absolute mathematical honesty, and dignity. It rejects modern fintech cliches—there are no neon accents, gamified badges, celebratory confetti, or whimsical decorative vectors. Instead, it prioritizes legibility, dense structured information, and immediate scannability.

The visual style is rooted in a refined **Corporate / Modern** aesthetic, customized for high-density transactional tracking. Built around Android Material 3 and Jetpack Compose foundations, the system uses strict tabular presentation, understated surface distinctions, and dependable contrast to make everyday financial realities clear and low-stress for Indian families balancing irregular inflows, household commitments, EMIs, and long-term targets.

## Colors

The palette uses a deliberate, sober color strategy meeting WCAG AA contrast ratios across all semantic indicators.

- **Primary (`#000666`)**: Deep Navy. Used for authoritative anchors, primary buttons, major ledger balance indicators, and top-level navigation states.
- **Secondary / Neutral Slate (`#475569`)**: Used for informational labels, non-critical metrics, and structural metadata.
- **Positive (`#065F46`)**: Forest Green. Indicates real money-in, verified liquidity, and balanced states. Avoids bright neon greens.
- **Warning (`#B45309`)**: Warm Amber. Indicates pending bills, payments due within 7 days, and projections requiring attention.
- **Negative (`#BE123C`)**: Deep Rose. Explicitly marks outflows, deficits, breaches, and overdue commitments.
- **Page Background (`#F8F9FA`)**: Cool Off-White reducing glare during prolonged budgeting sessions.
- **Card Surface (`#FFFFFF`)**: Pure White for crisp visual containment.
- **Hairline Border (`#E8EAF6`)**: Subtle slate-blue border providing separation without heavy shadows.

### Confidence Tier Palette Accents
- **Recorded**: `#0F172A` text on `#F1F5F9` badge (Solid fill, verified data).
- **User Estimate**: `#1E40AF` text on `#EFF6FF` badge (Stated input).
- **Calculated**: `#334155` text on `#F8FAFC` with a 1dp `#CBD5E1` border.
- **Forecast**: `#B45309` text on `#FEF3C7` badge.
- **Low-Confidence Forecast**: `#94A3B8` text on transparent fill with a dashed `#CBD5E1` hairline.

## Typography

Inter serves as the sole type system, offering clean neutral forms and reliable tabular figure support.

### Tabular Alignment & Indian Digit Grouping
All monetary and numerical views must enforce OpenType tabular figures (`tnum`) and slashed zero (`zero`) where applicable:
- Compose implementation: `FontFeatureSettings("tnum, zero")`.
- Amounts must strictly follow the Indian number format (e.g., `₹12,34,567.00` instead of `₹1,234,567.00`).
- The Indian Rupee glyph (`₹`) is rendered at the identical weight and tabular baseline as adjacent numbers, preventing vertical offset issues across varied Android OEMs.

## Layout & Spacing

The layout follows a fluid-grid approach aligned to an 8dp baseline grid, with 4dp sub-steps for dense tabular components.

- **Mobile (< 600dp)**: Single-column flow with `16dp` outer side margins and `12dp` internal card gutters. Bottom navigation is anchored, leaving top viewports dedicated to ledger totals.
- **Foldables / Small Tablets (600dp - 839dp)**: Dual-pane layout (Account / Category list on the left, Detailed ledger / Monthly cash-flow forecast sheet on the right). `24dp` outer margins, `16dp` gutters.
- **Data Scannability**: Dense lists use fixed 48dp single-line heights or 64dp two-line item containers to maximize visible rows above the screen fold.

## Elevation & Depth

Visual hierarchy is maintained via **low-contrast outlines** and tonal layering, avoiding heavy shadows or muddy elevation tiers.

1. **Level 0 (Base Canvas)**: Background color `#F8F9FA`. No borders, no shadows.
2. **Level 1 (Default Containers & Cards)**: Pure white `#FFFFFF` surface with a `1dp` solid hairline border in `#E8EAF6`. Elevation is `0dp`.
3. **Level 2 (Dropdowns, Pickers, Bottom Sheets)**: Surface `#FFFFFF` with a `1dp` border `#E8EAF6` paired with a minimal structural shadow: `ambientColor = #000666` at 4% opacity, `spotColor = #000666` at 6% opacity, blur radius `8dp`, Y-offset `2dp`.
4. **Active/Focused Tile**: Surface `#FFFFFF` bordered with a `1.5dp` solid stroke of `#000666`.

## Shapes

The design system relies on a **Soft** shape language (`roundedness: 1`), keeping corner radii small and disciplined to convey structural precision.

- **Badges, Tags & Confidence Chips**: `4dp` (`0.25rem`) rounded corners.
- **Buttons & Input Fields**: `8dp` (`0.5rem`) corner radii for structured touch targets.
- **Cards & Data Groups**: `8dp` (`0.5rem`) corner radii.
- **Modal Sheets**: Top edge corners at `12dp` (`0.75rem`), with flat bottoms.
- Avoid full pill shapes and circular cards.

## Components

### Buttons
- **Primary**: Background `#000666`, text `#FFFFFF`, corner radius `8dp`, height `44dp`, horizontal padding `16dp`. Label uses `label-lg`.
- **Secondary (Outlined)**: Background transparent, border `1dp` solid `#475569`, text `#1E293B`, height `44dp`.
- **Destructive**: Background transparent, border `1dp` solid `#BE123C`, text `#BE123C`.

### Input Fields
- Standard height `52dp`, background `#FFFFFF`, border `1dp` solid `#CBD5E1`, corner radius `8dp`.
- **Currency Field**: Fixed prefix `₹` styled in `label-lg` with tabular alignment. Right-aligned amounts for consistent arithmetic scanning.
- **Focused State**: Border `1.5dp` solid `#000666`. Error state: Border `1.5dp` solid `#BE123C`.

### Lists & Ledger Rows
- High-density single-line (`48dp`) or dual-line (`60dp`) items.
- Hairline divider `#E8EAF6` inset by `16dp`.
- Structure: Left icon or category code, center description with date subtitle, right-aligned amount in bold tabular figures paired with a confidence badge.

### Confidence Tier Badges
Every cashflow projection displays an explicit visual indicator:
1. **Recorded**: Text `#0F172A`, fill `#F1F5F9`, 4dp radius, label `REC` (Verified transaction).
2. **User Estimate**: Text `#1E40AF`, fill `#EFF6FF`, 4dp radius, label `EST` (Manual budget value).
3. **Calculated**: Text `#334155`, fill `#F8FAFC`, 1dp border `#CBD5E1`, label `CALC` (Auto-derived from rules/recurrence).
4. **Forecast**: Text `#B45309`, fill `#FEF3C7`, 4dp radius, label `FCST` (Statistical expectation).
5. **Low-Confidence Forecast**: Text `#94A3B8`, border `1dp dashed #CBD5E1`, label `LOW-CONF` (High variance or incomplete pattern).

### Selection Controls
- **Checkboxes & Radios**: Check fill in `#000666`, unselected border in `#64748B`. Corner radius for checkboxes is `2dp`.
- **Switch**: Width `44dp`, height `24dp`, track off `#E2E8F0`, track on `#000666`, thumb white `#FFFFFF`.

### Cards & Summary Modules
- Pure `#FFFFFF` fill with `1dp` hairline border `#E8EAF6`.
- Internal padding is strictly `16dp`.
- Header houses the group title (`title-sm` uppercase, `#475569`) and monthly total formatted with Indian numbering grouping.