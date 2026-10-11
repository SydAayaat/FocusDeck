> Historical V1.5 instructions. For the current DONE-only flow, use [UPDATE_1.6.md](UPDATE_1.6.md).

# Update to FocusDeck V1.5

This source release adds quality-gated BeatScores, comparable-history PBs, GPT-defined block/session rewards, daily physical/caffeine eligibility and detailed automatic reward logs. It preserves existing pairing, private history and OLED behavior when installed with the same signing key. No APK is included because Android SDK/compiler are unavailable here.

Extract the updated source over `/home/hax/Projects/FocusDeck` while preserving your Android SDK local.properties, signing key and PC configuration/data. Then:

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
bash scripts/build-android.sh test &&
bash scripts/build-android.sh debug &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh &&
bash scripts/setup-desktop.sh
```

After both updates:

```bash
focusdeck validate examples/competitive-study.json
focusdeck start examples/competitive-study.json
```

For a short smoke test, create a fresh session UUID and set both focus block durations to 60 seconds and the break to 10 seconds. This is a test of behavior, not a learning benchmark. Session guitar is optional: skip it during smoke testing. Avoid physical rewards while simply checking controls; use SKIP REWARD. The real example has 50 timed minutes plus review/reset and optional guitar overhead.

Check on the S24 Ultra:

1. Start and wait beyond the first budget. Overtime must remain on the same task. DONE must open the black/white/gray quality screen, not start the next timer.
2. Confirm all criteria. Check planned/active/paused/wall, pace/focus/score/rank, NO BENCHMARK YET and configured reward. Leave the screen open: next block must not start. SKIP REWARD advances and logs skipped=true, acknowledged=false.
3. On a later focus block, use NOT COMPLETED. Confirm zero score/no reward and that the session completion fraction reflects incomplete work. CONTINUE finalizes or opens session reward. Incomplete sessions have no session reward actions; there is no punishment.
4. Repeat a genuinely comparable quality-confirmed task with the same comparison, duration, criteria and scoring. A slightly better result can earn PB; no comparison metadata/different complexity or duration must show no benchmark. Very early completion must be marked recalibration and held to completion tier, not PB.
5. Test a configured daily limit of zero and then a small rep/seconds limit, with a supplied mobility/walk alternative. Offered or skipped rewards must not consume volume. Acknowledged activity must carry into another session and survive app/service/process restart.
6. Pause/resume before DONE, rotate during review, background/lock the phone, disconnect LAN and reconnect. Timer times must remain correct; review/reset gaps must be separate intervals. Reboot recovery must retain pending gates and mark timeline partial without inventing active time.
7. Finish/skip the session reward. Retrieve the exact ID report and inspect all four automatic CSVs on PC and in the chosen phone history folder. COPY FULL REPORT must contain score, criteria, benchmark and reward acknowledgements. Reconnect/retransmit must not duplicate rows.
8. Coffee is disabled in the shipped example. Inspect configurable eligibility with real valid study history when useful: short sessions/cutoff/spacing must use only supplied non-coffee alternatives or no reward. Do not shorten the minimum productive duration to force a smoke-test coffee award; it is validated at ≥3h.

Configure reward policy through your approved JSON plan. Use `docs/GPT_PROJECT_INSTRUCTIONS.md` in the GPT project for proposal/approval/plan/report feedback, and `docs/REWARDS_AND_COMPETITION.md` for exact rules. Existing plans without reward_strategy keep legacy DONE progression. This update adds no AI call or polling to reward screens; battery measurements remain a separate device task.
