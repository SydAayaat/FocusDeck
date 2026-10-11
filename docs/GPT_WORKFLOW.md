# Paste a GPT plan, return its matching report

Use [GPT Project Instructions](GPT_PROJECT_INSTRUCTIONS.md) with the [v2 contract](STOPWATCH_MODE.md). Ask GPT to choose each topic's mode independently, specify deliverables/weights/exact reward policies, and return complete valid JSON in a quoted `focusdeck start -` heredoc. Every new plan needs a fresh UUID and current timezone-qualified created_at. No pairing credentials belong in a plan.

Example (replace UUID/time before a new real session):

```bash
focusdeck start - <<'FOCUSDECK_PLAN'
{
  "protocol_version": 2,
  "session_id": "f96cf951-45ad-4e15-a69e-78a122724108",
  "title": "Robotics 1",
  "created_at": "2026-10-07T10:00:00Z",
  "auto_advance": true,
  "blocks": [
    {"id":"ROB1-2.1","type":"focus","title":"Frames and rotation matrices",
     "timer_mode":"stopwatch","session_weight":1.2,
     "quality_criteria":["Solve one rotation matrix problem"],
     "reward_policy":{"enabled":false}},
    {"id":"ROB1-2.2","type":"focus","title":"Rotation composition",
     "timer_mode":"countdown","duration_seconds":1800,"session_weight":1.0,
     "quality_criteria":["Solve one composition example"],
     "reward_policy":{"enabled":false}}
  ],
  "session_reward_policy":{
    "enabled":true,"selection":"highest_satisfied",
    "tiers":[{"condition":"session_completed","min_completed_blocks":2,
              "reward":{"text":"Play guitar for ten minutes."}}]
  }
}
FOCUSDECK_PLAN
```

The quoted heredoc does not expand shell substitutions in JSON. Keep its final delimiter at the start of its own line. `focusdeck validate -` accepts the same heredoc without starting; file validation/start also remain supported. Both peers need 1.8 for v2; an old phone is rejected without changing the supplied modes. Existing v1 examples remain countdown.

Only DONE finalizes a topic, including after countdown expiry or while paused. Stopwatch has no expiry/target/overtime. DONE freezes measurements and shows the result/reward page. REWARD DONE acknowledges the activity; CONTINUE advances without acknowledgement. Disabled/unmatched rewards are normal results. With auto_advance true, the next block starts after result handling; false waits for START. No extra quality declaration is required.

The start receipt preserves session_id and a separate delivery command_id. Report attempt keys add actual start time. Do not reuse IDs for unrelated plans. Retrieve or copy this example's matching report:

```bash
focusdeck reports --session f96cf951-45ad-4e15-a69e-78a122724108 --latest
focusdeck copy-report --session f96cf951-45ad-4e15-a69e-78a122724108
```

Paste into the conversation that planned it. `--json` returns structured data; `--key UUID:STARTED_AT_MS` selects an exact attempt. The PC archive works offline after sync. KDE Wayland clipboard uses wl-copy; no manual terminal piping is needed. Phone COPY FULL REPORT carries the same plan identity and evidence.

Ask GPT to match the original plan/attempt, distinguish stopwatch from countdown, and assess DONE deliverables and actual/paused duration with Focus Ratio. Adjust targets only for countdown. Stopwatch Pace/Beat/class are N/A. Mixed scores describe countdown-only evidence, never whole-session performance. Independent completion rewards use explicit module IDs, DONE counts/all-focus completion or session completion.

Read timeline_coverage before interpreting pauses or uninterrupted stretches. Sequence order matters after civil-clock changes; missing legacy/reboot/partial evidence remains unknown. Block wall is observed monotonic active+paused; whole-session wall retains civil start/end accounting and waits. Ask for output, correctness and delayed recall before treating speed as mastery or pauses as distraction.

GPT does not gain access to your PC terminal by generating a plan. A prepared/running session cannot be replaced until the existing one is administratively stopped. Use the complete [stopwatch](../examples/stopwatch-study.json) and [mixed-mode](../examples/mixed-mode-study.json) examples as planning references, with fresh IDs for each real session.


## Achievements (1.9.1)

Use the existing companion connection: `focusdeck achievements apply FILE` or `apply -` for contract 1/2 JSON. Contract 2 creates manual cards in all categories and supports archive/restore/confirmed delete; see [1.9.1](UPDATE_1.9.1.md). `validate FILE` checks the production validator. `list` and `history ID` work offline using the automatic phone snapshot, explicitly marked cached with its observation timestamp. See [Achievements](ACHIEVEMENTS.md), [GPT achievement instructions](GPT_ACHIEVEMENTS_INSTRUCTIONS.md), and `examples/achievements/` for actual event, dynamic subject and latest-write-wins target payloads. Achievement writes require advertised phone capability, use stable event IDs and never alter session/report/reward contracts.


## FocusDeck 1.10 cumulative progress and motion

Current achievement command contracts are 1/2/3. Contract 3 adds `progress_set`, `progress_increment`, capability flags and initial progress in definitions. Commands 1/2 remain accepted; progress is capability-gated. Read [the 1.10 update](UPDATE_1.10.0.md) and [current GPT achievement rules](GPT_ACHIEVEMENTS_INSTRUCTIONS.md) before generating progress commands. Targets are supplied by project configuration; never assume Custom Compute has 100 topics. Progress does not replace the daily/streak model. All archive/restore/delete, replay, layout and closed-day safeguards remain.
