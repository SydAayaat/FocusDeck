# Index Tracker — FocusDeck 1.19.0

The phone owns all Index state. Desktop prepares commands and reads acknowledged snapshots; tablets are read-only. Existing cards are never automatically migrated.

## Desktop workflow

Card Builder → New Index Tracker. Choose HYBRID (default) or PROGRESS_ONLY. Import the actual numbered Markdown/text index or JSON. Preview the unique leaf count and reconciliation changes. Edit titles, parent IDs and ordering while keeping source IDs stable. Review and send. Icons use the existing reusable multi-file/ZIP library and palette tint.

For existing PTfS / Custom Compute cards, open Card Builder → Migrate this card to Index. Import the real Master Index, review and explicitly confirm migration. The phone checks the store revision and preserves the same card ID, measurement kind, original definition, numeric progress history, activity, contracts, completed streaks and icon assignment. No numeric progress is mapped to fabricated topics. Progress-only cards retain old completed streaks as legacy evidence.

For revisions, import/edit the new document and preview added/removed/renamed/moved/reordered nodes and changed leaf roles. Reconciliation requires the current Index revision and explicit confirmation. Removed topic states and events remain stored; re-importing those stable IDs restores their recorded states. Prior documents and the original definition remain in the ledger as rollback evidence. Restore a previous document through guarded reconciliation, never delete/recreate a real card.

Achievements shows the derived topic count, nested checklist, search, section counts, selected evidence, milestones, inline history and 7/14/30/90-day bars (default 14). Overview/Analytics include Index summaries and actual evidence copying. Copy/export is available from the selected Index detail. Tablet mirrors the hierarchy and actual events without writer controls.

Topic complete/reopen uses the current Index revision, exact receipt ID, actual timestamp, optional note and evidence. Replays never count twice. Same-state updates do not generate events. A reopen reduces current progress but retains its earlier completions. Bars show gross completion events; net labels retain reopen corrections. Re-completion is a new real completion event, not duplicate delivery.

Hybrid actual study activity is explicitly confirmed separately. The existing phone-local date, daily-objective and day-closing model is retained. A topic event never creates a study day; a study event never increases topic count. Progress-only cards show no daily-streak controls.

## Exact import format

```text
1 Chapter
1.1 Section
1.1.1 Named topic A
1.1.2 Named topic B
```

Numbered Markdown headings/lists also work. Every nonblank line must have a real explicit number; introductory prose must be removed. JSON supports explicit ASCII IDs and parent references:

```json
{"topics":[{"id":"chapter","title":"Chapter"},{"id":"topic-a","title":"Actual topic","parent_id":"chapter"}]}
```

Only leaves are trackable. Duplicate IDs, ambiguous numeric aliases (1 versus 01), missing parents and cycles are rejected. Maximum 5,000 nodes, depth 32, ID 100 characters, title 500 characters, import text 512 KiB and a combined node-text bound of 130,000 characters. No imported completion flags are accepted. Topic timestamps must be chronological per topic, and cannot be in the phone's future.

No actual PTfS or Custom Compute Master Index was supplied for this release. PTfS's existing numeric 126 target is not verified as a canonical topic count. Actual imported unique leaves determine the new target.

## Compatibility / deployment

Achievement command contract 5 extends contracts 1–4. Storage/snapshot/wire remain 1, with backward-default Index fields. Capabilities gate delivery. Updated tablets receive full evidence; older tablets receive a compatible progress projection without unknown Index fields. Normal sessions, reports, rewards and credentials are unchanged.

Use the full source update and existing install scripts on your PC, preserving its existing signing key. The chat APK is signed with a workspace debug key; a mismatched installed signer cannot accept an in-place update. Do not uninstall or clear app data to bypass this. No installation, live migration, re-pairing, data reset or remote push was performed.
