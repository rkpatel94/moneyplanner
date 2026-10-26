# Personal Money Planner — Figma UI design prompt

A ready-to-paste prompt for an AI design tool (Figma Make, Figma AI, Google Stitch, or any assistant with Figma access) to design every screen of the app with a fresh, next-generation look.

**How to use it**

- If the tool accepts long prompts, paste **Part 1** and **Part 2** together.
- If it limits prompt length, paste **Part 1** first, then send the batches in **Part 3** one at a time. Each batch names the screens it covers, and their full specs are in Part 2.
- Keep the sample data in Part 1 unchanged across batches so every screen tells the same story.

---

## PART 1 — MASTER BRIEF

### Your role

You are a senior product designer for a top fintech studio. Design a complete, production-ready Figma file for an Android app called **Personal Money Planner**: a design system with components and variables, every screen in light and dark mode, the key states of each screen, and a clickable prototype of the main flows. The result must look unique, trendy and unmistakably next-generation (2026), while every number stays instantly readable and trustworthy.

### The product

Personal Money Planner is an offline-first Android app for Indian households. It is not a ledger of what was spent. It projects what the user will actually have left in each coming month, built only from figures they entered themselves: salary, rent, EMIs, credit cards, bills, yearly payments such as insurance and school fees, money lent to or borrowed from people, savings goals and everyday spending.

- No account, no login, no cloud, no sync, no analytics, and no internet permission. Every record stays on the phone. Data leaves only when the user exports or backs up a file themselves.
- Platform: Android, Material 3 (Expressive) foundations, built in Jetpack Compose. Min Android 8.
- Language: English now, with Hindi and Gujarati planned.

### Who it is for

Salaried Indian households, aged 25 to 45, who pay by UPI every day and juggle a salary, rent, one or two loans, a credit card and family money. Their real question is not "what did I spend?" but "will I have enough on the 28th?". They want calm certainty, not a dashboard that shouts.

### The design concept: "Kal" (कल)

In Hindi, and in Gujarati as કાલ, *kal* means both **yesterday** and **tomorrow**. That is exactly what this app does: what you recorded (yesterday) projects what you will have (tomorrow). Make it the visual idea of the whole product:

- **The past is solid.** Recorded money is drawn with solid fills, firm ink and crisp edges.
- **The future is light.** Projected money is drawn translucent: soft gradients, dotted lines, hatched or glassy fills that clearly read as "not real yet".
- **Today is a horizon.** One horizon line divides past from future in charts, the calendar and timelines. It is the app's signature motif: a subtle arc on the Home hero, the "today" line on every chart, the onboarding illustration and the lock screen.

This is not decoration. It is how the app keeps its promise of never pretending to know what it does not know.

### Product rules the design must express (non-negotiable)

1. **Confidence is stated, never implied.** Every figure that is not a recorded fact carries a confidence pill with an icon, a word and a colour, so it works for colour-blind users and in direct sunlight. One vocabulary across every screen: `RECORDED`, `UPCOMING`, `OVERDUE`, `ESTIMATED`, `FORECAST`, `ACCOUNT UNKNOWN`.
2. **Two balances, both true.** Home leads with **Available to spend** (bank and cash minus money set aside for goals), with **Total bank & cash** and **Set aside for goals** beneath it. When nothing is set aside, the single label is **Available now**.
3. **Comparisons run to the same point.** A month in progress is compared with the same days of the previous month ("By day 26 of Sep"), never with its total. When the earlier month was shorter, both sides stop at the same day and the screen says so.
4. **A credit card purchase is not cash out.** It is marked **On card**. Only paying the card bill reduces cash.
5. **Money in and money out have fixed meaning colours.** Do not use wallpaper-based dynamic colour anywhere.
6. **Indian money format everywhere.**
   - Lakh and crore grouping: ₹1,23,456.
   - The ₹ symbol is set one weight lighter than the digits.
   - Lists use tabular figures so digits align.
   - Compact forms such as ₹1.2L and ₹2.4Cr appear only on chart axes.
   - Negative amounts show a minus sign in the negative colour and are never hidden.
7. **Private by design.** No login, no account avatar, no cloud or sync icons, no "connect your bank". Show privacy plainly, for example: "Everything stays on this phone."
8. **The assistant is rules-based, not AI.** Label it "Works offline · answers from your records". No sparkles and no AI branding.
9. **Calm, honest microcopy.** Say what happened and what to do next. No shaming, no exclamation marks, and report good news too.

### Visual direction

- **Mood:** "midnight indigo meets marigold". Indian-modern, premium and calm, like a contemporary Indian design studio rather than a generic blue bank. Bold where it helps (hero numbers, verdicts) and quiet everywhere else.
- **Layout:** bento grids on Home and Reports, with modular tiles of different sizes (1×1, 2×1, 2×2) on a 4-column grid. Page margins 20dp, gutters 12dp.
- **Depth:** "liquid glass" only for floating chrome: the bottom dock, the FAB, the sticky month selector and sheet headers. Use a translucent fill, 24–30 background blur and a 1px inner light stroke. Content cards stay solid so figures are always legible. Provide a solid fallback for devices without blur.
- **Shape:** Material 3 Expressive.
  - Cards 28dp, tiles 24dp, inputs 16dp, buttons and chips full pill.
  - Squircle containers for icons and avatars.
  - A scalloped "cookie" badge reserved for achievements such as **Goal reached**.
- **Numbers as heroes:** large expressive display numerals for hero figures, with an odometer roll when a value changes.
- **Texture:**
  - A barely-there grain (2–3%).
  - Soft aurora gradients (indigo → violet → teal) behind hero areas, strongest in dark mode.
  - A faint abstract pattern inspired by Indian block printing (around 4% opacity) on onboarding, empty states and the lock screen. Tasteful, never kitsch.
- **Illustration:** soft 3D, clay-style objects for empty states and onboarding: a coin jar, a calendar, a scooter, house keys, a marigold. Consistent top-left lighting. No religious symbols, no real bank logos.
- **Icons:** Material Symbols Rounded (weight 400, optical size 24). Outline when inactive, filled when selected.
- **Motion** (annotate it in the file):
  - Spring physics, damping about 0.85.
  - The active dock pill morphs between tabs.
  - Cards lift 4dp on press, with a haptic tick.
  - Balances roll like an odometer.
  - Lists stagger in at 30ms.
  - Loading uses shimmer skeletons.
  - Everything respects the system "remove animations" setting.

### Foundations (create as Figma Variables with Light and Dark modes)

**Colour.** Verify contrast and adjust tone, not hue, wherever a pair fails.

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| primary | #2B2F8F | #8A8FFF | Brand indigo, primary actions |
| on-primary | #FFFFFF | #0B0D2B | Text on primary |
| accent | #FFB21C | #FFB21C | Marigold: focus, active dock pill in dark, celebration (always with ink text) |
| accent-soft | #FFF1D1 | #3A2E12 | Accent containers |
| positive (money in) | #0B7A6B | #3FD3B9 | Peacock teal |
| negative (money out) | #C2334D | #FF7A8F | Kumkum rose |
| warning (overdue, near limit) | #9A5B00 | #FFC048 | Turmeric |
| forecast / info | #6B5BD6 | #A99BFF | Lavender, used for everything projected |
| unknown | #5B6275 | #A3AABB | Slate |
| background | #F6F4EF | #0B0D2B | Porcelain / midnight |
| surface | #FFFFFF | #15183D | Cards |
| surface-raised | #FFFFFF + shadow | #1D2150 | Sheets, dialogs |
| surface-sunken | #EEEBE4 | #0F1133 | Inputs, tracks |
| outline | #E2DED5 | #2C3166 | Hairlines |
| ink-primary | #12142B | #F4F3FF | Main text |
| ink-secondary | #4A4F66 | #B9BCE0 | Supporting text |
| ink-tertiary | #7C8196 | #858AB8 | Captions |

Each semantic colour also needs a container tone (a light wash in Light mode, a deep tone in Dark mode) for pills and tinted cards. Text must reach 4.5:1 contrast and large numerals and UI 3:1.

**Typography.** Every font is bundled with the offline app, so use only open-licence fonts.

| Style | Font | Size / line | Notes |
| --- | --- | --- | --- |
| Hero amount | Bricolage Grotesque 700 | 56 / 60 | −2% tracking; the ₹ is set in Inter one weight lighter at 85% size |
| Display | Bricolage Grotesque 700 | 40 / 44 | Screen heroes, verdicts |
| Headline | Bricolage Grotesque 600 | 28 / 32 | Large top bar titles |
| Title | Inter 600 | 20 / 26 | Card titles |
| Body | Inter 400 | 16 / 24 | |
| Body small | Inter 400 | 14 / 20 | Subtitles |
| Label | Inter 500 | 13 / 16 | Buttons, chips |
| Pill / overline | Inter 600 | 11 / 14 | Uppercase, +8% tracking |
| Money (lists) | Inter 600 | 16 / 20 | Tabular figures on for every number |

Use Noto Sans Devanagari and Noto Sans Gujarati as fallbacks. Leave room for 30% longer text, with 1.5× line height for Indic scripts.

**Layout.**
- 4dp grid: 4, 8, 12, 16, 20, 24, 32, 40.
- Page margin 20dp, card padding 16–20dp, section gap 24dp.
- Bottom content padding 120dp so the floating dock never hides content.
- Frames 412×917, edge to edge with a 32dp status bar and a 24dp gesture bar. Spot-check layouts at 360×800.

**Radius:** 8, 12, 16, 24, 28, full.

**Elevation:**
- level1: 0 1 2 rgba(11,13,43,.06) plus 0 4 12 rgba(11,13,43,.06).
- level2: 0 8 24 rgba(11,13,43,.10).
- glass: surface at 64% (Light) or 56% (Dark), blur 28, 1px inner stroke in white at 40% (Light) or 12% (Dark).

**Motion:** short 120ms, medium 240ms, long 400ms. A standard spring, plus an expressive spring for celebrations.

### Components (each with variants, properties and auto layout)

1. **Confidence pill:** 6 kinds × light/dark × two sizes. Icon plus word:
   - RECORDED: solid check circle, ink.
   - UPCOMING: clock, lavender outline.
   - OVERDUE: exclamation, turmeric fill.
   - ESTIMATED: "≈" wave, dashed outline.
   - FORECAST: dotted trend line, translucent lavender.
   - ACCOUNT UNKNOWN: question mark in a dashed square, slate.
2. **Money text:** hero, large, medium and small sizes. Positive, negative and neutral states. Sign shown or hidden. An "On card" suffix. A struck-through variant for undone entries.
3. **Two-balance hero card:** a big Available to spend figure, the Total bank & cash and Set aside for goals lines, a segmented bar showing the split, a quick-action chip row, and the horizon arc in the background.
4. **Bottom dock:** five tabs (Home, Forecast, Calendar, Reports, More) in a floating glass pill 16dp from the edges. The active tab is a filled pill with its label. Inactive tabs show an icon with a small label. Include a badge-dot variant.
5. **FAB:** a 64dp squircle "+" for Add expense. Long-press opens an arc menu: Expense, Income, Voice entry, Paste bank SMS, Move money.
6. **Top app bars:** large collapsing and small variants, back or close icons, trailing actions, and a transparent variant over a hero gradient.
7. **Month selectors:**
   - A compact "‹ October 2026 ›" control with a "This month" caption.
   - A **month horizon strip**: 12 scrollable chips, each with a mini bar of the projected closing balance. Past months are solid, future months translucent, and the current month carries the horizon marker.
8. **Bento tile:** 1×1, 2×1 and 2×2 sizes, each with a header (icon chip, title, chevron), a body slot and an optional footnote.
9. **List row:**
   - A leading category icon chip in the category colour.
   - Title, plus a subtitle of date and payment method.
   - Trailing amount with a confidence pill.
   - Variants with swipe actions and a checkbox (used by SMS review).
10. **Progress:**
    - Linear budget bar with a pace marker and a ghost "projected end" segment.
    - Goal ring.
    - Money-in vs money-out split bar.
    - Runway line: day-by-day balance with a comfort-floor band and a "today" horizon.
11. **Charts:**
    - Forecast area: past solid, future gradient with a dotted line.
    - Paired income vs spending bars.
    - Category bars, each with a marker for its own recent average.
    - Sparkline and donut.
    - All charts print their numbers beside the shapes.
12. **Amount input:** a hero field with a custom numeric keypad (1–9, 00, 0, decimal point, ⌫). Grouping updates live, the amount is spelled out in words beneath it, and a lakh/crore hint appears.
13. **Chips:** filter, choice (category with icon and colour), input (tags) and assist (quick actions).
14. **Inputs:** tonal text field, date field with a calendar sheet, dropdown, switch, segmented button, stepper, slider and PIN dots.
15. **Bottom sheets:**
    - Standard: drag handle, title, content and actions.
    - Confirmation that lists what else goes with the record.
    - "Type DELETE to confirm".
16. **Dialogs:** restore preview and export period picker (both specified under K3).
17. **Cards and banners:** attention (warning), runway warning, shortfall (negative), good news (positive), insight, empty state (illustration, title, body, action), skeleton.
18. **Snackbar** with an Undo action.
19. **Avatar:** an initials monogram in a squircle with a stable colour per name. No photos.
20. **Home-screen widget** components.

### Global patterns

- Every list screen needs an empty state, a loading skeleton, a populated state and a long list.
- Every editor has:
  - a top bar with close or back,
  - a sticky bottom save button that repeats the amount ("Save expense · ₹1,250"),
  - delete in the overflow menu,
  - a destructive confirmation that says what else is removed.
- Transfers and goal contributions show no plus or minus sign, because they do not change the total.
- Dates read "26 Oct" or "Mon, 26 Oct 2026". Relative due chips read "Due today", "Due in 3 days" or "2 days overdue".
- Destructive actions use negative-coloured text buttons. A filled red button appears only after the user types DELETE.
- Dark mode is first-class: design every screen in both modes.

### Sample data

Use this consistently across every screen. Today is **Mon, 26 Oct 2026**. These are sample figures for realistic mockups.

- **User:** Aarav (monogram "A"), Pune.
- **Accounts:** HDFC Savings ₹1,44,200, Cash ₹3,200, Paytm Wallet ₹850. Total bank & cash ₹1,48,250. Set aside for goals ₹1,07,000. **Available to spend ₹41,250.** Bank names appear as plain text only, never as logos.
- **Income:** Salary ₹85,000 on the 1st, paid into HDFC Savings. Freelance work, irregular, about ₹12,000.
- **Commitments:**
  - Rent ₹18,000 on the 5th.
  - Home loan EMI ₹24,500 (SBI, installment 38 of 240).
  - Car loan EMI ₹11,200 (installment 22 of 60, on the 10th).
  - Electricity, ESTIMATED at ₹2,100–₹2,800, on the 12th.
  - Internet ₹999 on the 8th.
  - Mobile ₹399 on the 15th.
- **Credit card:** HDFC Millennia, limit ₹1,50,000. Last statement ₹12,340, due 18 Nov. ₹4,560 charged since the statement, across 6 purchases.
- **Yearly payments:** Car insurance ₹14,200 on 5 Dec, School fees ₹45,000 on 10 Jun, Property tax ₹6,800 on 31 Mar.
- **People:**
  - Rohan owes you ₹3,500, expected 15 Oct (OVERDUE).
  - You owe Neha ₹2,000, expected 5 Nov.
  - A Goa trip bill split with Kabir and Rohan.
- **Goals:**
  - Emergency fund: target ₹1,80,000 (6 months of essentials), ₹70,000 saved, ₹1,10,000 to go.
  - Goa trip: ₹60,000 target, ₹22,000 saved, by Dec 2026.
  - New laptop: ₹90,000 target, ₹15,000 saved.
- **Budgets:**
  - Food ₹8,000: ₹5,620 spent, On track.
  - Shopping ₹5,000: ₹4,450 spent, Close to the limit.
  - Fuel ₹3,000 plus ₹400 carried forward: ₹2,150 spent.
- **Forecast:**
  - October: about ₹19,000 expected remaining (Comfortable).
  - November: Careful.
  - December: projected to end at −₹4,800, because the car insurance lands well before the next salary.
- **This month so far:** ₹31,480 spent by 26 Oct, against ₹34,900 by the same day of September (10% less).
- **Expense categories:** Food, Grocery, Travel, Fuel, Shopping, Rent, Utilities, Education, Medical, Entertainment, Family, EMI, Insurance, Investment, Vehicle, Gifts, Other.
- **Income categories:** Salary, Freelance, Business, Other income.
- **Payment methods:** Cash, UPI, Bank transfer, Debit card, Credit card, Auto debit, Cheque, Wallet.

### Figma file structure

0. **Cover:** the Kal concept, the principles and a palette strip.
1. **Foundations:** variables in Light and Dark, type scale, spacing, radius, elevation and glass, icons, motion notes, accessibility checks.
2. **Components:** everything listed above, with variants, properties and usage notes.
3. **Patterns:** empty, loading and error states; sheets, dialogs, confirmations; list rows; editors.
4. **Screens:** one section per flow (A–L in Part 2).
   - Each screen appears in Light and Dark, with its listed states side by side.
   - Name frames like "B1 Home / Dark / Populated".
5. **Prototype:** clickable flows:
   - Onboarding → Home.
   - Add an expense with the keypad → saved.
   - Home → Forecast → shortfall → Calendar opened on that month.
   - Record a payment from a person.
   - Restore a backup through its preview.
   - The lock screen.
6. **Widget and notifications.**
7. **Handoff:** redlines for the key screens, token names, and notes mapping each component to Compose and Material 3.

**Quality bar:**
- Auto layout everywhere and no detached instances.
- Realistic data, never lorem ipsum.
- Everything pixel-aligned to the 4dp grid.
- Touch targets at least 48dp.
- Accessibility annotations covering contrast, focus order on editors, and TalkBack labels that read amounts in full ("rupees forty-one thousand two hundred fifty").
- Screens tested at 200% font size.
- No effect Compose cannot build. Blur degrades to solid translucent surfaces on older devices.

---

## PART 2 — EVERY SCREEN

For each screen: what it is for, what it shows, and the states to draw.

### A. Getting in

**A1 Lock screen.**
- Contents: "Money Planner is locked" over the horizon motif, PIN dots with a custom keypad, and a "Use biometrics" button (fingerprint or face).
- Nothing financial is visible behind the lock.
- States:
  - default,
  - wrong PIN (shake and message),
  - lockout ("Too many attempts. Try again in 0:30" with a countdown ring),
  - biometric prompt.

**A2 Onboarding.** Five steps. Every step is skippable, with progress dots and a "Skip setup" link.
1. "Let us set you up": what should we call you? Includes the privacy line "Everything stays on this phone. There is no account, no sync…"
2. "How much do you have?": money available now across bank and cash, entered on the amount keypad. "It does not need to be exact."
3. "What comes in?": monthly income, plus the day it usually arrives as a 1–31 day chip grid.
4. "What goes out every month?": Rent or housing, and Loan installments (total). "You can add the rest later."
5. "Here is what that means": a review screen.
   - Lines: Starting with, Expected in, Already committed, and "That leaves about ₹19,000 at the end of October".
   - A verdict: "This month works out" or "This month is projected to fall short".
   - The horizon chart animates from today to the end of the month.
   - Buttons: "Go to my dashboard" and "Change something".

### B. Bottom-bar tabs

**B1 Home (Dashboard).** A bento grid. The order is the argument: what you have, what is wrong, what is about to leave, then how the month ends.
- **Greeting row:** "Good afternoon, Aarav", today's date, a search icon and the monogram avatar.
- **Two-balance hero (2×2):**
  - "AVAILABLE TO SPEND ₹41,250".
  - Beneath it: Total bank & cash ₹1,48,250 and Set aside for goals −₹1,07,000.
  - Quick chips: Expense, Income, EMI, Save.
- **Needs attention (2×1, warning surface, shown only when needed):**
  - Up to 3 overdue rows, for example "Rohan · ₹3,500 · Due 15 Oct" with an OVERDUE pill.
  - A total line: "and 2 more overdue. Late in all: ₹X to pay and ₹Y to receive."
  - A row reading "3 expenses with no account · ₹1,240" with an ACCOUNT UNKNOWN pill.
- **Runway warning (only when relevant):** "Balance dips low around 3 Nov · lowest point ₹1,150". The comfort floor is ₹2,000. Never shown on a brand-new install.
- **Next 7 days (2×1):** up to 6 dated items with pills, a subtitle "₹42,340 due by 2 Nov", an "and N more" line, and "View all" (opens Calendar).
- **October forecast (2×1):**
  - A verdict chip: Comfortable, Careful or Tight, each with an icon.
  - "Expected in against payments", shown as a commitment bar ("68% committed").
  - Lines: "₹X going out · ₹Y expected".
  - "Safe to spend ₹Z, after every commitment you have recorded".
- **Small tiles (1×1):** Income received vs expected; Spent this month, with the same-point comparison; Saved this month; People (owed to you / you owe); Credit card due (₹12,340 on 18 Nov); Emergency fund ring ("2.3 months of essential expenses").
- **Recent spending (2×1):** the last 4 expenses.
- **Budgets (2×1):** budgets needing attention, otherwise the top two plus "All 3 budgets are on track".
- **Plan ahead:** assist chips for Can I afford it?, Ask about your money, Goals and Budgets.
- **Worth noticing:** an insight carousel covering a spending spike, a loan nearly finished, a card near its limit, and good news ("You spent 10% less than last month · ₹3,420 less than you had by the 26th of last month").
- A FAB (Add expense) floats above the dock.
- States:
  - populated,
  - brand-new install (a "Start with what you know" card, no warnings),
  - negative balance,
  - loading skeleton.

**B2 Forecast ("money ahead").**
- **Month horizon strip:** 12 months.
- **Possible shortfall card** (negative surface, for the first short month): "December 2026 is projected to end at −₹4,800. Car insurance (₹14,200) falls 27 days before the next money is expected." Button: "See what is dated that month", which opens the Calendar on December.
- **Basis card:** "Based on 3 months of your spending, 9 recurring commitments and 1 estimate."
- **Expected remaining hero** for the selected month, with a FORECAST pill.
- **Split bar:** money in vs money out, with paired tiles.
- **Expected breakdown:** Income, EMIs, Bills, Card dues, Yearly payments, People, and Everyday spending (ESTIMATED).
- **Dated payments:** a collapsible list with pills, OVERDUE included.
- A "Thinking about a purchase?" link opens Can I afford it?.
- States: populated, shortfall, "Nothing to forecast yet".

**B3 Money calendar.**
- A month selector with a 7-column grid.
- Each day cell shows small money-in (teal) and money-out (rose) markers, and an overdue dot where something is late.
- Today wears the horizon ring. Past days are solid, future days translucent.
- Tapping a day opens its list below the grid: what happened (RECORDED) comes first, then what is expected (UPCOMING or ESTIMATED), with overdue items marked.
- An "Everything this month" list.
- Past months show recorded items only.
- States: current month, past month, a future month opened from a link, an empty month.

**B4 Reports.** A month selector, then these cards:
- **This month so far:** "October · 26 of 31 days elapsed (83%)", Spent so far against "By day 26 of Sep", and "That is 10% less than by the same day last month". When the earlier month was shorter, both bars stop at the same day and a footnote says so.
- **Income against spending:** paired bars over six months, with the current month flagged "in progress".
- **Where it went:** category bars, each with a marker for its own recent average, for example "Food +32% against your usual".
- **What your commitments cost:** EMIs, bills and the yearly reserve, plus a donut titled "The part of your month that is already spoken for".
- **By account:** money in and out per account.
- **Savings progress:** goal rings.
- **People.**
- States: populated, "Nothing to report yet".

**B5 More.** A hub of grouped icon tiles (2-column grid) with search at the top:
- Where your money is: All transactions, Recent activity, Accounts.
- People: People and splits.
- Save typing: Import from SMS.
- Money coming in: Income.
- Money going out: EMIs and loans, Recurring bills, Credit cards, Yearly expenses.
- Saving: Savings goals, Emergency fund.
- Limits: Budgets.
- Understand: Can I afford it?, Ask about your money.
- Household: Vehicles, Family.
- Settings.

### C. Recording money

**C1 Add or edit expense.**
- Hero amount with the custom keypad.
- Description.
- Category choice chips (icon plus colour), in a two-row scroller.
- Date.
- Payment method chips, with UPI as the default.
- **Paid from:** an account that defaults automatically (cash comes from Cash, everything else from the bank). Choosing Credit card shows a "Which card" picker and the note "This does not leave your bank balance today."
- **More details:** Person involved, Vehicle, Spent on (family member), Tags as input chips, and a receipt attachment (photo or PDF thumbnails).
- Sticky "Save expense · ₹1,250" button, plus an "Add with voice" button.
- Voice states:
  - a listening sheet with a waveform,
  - a confirmation card showing "What I heard" against "What I understood",
  - error states: "Voice input is not available" and "No amount was recognised. Say the amount first, for example '500 lunch'".
- Edit state: a "Created by a scheduled payment" banner for expenses that came from paying a bill.
- Delete confirmation that explains the effect on the balance.

**C2 All transactions.**
- A "‹ October 2026 ›" selector, the Total spent, and category filter chips.
- The list is grouped by day, with sticky day headers and day totals. A "Scheduled" pill marks payments made against bills and EMIs. FAB.
- A second mode, **"Expenses with no account"**, opened from Home:
  - an explanation at the top,
  - expenses from every month, with full dates and ACCOUNT UNKNOWN pills,
  - an empty state reading "Every expense has an account".

**C3 Recent activity.**
- The last 10 money movements of every kind: expenses, income receipts, settlements, transfers and goal entries, each with a kind icon.
- Transfers and goal entries are unsigned. Card purchases are marked "On card".
- Tapping a row opens its editor.
- The delete sheet explains what goes with the record, for example "Undoing this bill payment returns it to the forecast."

**C4 Import from SMS.**
- **Step 1:** a large paste box titled "Paste a bank message", with the hint "Several at once is fine, with a blank line between them", and a "Read it" button. A privacy note: "The app never reads your messages."
- **Step 2:** review. Each parsed message becomes a checkable card:
  - amount, debit or credit, date, merchant, the account's last four digits, and a category picker;
  - possible duplicates arrive flagged and unticked, with the reason shown;
  - "Show message" expands the raw text;
  - "Select all";
  - an import button showing the count.
- Nothing is saved until the user has reviewed it.

**C5 Search.**
- A search field with results grouped into Expenses, People, Loans and Bills, matches highlighted.
- Empty state: "Nothing found".

### D. Income

**D1 Income.**
- **This month:** expected and received receipts, with RECORDED, UPCOMING and OVERDUE pills.
- **Sources:** a list such as "Salary · ₹85,000 · monthly on the 1st" and Freelance.
- Actions to add a source and add a receipt.
- Empty state: "No income added yet".

**D2 Income source editor.**
- Name and amount.
- Type chips: Salary, Freelance, Business, Bonus, Incentive, Rental, Interest, Other.
- Day of the month and frequency.
- Start and end dates.
- Yearly increment %.
- The account it is paid into.

**D3 Income receipt editor.**
- Amount on the keypad, date, linked source, the account it was received into, and notes.
- A period note, for example "Counts as October's salary".

### E. Accounts

**E1 Accounts.**
- **Total across accounts:** "as of" date, with a stacked bar split by account.
- **Account cards:** HDFC Savings, Cash and Paytm Wallet, each with its balance and recent movements. An "Unassigned" row appears when some records have no account.
- **Move money, Withdraw cash, Deposit cash:** sheets with from, to, amount and date.
- An editable transfers list.
- A balance-corrections row, shown only when a correction exists.
- A "Close account" dialog.
- **Correct my balance:** a reconciliation sheet, opened from Settings › Your balance. It shows the computed balance next to the actual one, and records the difference as an adjustment rather than rewriting history.

### F. Plans and commitments

**F1 Your plans.**
- Header: "Manage your EMIs and upcoming commitments."
- The monthly EMI total.
- Active loans with progress bars, for example "38 of 240 paid · 202 left · finishes 2043".
- Links under "Other commitments": Recurring bills, Credit cards and Yearly expenses, each with this month's total.

**F2 EMIs and loans.** The loan list, plus a "Fully repaid" section.

**F3 Loan detail.**
- A progress ring.
- Loan details: installment, amount borrowed, rate, start date, first due date and a masked account number.
- Interest details: interest over the loan, interest still to pay.
- **Installment schedule** with Mark paid and undo on each installment: paid shows RECORDED, the next one UPCOMING, a missed one OVERDUE.
- **Pay off sooner:** enter an "Extra each month" amount to see months saved and interest saved, with a before/after mini chart.
- Edit and delete.

**F4 Loan editor.**
- Loan name ("For example, Bike EMI or Home loan") and installment amount.
- Optional: amount borrowed and interest rate.
- Loan start date, first installment due date, frequency, total installments.
- Installments already paid ("if this loan started before you began using the app").
- Loan account number (optional).

**F5 Credit cards.**
- Cards drawn as physical cards: a neutral gradient with the bank name as text, never a logo.
- "LAST STATEMENT ₹12,340".
- "Charged since then ₹4,560 · 6 purchases".
- A utilisation bar (statement plus purchases since).
- A cycle timeline running from cycle start to statement day to due date, with a today marker.
- "Due in 23 days".

**F6 Card editor.** Card name, bank, last four digits (optional), credit limit, current outstanding, minimum due, statement day, due day, active.

**F7 Recurring bills.**
- A "Bills due this month" summary.
- Rows with due chips ("Due today", "Due in 3 days", OVERDUE).
- Fixed amounts and ESTIMATED ones (a range such as ₹2,100–₹2,800 with an ESTIMATED pill).
- **Mark paid** records the payment as a linked expense.

**F8 Bill editor.**
- Bill name and category.
- Amount type: Fixed, or Estimated with a min–max range ("Actual amount when you pay").
- Due day and frequency (Weekly, Monthly, Quarterly, Half yearly, Yearly).
- Start date and an optional end date.
- Payment method, auto-debit toggle.
- Essential toggle: "Essential bills count towards your emergency fund target."

**F9 Yearly expenses ("Across the year").**
- A 12-month strip that marks the months carrying a yearly payment.
- Rows such as "Car insurance · ₹14,200 · 5 Dec".
- A suggested monthly reserve.

**F10 Yearly expense editor.** Name, amount for the year, due month and day, category, linked vehicle, active.

### G. People

**G1 People.**
- **Overall:** Owed to you ₹3,500, You owe ₹2,000, and the net.
- People rows with monograms and their net position, or an "All settled" state.
- Actions: Add person, Split a bill.
- Empty state: "Add the people you lend to, borrow from, or split bills with."

**G2 Add person.** Name, relation and phone (optional).

**G3 Person detail.**
- A net-position hero with a "What this means" explainer.
- Totals in each direction.
- An entries list (money lent, money borrowed, shared bills) with expected dates and OVERDUE pills.
- A settlements list.
- **Record a payment** sheet:
  - amount, then method (only methods that move cash), account and date;
  - a live line: "Still owed after this: ₹X";
  - a note when it would overpay.
- **Nudge:** a polite reminder preview handed to the system share sheet.

**G4 Add entry.** Direction (Money lent or Money borrowed), amount, date, "Expected to be settled by", and a note.

**G5 Split a bill.**
- Amount.
- Who paid: "I paid" or another person.
- Participants as monogram chips.
- How to split, as a segmented control: Split equally, Custom amounts, By percentage, One person pays all.
- "Each share" updates live, remainder included (₹1,000 ÷ 3 = ₹333.34 + ₹333.33 + ₹333.33).
- Category and date.

### H. Saving and limits

**H1 Savings goals.**
- Goal cards with progress rings: saved against target, "Needed each month ₹X to reach it by Dec 2026", and a priority chip (High, Medium or Low).
- A "Goal reached" cookie badge.
- Common goal suggestions on the empty state.

**H2 Goal editor.** Name ("For example, New phone, Bike, or Vacation"), target amount, an optional target date, priority, notes.

**H3 Goal detail.**
- A big ring with Saved so far and Still to save.
- A history of entries (Added and Taken out).
- **Add to this goal** sheet: direction (Add or Take out) and a positive amount.
- An explainer: "Money in a goal is set aside, not spent. It stays in your balance."

**H4 Emergency fund.**
- **"How much cover do you want?"**: a months stepper with the hint "Most households aim for three to six months".
- **"What one month costs you"**: Everyday essentials, Essential bills, Loan installments, Monthly share of insurance. Caption: "Built from what you have recorded, not from an average household."
- Target ₹1,80,000, saved ₹70,000, ₹1,10,000 to go, shown on a "months of cover" ring.
- An "Add to your fund" sheet.
- States: in progress, Fully funded, "add your bills first".

**H5 Budgets.**
- An explainer: "Budgets count what you spend, however you paid for it, including by card."
- Budget cards, each with:
  - a linear bar with a pace marker,
  - a ghost projection ("At this pace you will finish the month around ₹9,100"),
  - a state: On track, Close to the limit, On pace to go over, or Over budget.
- Carry-forward arithmetic as three lines: Monthly budget ₹3,000, Carried forward +₹400, Available this month ₹3,400.
- **Set a budget** sheet: "Applies to" (Everything, or a category), monthly limit, a "Carry unspent money forward" toggle, and an alert threshold slider.
- Empty state: "No budgets set".

### I. Understand

**I1 Ask about your money.**
- A chat layout headed "Ask about your money · Works offline, answers from your records".
- "Try asking" chips drawn from the user's own data, for example "How much can I spend this week?" and "Who owes me money?".
- Answers arrive as cards with figures and an action button ("See the forecast", "Open people").
- An "Ask by voice" button.
- An honest no-match reply that lists what it can answer.

**I2 Can I afford it?**
- "What are you thinking of buying?" on the amount keypad.
- A horizon choice: "Check against the next N months".
- **Result:**
  - a verdict card: comfortable, tight, or dips below zero;
  - "Left immediately after buying";
  - "Lowest point you would reach", shown as a runway mini chart with the comfort band.
- **How this was worked out:** Money available now, Expected to come in, Payments already committed, Everyday spending ("Estimated from your own history"), This purchase.
- Footer: "This is a comparison of your own numbers, not financial advice."

### J. Household

**J1 Vehicles.** Vehicle cards (Bike, Scooter or Car icon, name, registration) with this month's costs. Add vehicle.

**J2 Vehicle editor.** Type chips, name, registration, notes.

**J3 Family.** Members with relation chips (Self, Wife, Husband, Son, Daughter, Father, Mother, Brother, and so on) and each member's spending this month. An "Add family member" sheet.

### K. Settings and data

**K1 Settings.** One scrolling screen of grouped cards:
- **You:** name.
- **Appearance:** Follow system, Light or Dark.
- **Planning:** "How far ahead to forecast", and emergency fund cover.
- **Reminders:** "Remind me about payments", How early, Time of day, and which kinds (EMIs, Bills and yearly expenses, Credit card dues, Money owed by people), plus "Weekly summary on Monday".
- **Security:** app lock with PIN, Change PIN, Use biometrics. When the lock is off, show the warning "Anyone with your phone will be able to open your financial records."
- **Your balance:** Correct my balance.
- **Your data:**
  - a Backup status card ("Because this app is the only copy of your records") showing the last backup and a health level;
  - Back up now;
  - Automatic backup ("Back up weekly" to a folder the user picks, with Change folder);
  - Restore from a backup;
  - Export to Excel, Export expenses as CSV, Export this month to PDF;
  - Import expenses from CSV.
- **Start again** (destructive): Clear all my records, and Reset the app completely.
- **About:** "Works entirely offline. No internet permission, no account, no analytics."

**K2 Categories.**
- Expense and income categories, each with an icon and colour.
- An Essential toggle: "Essential categories count towards your emergency fund target."
- A New category sheet with name, icon picker and colour.
- Categories already in use are hidden rather than deleted, and can be restored.

**K3 Dialogs and sheets.**
- **Restore this backup?**
  - When it was taken, and an "It contains:" list of counts (expenses, income, people, loans, bills, goals, budgets, accounts).
  - Warning pills: "This backup is empty" and "This backup is from an older version that did not save budgets."
  - Button: "Replace everything".
- **Export to Excel:** a period picker (This month, Last month, This year, Everything, Custom range).
- **Choose a PIN:** enter, then confirm.
- **Turn off app lock?**
- **Clear all your records?** and **Reset the app completely?**: the user types DELETE to confirm, with a "Save a backup first" option.

### L. Outside the app

**L1 Home-screen widget.**
- 2×1 and 4×1 sizes showing "Available to spend ₹41,250" ("Available now" when nothing is set aside). Tapping it opens the app.
- **Locked state** when the app lock is on: "Locked · Open app to view balance", with no amount.
- Light and dark versions with a rounded, glass-like surface.

**L2 Notifications.**
- Examples: EMI due tomorrow; Bill due today (estimated); Card bill due in 3 days; Money expected from Rohan is overdue; a Monday weekly summary ("₹42,340 due in the next 7 days").
- Also design a private variant without amounts, for when the app lock is on. The app does not do this yet, so mark it as a recommendation.

---

## PART 3 — BATCH PROMPTS

Paste Part 1 first, then send one batch at a time. Each batch should end with every listed screen in Light and Dark, in the states Part 2 lists.

1. **Foundations and components.** "Using the master brief, build the Cover, Foundations (variables in Light and Dark, type, spacing, radius, elevation and glass, icons, motion notes) and every component listed, with variants and auto layout."
2. **Getting in and navigation.** "Design A1 Lock screen and all five A2 Onboarding steps, plus the bottom dock and FAB in context. Prototype onboarding → Home."
3. **Home.** "Design B1 Home in all its states (populated, brand-new install, negative balance, loading), plus the L1 widget."
4. **Forecast and calendar.** "Design B2 Forecast (including the December shortfall) and B3 Money calendar (current month, a past month, and December opened from the shortfall link). Prototype Home → Forecast → shortfall → Calendar."
5. **Reports and More.** "Design B4 Reports and B5 More."
6. **Recording money.** "Design C1 Add or edit expense (keypad, card picker, more details, voice states, delete), C2 All transactions (both modes), C3 Recent activity, C4 Import from SMS (paste and review) and C5 Search. Prototype adding an expense."
7. **Income and accounts.** "Design D1–D3 and E1, including the Move money, Withdraw, Deposit and Correct my balance sheets."
8. **Plans.** "Design F1–F10: Your plans, EMIs and loans, Loan detail with Pay off sooner, the loan, card, bill and yearly editors, Credit cards with the cycle timeline, Recurring bills with Mark paid, and Yearly expenses."
9. **People.** "Design G1–G5, including the Record a payment sheet and the Nudge share preview. Prototype recording a payment."
10. **Saving and limits.** "Design H1–H5, including the Goal reached badge, the emergency fund stepper and the Set a budget sheet."
11. **Understand and household.** "Design I1 Ask about your money, I2 Can I afford it?, and J1–J3."
12. **Settings and finish.** "Design K1 Settings, K2 Categories, the K3 dialogs and L2 notifications. Prototype restoring a backup through its preview. Then do a final pass: dark mode, 200% font, contrast, naming and handoff notes."

### Final checklist

- [ ] Every figure that is not recorded carries one of the six confidence pills, with icon, word and colour.
- [ ] Home leads with Available to spend; total and set-aside figures sit beneath it.
- [ ] Past is solid, future is translucent, and today is the horizon, on every chart and timeline.
- [ ] All money uses Indian grouping and tabular figures, and negatives are shown with a minus sign.
- [ ] Nothing suggests login, cloud, sync or AI.
- [ ] Every list has empty, loading and populated states, and every editor has a sticky save showing the amount.
- [ ] Light and dark are both designed, contrast passes, touch targets are at least 48dp, and screens survive 200% text.
- [ ] Components use auto layout and variables, frames are named consistently, and the prototype flows run end to end.
