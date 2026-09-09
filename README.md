# Personal Money Planner

An offline-first Android app for Indian household finances. It does not only record what
was spent — it projects what the user will actually have left in each of the coming months,
built entirely from figures they have entered themselves.

## Building

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

Requires JDK 17+ (the Android Studio bundled JBR works) and Android SDK platform 35.
`local.properties` must point at your SDK.

| Item | Value |
| --- | --- |
| Language | Kotlin 2.1.20 |
| UI | Jetpack Compose, Material 3 |
| Min / target SDK | 26 / 35 |
| Database | Room 2.7.1 |
| DI | Hilt 2.56.2 |
| Background work | WorkManager 2.10.1 |
| Release APK | ~3.3 MB (minified, resources shrunk, Inter bundled) |

## Privacy posture

The app declares **no `INTERNET` permission**, so it cannot phone home even by accident.
There is no account, no sync, no analytics. `allowBackup` is off and cloud/device-transfer
backup rules exclude every domain, so financial records are not swept into a Google
backup. The only way data leaves the device is the user explicitly exporting it through
the system share sheet.

The app asks for no SMS access at all. Bank alerts are imported by pasting them, which
costs a copy and a tap and keeps the promise that an app holding this much financial
detail never reads your messages.

The merged manifest declares exactly these permissions:

| Permission | Declared by | Why |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | this app | Payment reminders. Requested only once reminders are switched on. |
| `RECEIVE_BOOT_COMPLETED` | this app | Re-register the daily reminder after a restart. |
| `USE_BIOMETRIC` | this app | Optional biometric unlock. |
| `USE_FINGERPRINT` | androidx.biometric | Legacy fallback for the above. |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | WorkManager | Pulled in transitively by the scheduler. `ACCESS_NETWORK_STATE` only reads connectivity status; it does not grant network access. |

Verify for yourself at any time:

```bash
grep -o 'uses-permission[^/]*' app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml
```

## Design system

The UI implements the **Bharat Wealth Framework** from the original design brief. That
folder is not part of this repository; the tokens it specified live in `ui/theme/`:

| Token | Value | Where |
| --- | --- | --- |
| Primary | Deep Navy `#000666` | `Theme.kt` |
| Page surface | `#F8F9FA`, cards pure white | `Theme.kt` |
| Card border | Soft Blue `#E8EAF6`, 1px hairline | `MoneyColors.cardBorder` |
| Elevation | `0 2px 8px rgba(26,35,126,.05)` / `0 8px 24px …` | `Elevation.level1/2` |
| Card radius | 24dp; controls 8dp; FAB pill | `MoneyShapes` |
| Page margin | 20dp; card padding 16dp; section gap 24dp | screen `contentPadding` |
| Semantic | Emerald `#065F46`, Amber `#B45309`, Rose `#BE123C` | `MoneyColors` |
| Type | Inter 400/500/600/700 | `Type.kt` |

**Inter is bundled**, not fetched — the app has no internet permission, so a downloadable
font would never arrive. It ships as the variable font and each weight is derived from the
`wght` axis, which adds ~400 KB to the release APK (2.8 MB → 3.2 MB). The SIL Open Font
License is included at `assets/inter_OFL.txt` as redistribution requires.

Every money style sets the `tnum` font feature, so a column of rupee figures aligns digit
for digit instead of shuffling as values change. The rupee symbol is rendered a weight
lighter than the digits, per the framework's currency rule.

Semantic colours are held outside the Material scheme in `MoneyColors` and dynamic colour
is deliberately not offered: a wallpaper-derived palette would repaint "money in" and
"money out" in arbitrary hues, and those two colours carry meaning here, not decoration.

### Bottom navigation

Home · History · Plans · Goals · More, matching the design. People and shared expenses sit
inside More rather than on the bar — the bar holds what a person opens most days, and a bar
that holds everything stops being navigable at a glance.

### Screens built to the reference designs

- **home_dashboard** → greeting with avatar, Available Now hero with quick-action chips,
  income card, spent/saved pair, forecast card with plain-language verdict
  (Comfortable / Careful / Tight) and commitment bar, Coming Up list.
- **money_ahead_forecast** → month chip row, Expected Remaining hero, money-in/out split
  bar with paired tiles, Expected Breakdown with semantic icon washes.
- **add_expense** → hero amount card focused on open at display size, form card, person
  chips, save button anchored in the thumb zone.
- **emi_plans** → Your Plans header, Monthly EMI summary, active commitments with progress
  and "N left", plus links through to bills, cards and yearly expenses.

The **Add with Voice** button shown in the add_expense design is now built — see Phase 5.

## Phase 5

**Voice expense entry.** Speech goes through the system recogniser via
`ACTION_RECOGNIZE_SPEECH`, so the app needs neither `RECORD_AUDIO` nor network access of
its own. `SpokenExpenseParser` is a rule-based parser, not a model: it reads the amount
(including "2 thousand" and "1 lakh"), matches a category against the user's *own*
category list before falling back to everyday synonyms, picks up a payment method, and
understands "yesterday".

It never saves. It fills the form and shows back both what was heard and what it made of
it — speech mishears numbers, and a wrong amount written straight to the ledger is a
corrupted record. The user confirms.

**Money assistant.** `MoneyAssistant` answers questions from the same calculators the rest
of the app uses. It is a deterministic intent matcher, **not a language model**, and the
UI says so. That is a deliberate trade: it cannot hold a conversation, but it also cannot
invent a number that is not in your data, and it works offline and instantly. When nothing
matches it says so and lists what it can answer rather than guessing.

**Receipt scanning is not built.** It needs ML Kit, which pulls `INTERNET` into the merged
manifest through its Play Services dependencies — even though the OCR model itself runs
on-device. Rather than quietly weaken the offline guarantee, the choice was put to the
user, who chose to keep the app fully offline. Verified: the shipped release manifest
still has no `INTERNET` permission.

31 further unit tests cover the parser and the assistant, including that every
suggestion the assistant offers is one it can actually answer.

## Budgets, runway and automatic backup

**Automatic backup.** The most dangerous property of an offline-only app is that the
records exist in exactly one place, with the device cloud backup switched off precisely
because this is financial data. Pick a folder once through the system document picker and
a copy is written there every week, keeping the newest five. The folder grant is
persistable, so no storage permission is requested and the app can see nothing else on the
device. A folder that has gone away is reported rather than retried forever.

**Budgets.** The forecast says what will happen; a budget is how you change it. Limits are
monthly, either per category or one overall.

Budgets count differently from the forecast, deliberately:

| | Forecast | Budget |
| --- | --- | --- |
| Question | Will I have the cash? | Am I overspending? |
| Card purchase | Ignored until the bill is paid | Counts on the day it is made |
| Rent paid from a scheduled bill | Counted once, as the bill | Counts against the rent category |

`BudgetCalculator` also projects where the month lands at the current pace, so a warning
arrives *before* the limit is breached rather than after the money is gone.

**Runway.** `RunwayCalculator` walks the balance forward a day at a time instead of only
reporting month ends, because a month can close comfortably while rent, an EMI and a card
bill land together in the week before payday. It reports the date money runs out, the date
it dips below a comfort floor, and the lowest point reached. Everyday spending is spread
evenly across the days rather than dropped on any one, since nothing in the data says when
it happens.

### Schema migrations

The database is at **version 5**, with four migrations. Every statement is copied from the
schema Room itself exported, so the result is byte-identical to a fresh install; Room
validates that on open and refuses to start if a migration produces anything different.
All four schema versions are exported to `app/schemas`.

| Version | Change | Notes |
| --- | --- | --- |
| 1 → 2 | Budgets | Purely additive. No existing table is touched. |
| 2 → 3 | `linkPeriodKey` on expenses; foreign key on shared shares | Existing rows are backfilled by matching each linked expense to the payment recorded on the same day, so history written earlier can still be undone one period at a time. The shares table is rebuilt rather than altered, because SQLite cannot add a foreign key in place. |
| 3 → 4 | Transfers between the user's own accounts | Purely additive. A database with no transfers describes exactly the position it did before. |
| 4 → 5 | Expense tags and receipt attachment metadata | Tags are reusable labels; receipts retain only the document-picker URI, not a copied image. |

Room is deliberately **not** given a destructive fallback: a missing migration must fail
loudly rather than quietly delete a financial history that cannot be recreated.

## SMS capture, onboarding, prepayment and insights

**SMS auto-capture.** The single biggest reduction in manual entry. `BankSmsParser` reads
bank alerts into draft transactions — amount, debit or credit, date, merchant, account
tail — across the formats the major Indian banks actually send. It is rule-based and
therefore predictable, and 31 tests across two suites hold it to two properties that matter
more than coverage: an amount is never wrong, and a message that is not a transaction is
never treated as one. The second suite replays real alert formats from the banks people in
India actually use, which is how the parser was caught rejecting genuine SBI debits. OTPs, due-date reminders, promotional offers and balance replies are all
recognised and discarded.

Nothing imports automatically. Every candidate is reviewed, the raw message sits one tap
away under each row, and anything that looks like an existing expense arrives
flagged and unticked — a duplicate is worse than a miss, because a miss is visibly absent
while a duplicate quietly makes the balance wrong. Matching allows a day either side, since
an alert can arrive after midnight for a purchase made before it, and the same message
pasted twice is caught within the batch.

Import is by pasting, and the app asks for no SMS permission at all. Several messages can
go in at once, separated by blank lines, which is how a batch arrives when someone catches
up on a week.

> Reading the inbox behind `READ_SMS` was removed. Play grants that permission almost
> exclusively to default SMS handler apps, so it blocked distribution for a feature that
> works without it, and an app trusted with this much financial detail is stronger for
> never asking to read your messages.

**Onboarding.** Four steps — name, balance, income, fixed outgoings — every one skippable.
It exists because a new user previously landed on an empty dashboard showing zero and had
to find Settings unaided, which is the moment most people decide an app does not work.
Anything entered goes through the ordinary repositories, so a setup salary is an editable
income source like any other, and the opening balance is recorded as a visible
reconciliation rather than a hidden special case.

**Prepayment planner.** On the loan screen, because that is where the decision is made.
Type an extra monthly amount and it shows the months and interest saved. The baseline is
derived analytically rather than simulated: carrying on unchanged means paying exactly the
installments that remain, so the months are known by definition. Replaying it instead
produced a rounding residue that added a phantom final month and understated every saving
by one.

**Insights.** The app held months of history and never once remarked on it. It now notices
a category well above its own recent average, a loan nearly finished, a card near its
limit, and whether more went out than came in — each with a stated threshold, and each
staying quiet until there is enough history to justify it. It reports good news too, since
an app that only ever flags problems stops being read.

**Nudge.** A pending balance can be turned into a polite message handed to the system share
sheet. The app never sends anything, has no contacts access, and needs no permission.

Settle-up simplification was considered and dropped. In a two-party ledger it does not
work: your friend owing you cannot cancel what you owe someone else without a three-way
agreement the app has no way to represent.

## Accounts, and where the money actually sits

The dashboard answers "how much do I have". Accounts answer "and where is it", which is the
question that decides whether a payment will actually go through on Tuesday. Every figure is
derived from the same records as the overall balance, so the two can never disagree, and the
per-account rows plus anything unassigned always come back to the same total.

**Cash is an account of its own.** A fresh install is seeded with two: a bank account and
cash. They were one combined account in earlier versions, which could not express what an
ATM withdrawal actually is. The withdrawal either had to be ignored, losing track of the
cash, or recorded as an expense, which understates the balance until the cash is spent and
then counts it twice when it is. An install made before the split is given a cash account
once, at next launch; archiving it does not bring it back.

**Withdraw and deposit are named actions**, on the accounts screen alongside the general
"move money". All three write the same `AccountTransfer`, and a transfer is neither income
nor an expense: it changes both account balances and changes the total by nothing at all.
They are offered separately because nobody thinks of an ATM as transferring to their cash
account, and naming an action the way the user names it is the difference between a feature
being found and not.

**Settling with a person asks the same questions as any other payment.** Paying someone back
₹1,000 against a ₹10,000 debt moves real cash, so the sheet asks how much, by what method,
from which account and on what date, and shows what would still be owed afterwards. Only
methods that actually move cash are offered: settling on a credit card would drop the
balance with no account behind it, because card spending does not reduce cash until the
card bill is paid. Paying more than is owed is allowed rather than blocked — an overpayment
is a real event, and the allocator already reports it instead of absorbing it — but the
sheet says so before the payment is recorded.

**Every record says which account it belongs to.** Income is recorded against the account it
was paid into, and a recurring income remembers its account so each receipt defaults to the
right one. An expense defaults to the account its payment method implies — cash comes out of
cash, everything else out of the bank, and a card purchase out of neither, because it touches
no account until the bill is paid. The moment the user picks an account by hand the app stops
guessing.

Records written before accounts were tracked carry none, and are reported as **unassigned**
rather than quietly attributed to whichever account happens to be first. It is the difference
between what the user has and what they have told the app where to find, and showing it is
more honest than inventing a home for it.

### Recent activity

Every other list in the app is filtered to one kind of record: expenses in History,
receipts on the income screen, settlements under a person. That is the right way to answer
"what did I spend on food" and the wrong way to answer "what did I just enter", which is
what someone needs after typing a figure wrong.

**More → Recent activity** shows the last ten money movements of every kind together —
expenses, receipts, payments to and from people, transfers between accounts, and money set
aside into goals. Tapping a row opens its editor and anything can be deleted. Every record
is also editable where it already lives: receipts on the income screen, settlements on the
person's screen, transfers on the accounts screen, and goal entries on the goal's screen.

A goal entry asks for a direction and a positive amount rather than a signed figure. A
withdrawal is stored as a negative contribution, which is what keeps the history complete
instead of quietly erasing the deposit it reversed, but that is a storage detail and nobody
should have to type a minus sign to correct one.

A transfer keeps the wording it was created with. Correcting a bank-to-cash movement still
reads as a withdrawal rather than reverting to the generic "move money", because that is
what the person making it calls it. Editing is guarded exactly as recording is: an edit can
introduce the same two slips a new entry can, naming one account twice or a non-positive
amount, and both are refused.

Editing a settlement shows the balance **without** that settlement, not the balance as it
stands. The live balance already has the payment taken off it, so offering that figure
while the user edits the very payment that produced it would describe a debt reduced twice
and make every "what would still be owed" line beneath it wrong.

Editing a receipt keeps the month it already settles. A salary due on the 1st and credited
on the last day of the month before is stamped with the month it was *for*, not the month
it landed in, and recomputing that stamp from the date whenever the record was touched
would hand it back to the wrong month and leave the forecast expecting a salary already in
the bank. An existing receipt therefore keeps its stamp until the user actually re-dates it
or points it at a different income. `IncomeReceiptPeriod` holds that rule, and seven tests
hold it in place.

Deleting is where the care goes. Each row is removed through whichever repository owns the
*event*, not whichever table happens to hold the row:

| Row | What deleting it actually does |
| --- | --- |
| Plain expense | Deletes the expense. |
| Bill, EMI or yearly payment | Undoes that one period through the obligation, so the payment record goes too and the occurrence returns to the forecast. |
| Shared bill | Deletes the shared expense, taking the split and every share it created with it. |
| Settlement | Removes the payment; the balance with that person goes back up. |
| Transfer | Returns the money to the account it came from. |
| Goal contribution | Drops the goal's progress by that amount. |

Deleting a linked expense on its own would leave the payment record behind: the forecast
would still believe the bill was settled while the money returned to the balance, which is
precisely the double count the rest of the app is built to prevent. The confirmation says
what else goes with the record before anything is removed.

A transfer and a goal contribution are shown without a sign, because neither changes the
total — one moves money between the user's own accounts, the other earmarks it. A card
purchase is marked "On card", since it is recorded on the day it happens but takes no cash
until the bill is paid.

### Credit cards

A purchase paid by card asks which card, and the card screen shows what has been charged to
it since its statement figure was last entered.

The outstanding on a card is deliberately **not** derived, unlike every other balance in
this app. It is the figure the user copies from their statement, because only the bank knows
what interest and fees it has added. That makes it the one place where recorded purchases
cannot simply be added to a balance: a statement of ₹20,000 already contains everything
charged before it was issued, so summing every card expense on top would charge those
purchases twice.

`CreditCardCalculator` therefore keeps the two apart and reports both — the statement figure
the bank gave you, and what has gone on the card since — rather than merging them into a
single number that would be wrong. Utilisation and the available limit use the total of the
two, because that is what the card really has on it today.

### Exporting to Excel

**Settings → Export transactions to Excel** writes a real `.xlsx` workbook for a date range,
with presets for this month, last month, this year and everything. One sheet per kind:
expenses, income, settlements, transfers, savings and card bills. A kind with nothing in the
range is left out rather than written as an empty tab.

Amounts are written as numbers and dates as date serials, which is the whole reason for
preferring a workbook to a CSV: a column of amounts can be summed and a column of dates can
be sorted and filtered. `₹1,500` as text does neither.

`XlsxWriter` emits the format directly rather than pulling in a library. An `.xlsx` is a zip
of XML parts, and the subset needed for tabular data is small. Apache POI would have taken
the release APK from 3.3 MB to roughly 15 MB; writing it costs about 10 KB. There are no
charts, formulas or images, which is where the format gets genuinely hard.

Verified end to end: the file the app produces on a device opens in a spreadsheet reader
with amounts as numbers and dates as dates.

## What changed after the first release

**Today refreshes itself.** Everything dated in this app is relative to today, and the
snapshot used to capture that date only when a record changed. A phone left on the
dashboard overnight kept yesterday's answer, quietly showing a payment as upcoming on the
morning it became overdue. `TodayProvider` now emits at midnight and on the system clock
and time zone broadcasts, and the snapshot takes it as an input like any other. Verified on
a device: advancing the clock past midnight with the app untouched moves the header on and
recomputes the forecast, because everyday spending is projected over the remaining days.

**Budgets carry forward.** Rollover and a per-budget alert threshold. Only unspent money
carries; an overspend is not carried as a debt, because that turns one bad month into a
limit the next month cannot meet either, and a budget nobody can hit stops being read. The
carry compounds across quiet months, is wiped by an overspend, reaches back no further than
a year, and never before the budget existed.

**Reports compare.** A single month says what happened, not whether that is normal. There
are now three comparisons: income against spending month by month, each category against
its own recent average, and what flowed through each account. A category with no history
reports no comparison rather than being measured against a zero, the current month is kept
out of its own baseline, and a month still running is flagged, since on the 3rd it always
looks like spending stopped.

**Credit cards know their cycle.** When the open cycle started, when the statement is cut,
and when that bill falls due — worked out from the statement and due days rather than
assumed. The projected bill is now the statement figure plus anything charged since, both
already recorded, which also fixes reminders staying silent on a card paid off and then
spent on again.

**Backups say how exposed you are.** The status sits above the buttons, since this app is
the only copy of these records. It stays quiet on an install with almost nothing in it,
because warning somebody with four expenses teaches them to ignore the warning by the time
it matters. Restoring now reads the file first and shows when it was taken and what is in
it, while the current records are still there to compare against.

**SMS import is paste-only.** `READ_SMS` is gone. Play grants it almost exclusively to
default SMS handler apps, so it blocked distribution for a feature that works without it.
Several messages can be pasted at once. The parser covers more of what the major Indian
banks actually send, and duplicate detection now allows a day either side, because an alert
can arrive after midnight for a purchase made before it.

**The assistant knows more and asks better.** Six new intents, and suggested questions now
come from the snapshot rather than a fixed list. Offering "who owes me money" to somebody
with no people recorded gets the honest answer "nobody", which teaches them the assistant is
not worth asking.

**The calendar shows the past too.** It was built from the forecast alone, which projects
forward from the current month, so any earlier month came back empty and the screen said
"nothing is scheduled" about a month that plainly had spending in it. A day now carries
what happened, taken from the records, alongside what is expected, taken from the forecast,
and keeps the two apart. An unpaid item whose day has passed is marked overdue, because
rent due on the 5th looked identical on the 9th to one due next week.

**Setup ends with a forecast.** Onboarding used to drop the user on a dashboard having asked
for a balance, a salary and their commitments without ever showing what those imply. The
last question now leads to a review, with the answers saved first so the figures come from
the same calculators the dashboard will use a moment later.

## Architecture

```
Compose screen → ViewModel → Repository → Room
                     ↓
              Calculators (pure Kotlin)
```

One Gradle module, strictly layered by package. `domain/calc` has no Android dependency at
all, which is what lets the entire financial engine be unit tested on the JVM without an
emulator.

- **`core/`** — `Money` and Indian formatting, date helpers.
- **`domain/model`** — domain types and `FinancialSnapshot`.
- **`domain/calc`** — the calculators. Pure functions, no I/O, no clock access.
- **`data/`** — Room entities, DAOs, repositories, mappers, backup.
- **`ui/`** — design tokens, shared components, 41 screens across 54 routes.

`SnapshotRepository` combines every table into a single `FinancialSnapshot`. Calculators
are pure functions of that snapshot, so the same data always produces the same numbers and
a screen never shows a half-updated position.

### Money is never a floating point number

Every amount is an exact `Long` count of paise inside a `Money` value class. `splitEvenly`
distributes the remainder one paisa at a time, so splitting ₹1,000 three ways yields
33,334 + 33,333 + 33,333 paise and never loses a paisa.

### Dates

All schedule logic goes through `RecurrenceCalculator` and `DateUtil.dayInMonth`. A payment
set for the 31st resolves to the 28th or 29th in February and **returns to the 31st**
afterwards, rather than being permanently clamped. Leap years come from `java.time`.

## The three rules that keep the forecast honest

These are the load-bearing decisions. Each is implemented in exactly one place and pinned
by tests.

**1. An obligation is counted once.**
A paid bill is already inside the balance, so only unpaid occurrences are projected. The
expense created when it was paid carries a link back to the bill, and linked expenses are
excluded from everyday-spending averages — so rent can never appear both as a scheduled
bill and as projected discretionary spending.

**2. A credit card purchase is not a cash outflow.**
`PaymentMethod.CREDIT_CARD` is the one method with `reducesCashImmediately = false`.
Card spending raises the card outstanding; cash leaves when the card bill is paid. Only
the card due is projected, so the same rupee is never subtracted twice.

**3. The current month is partial.**
Money already spent and received this month is inside the opening balance, so only what
remains is projected on top of it — including everyday spending, which is projected for the
remaining days only.

Two further consistency rules: money moved into a savings goal is **earmarked, not spent**
(it stays in the balance and is reported separately), and a balance owed by a person with no
expected date is a real balance but is **not guessed into a month**.

### Derived, never stored

Balances, goal totals, person balances and EMI paid-counts are all recomputed from history
rather than kept as running totals. Deleting or editing an old record corrects everything
downstream automatically, with no repair step, and no figure can drift away from the
records that are supposed to explain it. `BalanceCalculator.breakdown()` returns the
components so the dashboard number can always be explained.

Correcting a balance does not overwrite history: the difference is written as an explicit
reconciliation adjustment.

## Tests

377 JVM unit tests, all passing, covering every calculator plus the voice parser,
the bank SMS parser, the assistant, budgets, runway, prepayment and insights:

```bash
./gradlew testDebugUnitTest
```

They pin the edge cases that break naive implementations: partial payments, overpayments
that push a balance past zero, missed installments, deleted and edited records,
future-dated transactions, early EMI payments, loan completion, negative balances, month
boundaries, leap years, 31st-of-the-month schedules, quarterly cycles that do not start in
January, and money moved between accounts rather than spent.

Two worked examples from the product brief are encoded as tests and reproduce exactly:
the September cash flow (₹19,000 remaining) and the emergency fund shortfall
(₹1,80,000 target, ₹70,000 saved, ₹1,10,000 to go).

`app/src/androidTest` holds 15 instrumentation tests: database integrity (cascade deletes,
restrict constraints, restore ordering), per-period payment undo, and replaying real
databases through every migration. All 15 pass on an API 37 emulator:

```bash
./gradlew connectedDebugAndroidTest
```

The exported schemas are registered as `androidTest` assets, because
`MigrationTestHelper` reads them from the test APK rather than from disk. Without that the
four migration tests fail on a missing file before they can test anything, which is how
they sat unrun for so long. `connectedDebugAndroidTest` also needs network on its first
run to fetch the test platform, so it cannot be run `--offline` from cold.

> `JAVA_HOME` must point at a complete JDK 17+. If it points at an Android Studio bundled
> runtime whose `lib` directory is incomplete, Gradle exits immediately with
> `could not open .../lib/jvm.cfg` and no other explanation.

## Feature coverage

All five phases from the brief are implemented: dashboard, income, expenses, categories,
people, shared expenses with four split types and a settlement engine, EMIs, recurring
bills, credit cards, annual expenses, savings goals, emergency fund, vehicles, family
tracking, monthly forecast, calendar, reports, search, "Can I afford it?", reminders,
multiple accounts with transfers between them, JSON backup and restore, CSV export, and a
PIN/biometric app lock.

Phase 5 adds voice entry and an on-device assistant; expenses can now carry tags and a
photo or PDF receipt attachment selected through the system picker. Settings exports a
printable monthly PDF statement, imports the app's expense CSV format, and the launcher
offers a privacy-aware available-balance widget. Receipt scanning is deliberately omitted
(see below).

## Known gaps

- **Not run on a device.** No emulator or physical device was available, so the app has
  been verified by compilation and unit tests only. The first thing to do is install the
  debug APK and walk through the flows.
- **80C tracker** remains on the list.
- **Receipt scanning** is not built, to keep the app free of any network permission.
- **The assistant only understands what it was taught.** It is a rules engine over a fixed
  set of intents, so a question phrased unusually may not match. It says so when that
  happens rather than guessing.
- **The snapshot's idea of "today" can go stale.** It is captured when the snapshot is
  assembled, so an app left open across midnight keeps yesterday's date until something
  changes and the flow re-emits. Low impact, but it is a real edge.
- **Future credit card spending is not projected.** Only the outstanding the user has
  recorded is. Guessing next month's card spending would be inventing a number.
- **Database is not encrypted at rest.** The PIN is stored only as a PBKDF2 hash with a
  per-install salt, and app data is private to the app sandbox, but adding SQLCipher would
  be the next step for a device that might be rooted.
- **Hindi and Gujarati** are not translated. All user-facing strings are English; the
  architecture is ready for extraction to `strings.xml` resources.
