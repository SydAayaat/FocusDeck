# GPT Index commands — FocusDeck 1.19.0

Use the desktop Paste from GPT area. Output one fenced text block headed FOCUSDECK CARD, UPDATE or BATCH. The desktop prepares an editable review; nothing is automatically sent. Existing session/reward/card functionality remains available.

Never invent topics, counts, timestamps, evidence or study activity. Ask for the actual Master Index. Do not confirm 126 without importing/counting the supplied PTfS index. Preserve exact source IDs, names and parents. Treat previous numeric totals as unmapped legacy evidence during migration.

Create:

```text
FOCUSDECK CARD
title: Exact subject title
type: index_hybrid
master_index: |
  1 Actual chapter
  1.1 Actual topic
```

Replace that illustrative index with the user's real source. Use index_progress for PROGRESS_ONLY. Optional description, metadata, id, kind (daily/scheduled) and daily_participation are retained. Do not supply an initial numeric progress field. Review the imported checklist before sending.

Update using the Index revision shown in the current phone-synchronized evidence:

```text
FOCUSDECK UPDATE
operation: index_topic
card: subject_card_id
topic_id: "1.1"
completed: true
expected_index_revision: 3
note: User-confirmed completion note
evidence: User-supplied evidence
```

Quote topic IDs to prevent YAML treating numbers as numeric values. completed:false reopens. Omit optional note/evidence when absent. Each actual changed state advances the Index revision; same-state no-ops do not. For BATCH, supply the exact successive revision preconditions. Fresh operations get fresh receipt IDs; retry an uncertain delivery through the saved desktop receipt, not a new command.

Daily activity is independent and explicit:

```text
FOCUSDECK UPDATE
operation: event
card: subject_card_id
event: completed
text: Actual study activity explicitly confirmed by the user
```

The phone accepts only its current local study date. Numeric SET/increment is forbidden for Index cards. Do not infer study days from topic completion or completion from a confirmed study day.

Advanced UPDATE/BATCH operations: index_create(definition,document,configuration), index_migrate(achievement_id,document,configuration,expected_store_revision,confirm:true), index_reconcile(achievement_id,document,expected_index_revision,confirm:true), index_topic(achievement_id,topic_id,completed,expected_index_revision,note?,evidence?). The canonical schema is achievement-command-v5.schema.json. Migration/reconciliation should be prepared through the GUI dry-run preview. Preserve card identity/history; never delete/recreate it. Use copied Index evidence and exact session reports from the app for subsequent GPT evaluation.
