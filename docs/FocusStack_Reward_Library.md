# FocusStack — Reward Library

**Library version:** 1.1  
**Updated:** 2026-10-07  
**Used by:** ChatGPT FocusDeck planner (FocusDeck v1.10.0, session `protocol_version: 2`; legacy countdown v1 remains supported).

## Purpose and edit rules

This is the **editable source of truth for the rewards I own, enjoy, and permit**. Update a row or add a reward whenever my preferences, equipment, physical capacity, or circumstances change. The planner reads the current library **before proposing rewards**; it never assumes that old session reward text overrides a newer library update.

- **Priority:** `P1` = preferred/default choice; `P2` = variety/occasional; `P3` = rare or conditional. Priority is a preference, **not a mapping to BeatScore or an entitlement**.
- **Use:** `Block`, `Session`, or `Both` specifies where GPT may assign it. Short blocks should not trigger long activities.
- **Suggested amount** is a range for GPT to choose *within*, not an amount that increases automatically with score. The planner may use a lighter amount or assign no reward.
- **Allowed:** `Yes`, `Conditional`, or `No`. I can change this field. Exclusions and forbidden rewards below remain binding until I explicitly revise them.
- GPT selects **per-block score thresholds, exact wording, eligible reward, and session gates**, taking task difficulty, available wall time, previous rewarded activity, fatigue, available equipment, and local clock time into account. FocusDeck only measures, evaluates, and displays the supplied text; it does not invent rewards or parse/count the workout described.

## 1. Physical activation

| Reward | Suggested amount | Priority | Use | Allowed | Notes |
|---|---|---|---|---|---|
| Pull-ups | 3–5 reps; occasionally 5–8 | P1 | Block | Yes | Submaximal, never to failure; avoid closely repeated sets |
| Bodyweight squats | 10–20; occasionally up to 30 reps | P1 | Block | Yes | Watch accumulated leg load |
| Reverse lunges | 10–20 total reps | P2 | Block | Yes | Moderate lower-body effort |
| Calf raises | 20–40 reps | P2 | Block | Yes | Light/moderate, adjust if sore |
| Shadow boxing | 30–90 seconds | P2 | Block | Yes | Keep controlled and non-exhausting |
| Stair climbing / stair lap | 1–2 minutes | P2 | Block | Yes | Account for recent exercise |
| Hand gripper / grip trainer | 30–60 seconds | P2 | Block | Yes | Avoid overuse after pull-ups |
| Single-leg balance challenge | 30–60 seconds | P2 | Block | Yes | Clear safe space |

## 2. Physical reset and recovery

| Reward | Suggested duration | Priority | Use | Allowed | Notes |
|---|---|---|---|---|---|
| Brisk indoor walk | 1–3 minutes | P1 | Block | Yes | Useful after sedentary focus |
| Balcony / outdoor fresh air | 2–5 minutes | P1 | Both | Yes | Weather/access permitting |
| Mobility flow | 1–2 minutes | P1 | Block | Yes | Gentle, recovery-oriented |
| Shoulder / neck mobility | 1–2 minutes | P1 | Block | Yes | Gentle range of motion |
| Full-body stretching | 1–3 minutes | P2 | Block | Yes | Comfortable, no forced stretches |
| Cold water face splash | ~30 seconds | P2 | Block | Yes | Optional if enjoyable |
| Water refill + short walk | 1–2 minutes | P1 | Block | Yes | Useful default low-load reward |
| Longer outdoor walk | 10–20 minutes | P1 | Session | Yes | Mainly independent session reward |

## 3. Tangible achievements

| Reward | Exact action | Priority | Use | Allowed | Notes |
|---|---|---|---|---|---|
| Physical token | Move **one** coin/chip into the EARNED container | P1 | Both | Yes | Can pair with a modest physical reset |
| Progress marker | Add one marker to the physical tracker | P1 | Both | Yes | Avoid duplicating for same achievement |
| Personal-best card | Write/display the new BeatScore on a card | P1 | Both | Conditional | Only after a **real, comparable personal best** |
| Origami / paper star | Make one small physical achievement marker | P2 | Both | Yes | Requires only a short break |
| Special PB token | Move one distinct PB token | P1 | Both | Conditional | Only after a **real, comparable personal best** |

## 4. Guitar

| Reward | Amount | Priority | Use | Allowed | Notes |
|---|---|---|---|---|---|
| Favorite riff | 2–3 minutes | P1 | Block | Yes | Significant blocks, not every block |
| Short guitar lick | 3–5 minutes | P2 | Block | Yes | Avoid interrupting next task |
| Short guitar break | 5 minutes | P2 | Both | Yes | Reserve wall time |
| Guitar session | 10–15 minutes | P1 | Session | Yes | Independent session reward |

## 5. Food and drink

| Reward | Amount / condition | Priority | Use | Allowed | Notes |
|---|---|---|---|---|---|
| Special tea | One serving, occasionally | P2 | Both | Yes | Consider caffeine content |
| Sparkling water / special drink | One small serving | P2 | Block | Yes | Non-alcoholic; avoid treating hydration as conditional on performance |
| Small chocolate / fruit snack | One small portion, occasionally | P3 | Both | Yes | Optional, not a default |
| Coffee | One appropriate serving **after ~3–4 productive focus hours** | P3 | Session | Conditional | Default local cutoff **16:00**, subject to bedtime, spacing, last caffeine, and individual limits; never automatic |

For coffee: if timing, prior intake, tolerance, or recent sleep needs are unknown, **omit it** rather than guessing. A high BeatScore on a short session does not make coffee eligible. Water and necessary meals are never withheld as rewards.

## 6. Optional candidates (not core)

| Reward | Amount | Priority | Use | Allowed | Notes |
|---|---|---|---|---|---|
| Hot shower | 5–10 minutes | P3 | Session | Conditional | Only when practical |
| Brief cool-water rinse | Short, comfortable rinse | P3 | Session | Conditional | Only if enjoyable |
| Electronics tinkering | 5–15 minutes | P3 | Session | Conditional | Only if it will not derail next work |
| Eyes-open relaxation / quiet rest | Brief | P3 | Block | Conditional | Must be distinct from pre-work eye-mask ritual |

## 7. Digital feedback — NOT externally supplied rewards

FocusDeck may display BeatScore; ranks C/B/A/S/S+; planned vs actual duration; time ahead/behind target; Focus Ratio; PB and benchmark comparisons; session score; on/ahead count; weighted session performance. Stopwatch feedback includes actual active/paused time, Focus Ratio and completion; time scores are N/A. Cumulative progress and streaks are separate from rewards. Native app settings control supported layout and motion; GPT reward text does not restyle the UI. These are **app feedback metrics**, not physical rewards, and GPT must not encode them as fake reward actions. The actual application owns its display implementation.

## 8. Compatible combined rewards

- 5 submaximal pull-ups **and** move one physical token.
- 15 squats **and** a 90-second walk.
- 60-second light shadow boxing **and** 2 minutes of outdoor air.
- 3–5 submaximal pull-ups **and** a 2-minute outdoor walk.
- Verified PB token **and** a 3-minute guitar riff.
- Exceptional eligible long session: 10–15 minutes guitar **and** a longer outdoor walk.
- Guitar **and** coffee *only* when coffee conditions are met separately.

Combining activities does **not** mean stacking several tiers. The app awards **one highest satisfied tier** per policy. Keep combined physical load modest.

## 9. Pre-work ignition ritual — NEVER completion rewards

**Never assign:** push-ups, plank, eye mask, breathing exercises, eye-mask/silence rest, white-light focus ritual. These are reserved for starting work. Do not sneak them into combined rewards or PB tiers.

## 10. Forbidden rewards — NEVER assign

YouTube, Shorts, social media, Chess.com, pornography, doomscrolling, random online entertainment, shopping, endless browsing, or similar sticky digital activity.

## 11. Assignment policy and ranking

1. **No reward is normal.** Easy/admin/routine blocks commonly have `"reward_policy":{"enabled":false}`. A completed block never requires a reward.
2. **GPT decides independently for every block.** Choose a number of tiers, eligible score tests, and unique exact reward text for that block before starting. Two blocks with the same BeatScore may correctly unlock completely different rewards.
3. **Default progression:** lower eligible tiers favor light resets/tokens; mid tiers favor modest activation; high tiers may use guitar/combined tangible rewards. Do **not** blindly escalate reps with score, assign exercise to every block, or imply that the same rank always earns a particular action. Five outcome bands are optional, not mandatory.
4. **Do not count the no-reward band as an app tier.** Example: no reward below 85; tiers at 85, 95, 105, and 110. With `"selection":"highest_satisfied"`, FocusDeck picks one matching tier. Above score 111 is impossible.
5. **PB-only rewards:** set `"condition":"personal_best"` and use only if the run has a valid prior comparable benchmark; first attempts are not PBs.
6. **Physical load:** account for recent pull-ups, squats, grip, stairs, outside exercise, fatigue and soreness. Avoid failure sets, exhausting combinations, and repeated hard exercise in adjacent blocks. When unknown, choose a light reset or tangible token. Award text does not prove the user performed it.
7. **Independent session rewards:** select eligibility on weighted Session Score, counts/classes, Focus Ratio, and/or Session BeatScore. Reserve longer guitar or outdoor activities for genuinely substantial sessions. Session Score and Session BeatScore are different metrics.
8. **Time budgeting:** include planned focus + breaks + realistic reward opportunities + timer overtime reserve in the available wall window. Rewards are optional offers; never force delaying necessary food, water or rest.
9. **Approval and reproducibility:** show planned reward approach in the proposal, get approval, then include every concrete policy in the approved plan JSON. App chooses by supplied policies only. Review exact reward text, tier, and acknowledgment from matching report; acknowledgment is self-report, not physical proof.
10. **Change handling:** when I update this library, adopt it for *future* plans. Do not silently modify an already-started session or reinterpret rewards in its report.

## 12. My editable preferences / constraints

Fill in as needed; unanswered fields mean GPT uses conservative choices:

| Setting | Current preference |
|---|---|
| Overall favorite block rewards | Pull-ups, walk/fresh air, mobility, physical token |
| Favorite session rewards | Guitar (10–15 min), longer outdoor walk |
| Default pull-ups | 3–5 submaximal reps |
| Maximum total pull-up load per session | Not set — GPT must adjust conservatively |
| Coffee earliest eligibility | After ~3–4 hours of productive focus |
| Coffee latest local clock time | 16:00 (editable default) |
| Latest caffeine / sleep constraints | Not yet specified; omit coffee if uncertain |
| Injury, soreness, fatigue, current workout | Not specified; use light rewards if unclear |
| Other rewards/equipment I own | Add here |



## 13. Stopwatch and progress compatibility

The catalog, amounts, exclusions and caffeine constraints above also apply to stopwatch work. Choose completion or module-completion conditions (optionally Focus Ratio), not nonexistent stopwatch BeatScore/Pace/PB. An entirely untimed session can use completion counts, all-focus completion and overall Focus Ratio for an independent session reward; a mixed plan must explicitly label any countdown-only score conditions. External estimates reserve reward/break time; no stopwatch targets.

Updating a numeric progress card is not itself a reward, completed study day or proof of mastery. A physical progress marker may be offered only for confirmed distinct achievement evidence; never duplicate it just because a progress SET is retried. App progress and daily activity remain separate. No catalog reward, priority, quantity or forbidden activity was changed by this compatibility update.
