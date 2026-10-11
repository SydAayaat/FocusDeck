# FocusStack report analysis example

This is a **synthetic worked example**, not a claim about your real study history. It uses the supplied JSON plan and arithmetic/eligibility checked against the current desktop implementation. All eight timed blocks end with DONE; six are focus blocks, two are breaks. There is no quality-confirmation gate or incomplete outcome.

## Example input and operation

Assumption: notes define topics 1.1–1.8 as grouped in the plan. Available wall time: four hours. Timed targets: **180 minutes focus + 20 minutes breaks**. The remaining 40 minutes cover result/reward time and overtime reserve. Block rewards are selective; session reward is independent. No coffee reward is assigned.

GPT supplies reward wording before execution. Pull-ups, physical tokens, outdoor walks and guitar are arbitrary text, not catalog entries. The app checks the supplied score ranges and displays the matching text. Your push-up/plank ignition ritual stays separate. BeatScore still maxes out at111; this numeric bound comes from the score formula.

Save the JSON file, then run:

```bash
focusdeck validate FOCUSSTACK_GPT_INPUT_EXAMPLE.json
focusdeck start FOCUSSTACK_GPT_INPUT_EXAMPLE.json
```

The example UUID and created_at identify this illustrative plan. For an actual new session, use a fresh UUID and current timestamp; this wrapper makes a fresh copy and validates it before starting:

```bash
python3 - <<'PY_PLAN'
import datetime as dt
import json
import uuid
from pathlib import Path

source = Path("FOCUSSTACK_GPT_INPUT_EXAMPLE.json")
plan = json.loads(source.read_text())
plan["session_id"] = str(uuid.uuid4())
plan["created_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
Path("plan.json").write_text(json.dumps(plan, indent=2) + "\n")
print("Session ID:", plan["session_id"])
PY_PLAN
focusdeck validate plan.json && focusdeck start plan.json
```

After completion and sync: `focusdeck copy-report --session YOUR_SESSION_ID`, then Ctrl+V in the GPT Project. Keep the actual printed UUID with the original plan.

## Example completed-session evidence

- Session ID: `d4b707a2-2aeb-4d66-a8e5-0d377a64a9d8`
- Report key: `d4b707a2-2aeb-4d66-a8e5-0d377a64a9d8:1791367200000`
- Status: `complete`; block outcomes: `done`.
- Started: 2026-10-07 10:00:00 UTC; finished: 13:42:38.800 UTC.
- Recorded timezone: Europe/Berlin; tables below use UTC.
- Assumed timeline coverage: complete; no extensions; no previous comparable benchmark.

### The coach's response

**Completed all assigned work. Focus active time was 177/180 minutes (3 minutes under target), but the derivation needed 10 extra minutes. Session score: 73.75/100; session BeatScore: 100.57. Ten minutes of guitar unlocked.**

| Block | Target min | Active min | Delta min | Paused min | Pace Index | Focus Ratio | BeatScore / rank | Class | Weight |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | ---: |
| b1 | 20 | 16 | -4 | 0 | 125.00 | 100.00% | 111.00 S+ | strong | 0.5 |
| b2 | 30 | 26 | -4 | 2 | 115.38 | 92.86% | 106.67 S | strong | 1 |
| b3 | 45 | 55 | +10 | 8 | 81.82 | 87.30% | 86.83 B | slightly_over | 1.5 |
| b4 | 35 | 30 | -5 | 1 | 116.67 | 96.77% | 108.36 S | strong | 1.5 |
| b5 | 30 | 30 | +0 | 2 | 100.00 | 93.75% | 98.44 A | on_target | 1 |
| b6 | 20 | 20 | +0 | 0 | 100.00 | 100.00% | 100.00 A | on_target | 0.5 |

Target deltas use the **original planned active duration**, not wall time. The derivation is completed and slightly_over; it is not a failed task.

Session measurements:

| Measurement | Value |
| --- | --- |
| Focus active / break active | 177 min / 20 min |
| Total active, including breaks | 197 min |
| Paused | 13 min |
| Overtime beyond block budgets | 10 min (b3) |
| Aggregate focus Focus Ratio | 93.16% |
| Mean block Focus Ratio | 95.11% |
| Raw weighted points / total weight | 4.425 / 6 |
| Normalized session score | 73.75/100 |
| Session BeatScore / rank | 100.57 / A |
| Class counts | strong: 3; on_target: 2; slightly_over: 1; over_target: 0 |
| On-target or better | 5/6 |
| Wall time, including rewards/results | 3h 42m 38.8s |
| Remaining within four-hour window | 17m 21.2s |

Weighted points = 0.5(1) + 1(1) + 1.5(0.2) + 1.5(1) + 1(0.75) + 0.5(0.75) = **4.425**.
Session score = 100 × 4.425 / 6 = **73.75**. Session BeatScore is the separate weighted average of block BeatScores; it is not another name for 73.75.

### Rewards and benchmarks

| Stage | Decision | Example receipt |
| --- | --- | --- |
| b1 | Disabled: no reward assigned | No physical action |
| b2 | Beat 106.67 clears 102, not 108; PB condition false | Tier 1: “Walk outside for 60 seconds.”, acknowledged |
| b3 | Beat 86.83 clears neither 100 nor 107 | No reward unlocked; work completed |
| b4 | Beat 108.36 clears 102 and 108 | Tier 2: “90 seconds of gentle mobility + move one FocusStack token.”, acknowledged |
| b5 | Beat 98.44 is below 107 | No reward unlocked; work completed |
| b6 | Disabled: no reward assigned | No physical action |
| Session | 3 strong >=1; 5 on-or-above >=4; 0 over <=1; Focus 93.16% >=90; score73.75 >=70 | Tier 1: “Play guitar for 10 minutes.”, acknowledged |

The session score does not reach 85 or 95, so no larger tier. Missing b3/b5 block rewards does not prevent the independent session reward. No squats were unlocked in this example. The descriptions suggest **150 seconds of reset activity + one token**. This is a coach reading the text, not a measured activity counter. The app stores exact text/acknowledgement timestamps without parsing reps, tokens or caffeine. Acknowledgement is user confirmation, not verification.

Coffee is absent from the supplied text. Productive focus is177 minutes, below the coach’s normal3-hour threshold. A four-hour booking is not four productive hours. The app does not enforce coffee timing for text rewards; GPT must account for known timing/caffeine context when planning.

**PB: no benchmark yet.** No invented PB claims, including b1's 111 score. Rank S+ is not itself a personal best, nor evidence of mastery.

### Pause/transition evidence

| Block | Start UTC | DONE UTC | Transition |
| --- | --- | --- | --- |
| b1 | 10:00:00 | 10:16:00 | Focus DONE |
| b2 | 10:16:02.200 | 10:44:02.200 | Focus DONE |
| rest1 | 10:45:02.200 | 10:55:02.200 | Break DONE |
| b3 | 10:55:02.200 | 11:58:02.200 | Focus DONE |
| b4 | 11:58:04.400 | 12:29:04.400 | Focus DONE |
| rest2 | 12:30:34.400 | 12:40:34.400 | Break DONE |
| b5 | 12:40:34.400 | 13:12:34.400 | Focus DONE |
| b6 | 13:12:36.600 | 13:32:36.600 | Focus DONE |

| Block | Pause UTC | Resume UTC | Duration |
| --- | --- | --- | --- |
| b2 | 10:29:02.200 | 10:31:02.200 | 2 min |
| b3 | 11:22:32.200 | 11:30:32.200 | 8 min |
| b4 | 12:13:04.400 | 12:14:04.400 | 1 min |
| b5 | 12:55:34.400 | 12:57:34.400 | 2 min |

b3 has the longest pause (8 minutes), but its 55 active minutes already exclude that pause. Its block wall time is 63 minutes. Ask whether the pause was recovery, interruption or reference lookup before interpreting it. The synthetic timeline uses one pause in each of b2/b3/b4/b5; four pauses total.

The gaps after DONE are result/reward stages: four empty results at 2.2s each, b2's walk60s and b4's mobility90s. After the last block result, guitar10min runs from 13:32:38.800 to 13:42:38.800 UTC. These 12m38.8s explain the difference between 210 minutes active+paused and total wall time. Empty-result auto-advance assumes the app screen stays visible. Real acknowledgement timestamps determine reward-stage wall time; the configured reward duration alone does not prove elapsed time.

### Next-plan calibration

A single result supports provisional adjustments. For a repeat workload, request the last 3–5 comparable executions before claiming a stable pattern. The following prior active minutes are **illustrative additional evidence**, not stored user history:

| Comparable category | Five active results, min | Median | Next target | Reason |
| --- | --- | ---: | ---: | --- |
| b1 prerequisite review | 16, 18, 17, 16, 18 | 17 | 18 min | Moderate reduction from20; not fastest16 |
| b3 two derivations | 51, 54, 55, 52, 56 | 54 | 55 min | Increase from45; preserve verification |
| b4 three worked problems | 29, 32, 31, 30, 33 | 31 | 33 min | Small reduction from35; not fastest29 |
| b2 concepts / b5 recall / b6 synthesis | No repeated evidence supplied | — | Keep30 / 30 / 20 min | Do not recalibrate from one run |

Three adjustments:

1. Shift target time from consistently generous review/problem blocks into derivation: **18 + 30 + 55 + 33 + 30 + 20 = 186 focus minutes**.
2. Keep both10-minute breaks, the current weights and small selective rewards. With a four-hour window, 206 timed minutes leave34 minutes for rewards/results/reserve. Allow7 minutes for the supplied block reward/result stages and17 minutes for the largest session reward, leaving10 minutes reserve; if that is insufficient, explicitly reduce scope or reward allowance.
3. Re-evaluate the harder derivation reward gates after several runs at the new target. Do not lower thresholds merely to guarantee rewards, and do not accelerate every category.

New target durations change app PB matching. Update target_revision (and session comparison_key for the revised full workload) to make the new benchmark explicit. GPT can still use appropriately comparable old timing evidence to estimate workload; the app's strict PB match is narrower.

One learning-quality check: **Show your checked derivation and explain one assumption or limiting case from memory.** Timing and DONE establish declared execution/completion; they do not establish correctness, recall or mastery.
