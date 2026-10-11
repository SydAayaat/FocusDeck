# GPT-written conditional rewards · FocusDeck1.7

FocusDeck 1.8 adds v2 stopwatch/completion rewards. The time-based arithmetic and range/PB examples below apply to countdown only. Stopwatch time metrics are N/A; mixed aggregates are countdown-only. See [STOPWATCH_MODE.md](STOPWATCH_MODE.md) for explicit module/session conditions and eligibility.

GPT supplies wording and conditions BEFORE starting. FocusDeck measures performance, selects an eligible supplied tier and displays its text. No required activity catalog: pull-ups, tokens, custom offline activities and combined descriptions need no implementation per activity.

```json
"reward_policy": {
  "enabled": true,
  "selection": "highest_satisfied",
  "tiers": [
    {"min_beat_score":100,"max_beat_score":106.99,
     "reward":{"text":"Walk outside for 2 minutes."}},
    {"min_beat_score":107,"max_beat_score":111,
     "reward":{"text":"5 pull-ups + move one token."}},
    {"condition":"personal_best","min_focus_ratio":90,
     "reward":{"text":"Replace your PB card + a 60-second walk."}}
  ]
}
```

Text: nonblank, <=1000 Unicode code points. Amounts, durations and combined activities belong in text, with no required type/amount/duration keys. Original case and line breaks are preserved. Text is displayed, never executed as code or interpreted as measurements.

GPT handles exercise load/variety, digital bans, the separate pre-work ritual and caffeine suitability. The app does not infer reps, physical duration or coffee cutoff from text. Legacy typed plans/history remain supported with their old rules; those restrictions do not apply to text rewards.

Block tests: min_beat_score/max_beat_score0–111, min_focus_ratio0–100, min_pace_index0–120, condition personal_best. Session tiers also accept min_session_score/max_session_score0–100 and cannot use pace tests. All tests must pass. Upper bounds are inclusive and >= minimums. Two-decimal ranges100–106.99 and107–111 leave no gap; alternatively use only minimums with highest_satisfied.

highest_satisfied puts matching PB first, then greatest score threshold (session score first for sessions), later array index on ties. first_satisfied follows array order. <=20 tiers, <=4 alternatives per tier. No matching tier means no reward, not failure. enabled:false assigns none. Text policies need no reward_strategy.

DONE freezes measurements. REWARD DONE acknowledges activity; CONTINUE advances unacknowledged. Both keep work completed. Empty results advance after about2.2s while visible. Session reward is independent and requires all planned blocks finalized.

BeatScore max111 remains;112 is unattainable. Weighted class score and session BeatScore remain separate. Formula/classes/benchmarks/timeline accounting: [project instructions](GPT_PROJECT_INSTRUCTIONS.md).

Logs preserve selected tier, original policy/ranges, exact text and acknowledgement/timestamp. CSVs retain reward_label/reward_actions_json; desktop Markdown/clipboard preserves text. Text receipts contain kind:text, text, reps:0, seconds:0; zeros mean no parsed measurements, not no real activity. Modified receipt text or a reward outside its saved range is rejected.

Examples: [study plan](../examples/gpt-text-study.json), [smoke test](../examples/text-reward-smoke-test.json), [worked analysis](FOCUSSTACK_REPORT_ANALYSIS_EXAMPLE.md).
