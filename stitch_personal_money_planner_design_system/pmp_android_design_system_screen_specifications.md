# PERSONAL MONEY PLANNER
## Android Design System, Interaction System & Screen Specification
*Targeting Jetpack Compose + Material 3 · Indian Household Cash-Flow Planning*

---

### 1. DESIGN DIRECTION
**Core Philosophy: "Never pretend to know what the app does not know."**
Personal Money Planner is a forward-looking household cash-flow planning tool for Indian families, salaried individuals, couples, and dual-income households. It explicitly answers: *"Given what I know today, how much money will remain over the next 12 months, and when could it run short?"*

- **Visual Tone:** Factual, calm, deliberate, highly legible under bright daylight and outdoor mobile conditions.
- **Anti-Patterns Rejected:** No gradient hero cards, no glassmorphism, no neon/crypto palettes, no celebratory confetti/streaks, no gamified health scores, no synthetic population benchmarks, no decorative empty-state illustrations.
- **Hierarchy Drivers:** Typography weight, structured tabular grids, hairline dividers (`1dp`), explicit semantic status pills with icons and text, and clear surface contrast (`#F8F9FA` vs `#FFFFFF`).

---

### 2. TOKEN SYSTEM (JETPACK COMPOSE & MATERIAL 3)

#### 2.1 Color Tokens

##### Brand & Surface Tokens
| Token Name | Light Value | Dark Value | Compose Token / Usage |
|---|---|---|---|
| `color.brand.primary` | `#000666` | `#B4C5FF` | `MaterialTheme.colorScheme.primary` (Interactive accents, active nav) |
| `color.brand.onPrimary` | `#FFFFFF` | `#001A72` | `MaterialTheme.colorScheme.onPrimary` |
| `color.brand.primaryContainer` | `#EEF2FF` | `#1A2B88` | `MaterialTheme.colorScheme.primaryContainer` |
| `color.brand.onPrimaryContainer` | `#000666` | `#DAE2FF` | `MaterialTheme.colorScheme.onPrimaryContainer` |
| `color.surface.page` | `#F8F9FA` | `#111318` | `MaterialTheme.colorScheme.background` (Scaffold background) |
| `color.surface.card` | `#FFFFFF` | `#1A1C22` | `MaterialTheme.colorScheme.surface` (Cards, list rows) |
| `color.surface.cardElevated` | `#F1F3F9` | `#22242B` | `MaterialTheme.colorScheme.surfaceVariant` (Nested containers, table headers) |
| `color.border.default` | `#E8EAF6` | `#2D313A` | Hairline card & row divider (`1dp`) |
| `color.border.subtle` | `#F0F2F8` | `#23262E` | Secondary divider |
| `color.text.primary` | `#0F172A` | `#E2E8F0` | High-emphasis headings, amounts, labels |
| `color.text.secondary` | `#475569` | `#94A3B8` | Supporting context, dates, confidence labels |
| `color.text.tertiary` | `#64748B` | `#64748B` | Metadata, captions, disabled hints |

##### Semantic Color Tokens (Strictly Meaning-Driven; Dynamic Color Disabled)
*Rule: Brand colour is never substituted for semantic state. Semantic meaning never communicates through colour alone.*
| Semantic State | Fill Token | Border Token | Text Token | Meaning & Required Symbols |
|---|---|---|---|---|
| **Positive** (Money In / Completed) | `#E6F4EA` | `#A7D7B5` | `#065F46` | Always prepend `+` and "Money in" / "Recorded" |
| **Negative** (Money Out / Deficit) | `#FCE8E6` | `#F5B7B1` | `#BE123C` | Always prepend `−` and "Money out" / "Overdue" |
| **Warning** (Upcoming / Due / Attention) | `#FEF3C7` | `#FCD34D` | `#B45309` | Always pair with `[!]` or "Upcoming" / "Due in Xd" |
| **Neutral / Unknown** (Incomplete info) | `#F1F5F9` | `#CBD5E1` | `#475569` | Always pair with `[?]` and "Account unknown" / "Estimate" |

#### 2.2 Typography Tokens (Inter Variable Font — APK Budget ~400 KB)
*All monetary figures use `FontFeatureSettings("tnum")` (Tabular Numerals) with Indian digit grouping (`₹1,23,456`). Rupee symbol rendered at weight `FontWeight.Normal` while digits use `FontWeight.SemiBold` or `FontWeight.Bold`.*

| Token | Font & Weight | Size / Line Height | Tracking | Compose Mapping | Usage |
|---|---|---|---|---|---|
| `type.display.large` | Inter Bold (700) | 32sp / 40sp | -0.5sp | `Typography.displayLarge` | Primary Available Balance |
| `type.headline.large` | Inter SemiBold (600) | 24sp / 32sp | -0.25sp | `Typography.headlineLarge` | Screen Headers, Forecast Peaks |
| `type.headline.medium` | Inter SemiBold (600) | 20sp / 28sp | 0sp | `Typography.headlineMedium` | Card Titles, Month Display |
| `type.title.medium` | Inter Medium (500) | 16sp / 24sp | 0.15sp | `Typography.titleMedium` | Section Headers, List Titles |
| `type.body.large` | Inter Regular (400) | 16sp / 24sp | 0.25sp | `Typography.bodyLarge` | Form Inputs, Descriptions |
| `type.body.medium` | Inter Regular (400) | 14sp / 20sp | 0.25sp | `Typography.bodyMedium` | List Row Subtitles, Breakdown Items |
| `type.label.large` | Inter Medium (500) | 14sp / 20sp | 0.1sp | `Typography.labelLarge` | Buttons, Active Tabs |
| `type.label.medium` | Inter Medium (500) | 12sp / 16sp | 0.5sp | `Typography.labelMedium` | Status Pills, Confidence Tags |
| `type.label.small` | Inter Regular (400) | 11sp / 14sp | 0.5sp | `Typography.labelSmall` | Table Column Headers, Captions |

#### 2.3 Spacing Scale (4dp Base Scale)
- `spacing.xxs` = 4dp (Micro-alignment, icon-to-tag gap)
- `spacing.xs` = 8dp (Icon-to-text, chip gaps, tight row padding)
- `spacing.sm` = 12dp (Compact row spacing, pill padding)
- `spacing.md` = 16dp (Standard container padding, list item vertical padding)
- `spacing.page` = 20dp (Standard mobile page horizontal margin)
- `spacing.lg` = 24dp (Card internal padding, major group spacing)
- `spacing.xl` = 32dp (Distinct section separation)
- `spacing.xxl` = 40dp+ (Breathing room, bottom navigation clearance)

#### 2.4 Radius & Elevation Hierarchy
- **Radius 8dp (`radius.sm`):** Form controls, text fields, chips, status pills, buttons.
- **Radius 12dp (`radius.md`):** Secondary nested containers, callout boxes.
- **Radius 16dp (`radius.lg`):** Important grouped content blocks, timeline cards.
- **Radius 24dp (`radius.xl`):** Primary root cards (never pill-shaped).
- **Elevation Level 0:** Page canvas (`#F8F9FA`).
- **Elevation Level 1:** Cards (`#FFFFFF`, hairline border `1dp #E8EAF6`, shadow elevation `0dp` or `1dp`).
- **Elevation Level 2:** Floating Action Buttons / Sticky headers (`2dp`).
- **Elevation Level 3:** Modal Bottom Sheets (`6dp`).

---

### 3. COMPONENT INVENTORY & SPECIFICATIONS

#### 3.1 Amount Display Component (`PmpAmountText`)
- **Anatomy:** Currency Symbol (`₹`, `FontWeight.Normal`), Space (`2dp`), Integer Part (`FontWeight.SemiBold`, Tabular Numerals), Sign indicator (`+` or `−`).
- **Variants:**
  1. *Large:* 32sp Display (Available balance).
  2. *Medium:* 20sp Headline (Card sums, monthly totals).
  3. *Small:* 15sp Body (List rows, tabular cells).
  4. *Comparison:* Stacked previous vs current with delta calculation.
  5. *Signed:* `+ ₹85,000` (Money in) / `− ₹18,500` (Money out) with supporting textual label.

#### 3.2 Confidence & Status Pills (`PmpStatusPill`)
*Never relies on color alone. Includes explicit text uppercase label, bordered background, and semantic icon.*
1. **RECORDED:** `#E6F4EA` bg, `#065F46` text & border, `✓` icon. *"Directly entered or bank-verified."*
2. **UPCOMING:** `#FEF3C7` bg, `#B45309` text & border, `⏱` icon. *"Scheduled future transaction."*
3. **OVERDUE:** `#FCE8E6` bg, `#BE123C` text & border, `!` icon. *"Past scheduled due date."*
4. **ESTIMATED:** `#F1F5F9` bg, `#475569` text & dashed border, `~` icon. *"User-estimated future sum."*
5. **FORECAST:** `#EEF2FF` bg, `#000666` text & border, `↗` icon. *"Projected based on X months history."*
6. **ACCOUNT UNKNOWN:** `#F1F5F9` bg, `#475569` text & border, `?` icon. *"Unassigned historical data."*

#### 3.3 Comparison & Progress Bars
- **Progress Bar (`PmpProgressBar`):** Requires explicit numerator and denominator (`₹18,500 / ₹25,000 · 74%`). Never isolated percentages.
- **Comparison Bar (`PmpComparisonBar`):** Two horizontal paired tracks (e.g., Budget vs Spent, or Sep Day 9 vs Aug Day 9) with exact figures clearly labeled above each track.

#### 3.4 Month Selector (`PmpMonthSelector`)
- **Structure:** `[ ‹ Previous ]` · **September 2026** `(This month)` · `[ Next › ]`.
- **Interactions:** Touch targets ≥ 48dp. Prevents fast-swipe skipping. Accessible announcement: *"Selected month September 2026, currently ongoing."*

#### 3.5 Bottom Navigation (Strictly 5 Destinations)
1. **Home** (Dashboard)
2. **Forecast** (12-month forward projection)
3. **Calendar** (Daily cash movement & timeline)
4. **Reports** (Category, trends & commitments breakdown)
5. **More** (Accounts, Credit Cards, Budgets, People, Export, Settings)

---

### 4. SPECIFICATIONS FOR 8 CORE SCREENS

#### Screen 1: Dashboard (Calm Household Clarity)
- **Primary Questions Answered:**
  1. How much money do I have in total?
  2. How much is actually available to spend right now?
  3. What bills/commitments are coming in the next 7 days?
- **Structure:**
  - *Top Bar:* "Personal Money Planner" + Current Date + Notification/Profile badge.
  - *Hero Summary (No Gradients):* Clean white card with hairline border.
    - Title: "AVAILABLE TO SPEND" → `₹72,500` (32sp Bold).
    - Sub-row breakdown: "Total bank & cash: ₹92,500" · "Earmarked savings: −₹20,000" with `[i]` drill-down.
  - *Next 7 Days Section:* "₹18,400 committed before salary" (3 items: Rent ₹15,000 [Due 10 Sep], Electricity ₹2,200 [Due 12 Sep], CC Min ₹1,200 [Due 14 Sep]).
  - *Month-End Forecast Card:* "Expected Month-End Balance: ₹41,300" · Supporting badge: "Forecast · Based on 6 months history".
  - *Attention Required Section:* 1 Overdue item (Water bill ₹450) + 1 Unassigned transaction (₹8,500).
  - *Primary Action (Bottom Right FAB or sticky reachable bar):* `[ + Add Expense ]`.

#### Screen 2: 12-Month Cashflow Forecast
- **Primary Question:** *"Will my household run short of money over the next 12 months, and when?"*
- **Structure:**
  - *Header:* 12-Month Outlook (Sep 2026 – Aug 2027).
  - *Temporal Bar / Step Chart:* Discrete monthly balance projections with tabular values (Sep: ₹41,300, Oct: ₹36,800, Nov: ₹28,500, Dec: −₹4,800 [Shortfall]).
  - *Shortfall Warning Card (Calm & Factual):*
    - Header: `[!] POSSIBLE SHORTFALL DETECTED` (Amber/Red subtle border).
    - Date: 18 December 2026.
    - Projected Balance: `− ₹4,800`.
    - Explanation: "Projected annual insurance premium (₹25,000) occurs before expected salary on 31 Dec."
    - Action: `[ View Contributing Transactions ]`.
  - *Forecast Data Basis Card:* "Based on 6 months of recorded expenses, 14 recurring commitments, and 3 user estimates."

#### Screen 3: Money Calendar (Timeline Hybrid)
- **Primary Question:** *"On which exact dates does money move in and out?"*
- **Structure:**
  - *Month Header:* September 2026 with 7-day compact week strip. Today indicator on Day 9.
  - *Day Transaction Timeline:*
    - 9 Sep (Today): `[RECORDED]` Salary Credit `+₹85,000` (HDFC Bank · UPI).
    - 10 Sep (Tomorrow): `[UPCOMING]` Home Loan EMI `−₹18,500` (SBI Auto-debit).
    - 12 Sep: `[UPCOMING]` BESCOM Electricity `−₹2,200` (Estimated).
    - 5 Sep: `[OVERDUE]` Society Maintenance `−₹3,500` (Due 5 Sep · Tap to settle).

#### Screen 4: Reports (Factual, Contextual Comparisons)
- **Primary Question:** *"Where did money go, and how does this compare to elapsed time?"*
- **Structure:**
  - *Elapsed Banner:* "September 2026 · 9 of 30 days elapsed (30% of month)".
  - *Proportional Comparison:* "Spent so far: ₹14,500 vs ₹13,800 by Day 9 last month (+5%)".
  - *Category Breakdown Table (Text primary, bars secondary):*
    - Housing: ₹18,000 (37%)
    - Groceries & Food: ₹9,200 (19%)
    - Vehicle & Transport: ₹5,800 (12%)
    - Healthcare: ₹2,400 (5%)

#### Screen 5: Budgets (Explicit Carry-Forward Architecture)
- **Primary Rule:** Never treat carried-forward surplus as unexpected overspending.
- **Structure:**
  - Category Card: **Food & Dining**
    - Available This Month: `₹13,000`
    - Sub-breakdown: "Base monthly budget: ₹10,000" + "Carried forward from Aug: +₹3,000".
    - Spent so far: `₹7,200` (Progress track showing 55% of ₹13,000).
    - Remaining spendable: `₹5,800` (Healthy state).

#### Screen 6: Credit Cards (Statement vs Unbilled Separation)
- **Primary Rule:** Never combine statement balance and unbilled charges into one ambiguous number.
- **Structure:**
  - Card Header: **HDFC Regalia Gold (••4092)**
  - *Section A: Bank Statement (Fixed):*
    - Statement Outstanding: `₹18,500` (Generated 2 Sep · Due 20 Sep).
    - Due Status: `[DUE IN 11 DAYS]` (Warning tag).
  - *Section B: Unbilled Purchases (Active):*
    - `₹4,200` (6 recorded transactions since 2 Sep statement).
  - *Section C: Current Card Exposure:*
    - Total currently owed: `₹22,700` (`₹18,500 + ₹4,200`).
    - Credit Limit: `₹1,00,000` · Available Credit: `₹77,300`.

#### Screen 7: Person Detail (Rahul Sharma — Shared Family/Friend Money)
- **Primary Rule:** No ambiguous "Balance ₹8,500". Explicitly state direction of debt.
- **Structure:**
  - Net Position Card:
    - Direction: **Rahul owes you ₹8,500** (`+ ₹8,500`).
    - Audit Trail: "You paid for Rahul: ₹12,000" · "Rahul paid you back: ₹3,500".
  - Recent Ledger:
    - 7 Sep: You paid Dinner bill (Rahul's share ₹2,000).
    - 2 Sep: Rahul transferred via UPI `−₹3,500`.

#### Screen 8: Add Expense (Rapid, High-Scan Ergonomics)
- **Target:** Under 10 seconds execution time; thumb-accessible.
- **Structure:**
  - Amount Field: `₹ [       ]` (Autofocused, 32sp tabular input).
  - Category Selector: Chips for Top 4 recent (Food, Groceries, Fuel, Bills).
  - Date Selector: "Today (9 Sep)" default.
  - Payment Source: "HDFC Bank (UPI)" (Last-used sticky default).
  - Note: "Optional note" field.
  - Action: Sticky bottom bar `[ Save Expense · ₹850 ]`.
  - On Success: Immediate calm banner: *"Saved ₹850 to Food · HDFC Bank"* and instant return to previous screen. No fireworks, no confetti.

---

### 5. MOTION & INTERACTION SPECIFICATION
1. **Duration:** 150ms–250ms Material 3 standard easing (`FastOutSlowInEasing`).
2. **What animates:** Modal bottom sheet slides up/down; month transition horizontal cross-fade; accordion breakdown expansion.
3. **What NEVER animates:** No counting numbers from ₹0 to ₹72,500; no celebratory popups; no spinning hero wheels; no looping decorative gradients. Numbers render immediately upon screen mount.
4. **Accessibility:** Honor `LocalConfiguration.current.isTouchExplorationEnabled` and `prefers-reduced-motion` to immediately snap values.

---

### 6. RATIONALE (DECISION → PROBLEM → REJECTED ALTERNATIVE → REASON)
1. **Two Balance Numbers (Available vs Total):**
   - *Problem:* Users set aside ₹20,000 for emergency savings. Traditional apps either show ₹92,500 (tempting overspending) or hide the ₹20,000 completely (making user fear money was lost).
   - *Alternative Rejected:* Single blended balance card.
   - *Reason:* Truthful clarity. Earmarked money is legally in the bank, but psychologically committed. Showing both preserves financial reality.
2. **Credit Card Dual Tiers (Statement vs Unbilled):**
   - *Problem:* Paying ₹22,700 when the bank statement is ₹18,500 confuses users on statement reconciliation.
   - *Alternative Rejected:* Single balance labeled "Card Outstanding".
   - *Reason:* Prevents double-payment confusion and aligns exactly with Indian bank statement PDFs.
3. **Discrete Shortfall Forecast vs Panic Score:**
   - *Problem:* Predictive panic causes users to abandon financial tools.
   - *Alternative Rejected:* "Financial health score: 42/100 (Poor)".
   - *Reason:* Factual explanation ("Annual insurance due on 18 Dec") empowers the user to reschedule or set aside funds calmly.
