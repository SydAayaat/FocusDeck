# Achievement detail: approved lower section

References: `approved-achievement-detail-gold.png` (full regenerated concept) and `approved-achievement-detail-lower.png` (Hax’s authoritative crop). The follow-up explicitly preserves everything above the crop: existing navigation, hero ring/icon/glow/sparkles, title/description and progress panel. These images are design references, not app screenshots or UI backgrounds.

| Reference section | Native implementation and data |
| --- | --- |
| Current / Best / Record gap / Average | Actual streak statistics; Best completed history, signed current-minus-best gap, completed-history average; narrow/large-text fallback |
| Policy callout | Actual contract/daily/scheduled/fixed-focus/abstinence completion rule |
| Streak history | Chronological completed segments, fitted gradient bars, nice y-axis ticks, dashed actual Best/Average, selected outline/marker; lazy horizontal scrolling beyond 32 bars |
| Selected streak | Real dates/duration/record; net stored progress by unit, average over counted streak days, stored daily-contract statuses; absent progress/contract evidence N/A |
| Today | Actual local date; cumulative total/target if enabled, activity/contract completion if streak enabled, latest GPT focus targets or measured day totals, actual status; rows expand inline |
| Detailed history | Outlined reference-style heading followed by inline lazy historical entries and retained evidence; no separate history-only page |

Hybrid includes both systems. Streak-only omits cumulative progress/history; progress-only omits streak statistics/chart/selected summary and daily streak objectives. Abstinence uses measured elapsed time and timestamps. Global palettes apply; no extra icons/assets are needed beyond the bundled pack/native small glyphs. Motion uses finite transitions only; the existing upper ring animation is unchanged.

No mockup values are copied into real state. No protocol, timer, ledger or persistence change. Source checks do not prove Android compilation or device pixel/touch behavior.
