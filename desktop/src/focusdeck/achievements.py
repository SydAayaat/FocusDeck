"""Achievement contracts 1/2/3 and durable phone-authoritative snapshot assembly."""
from __future__ import annotations
import datetime as dt
import json
import re
import math
import tempfile
from .icons import validate_icon
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from .common import atomic_json, MAX_BYTES

def timestamp(value):
    if not isinstance(value, str) or len(value) > 60:
        raise ValueError("timestamp must be timezone-aware ISO-8601")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None or parsed.utcoffset() is None:
            raise ValueError()
        if parsed.timestamp() < 0:
            raise ValueError()
        return parsed
    except (ValueError, OverflowError):
        raise ValueError("timestamp must be timezone-aware ISO-8601 after 1970") from None

def integer(value, name, maximum):
    if type(value) is not int or not 1 <= value <= maximum:
        raise ValueError(f"{name} must be an integer from 1 to {maximum}")

def identifier(value, name="id", definition=False):
    if not isinstance(value,str) or not re.fullmatch(r"[a-z][a-z0-9_]{0,99}" if definition else r"[A-Za-z0-9_.:-]{1,100}",value):
        raise ValueError(f"Invalid {name}")

def validate_progress_fields(value):
    for field in ("current_value","target_value"):
        if field in value:
            number=value[field]
            if type(number) not in (int,float) or not math.isfinite(number) or not 0 <= number <= 1e12 or field=="target_value" and number<=0:
                raise ValueError(f"{field} must be finite, nonnegative and <= 1e12; target must be positive")
    if "unit" in value and (not isinstance(value["unit"],str) or not value["unit"].strip() or len(value["unit"])>100):raise ValueError("unit must be 1..100 characters")

def validate_command(value):
    if not isinstance(value,dict) or type(value.get("achievement_version")) is not int or value["achievement_version"] not in (1,2,3,4,5,6):
        raise ValueError("Expected achievement_version 1–6 object")
    version = value["achievement_version"]
    if version == 6 and value.get('operation') == 'layout_order':
        if set(value) - {'achievement_version','operation','id','timestamp','source','order','expected_store_revision'}: raise ValueError('Unsupported order fields')
        identifier(value.get('id')); timestamp(value.get('timestamp'))
        order=value.get('order')
        if not isinstance(order,list) or len(order)>1000 or any(not isinstance(k,str) for k in order) or len(set(order))!=len(order): raise ValueError('Invalid achievement order')
        for key in order: identifier(key,definition=True)
        if not isinstance(value.get('source','GPT'),str) or not 1 <= len(value.get('source','GPT')) <= 100:raise ValueError('Invalid source')
        if type(value.get('expected_store_revision')) is not int or value['expected_store_revision']<0:raise ValueError('Expected store revision required')
        return value
    if version == 6:raise ValueError('Achievement contract 6 supports layout_order only')
    if version == 5 and str(value.get("operation", "")).startswith("index_"):
        from .index_tracker import validate_index_command
        return validate_index_command(value)
    operation = value.get("operation")
    fields = {"upsert": {"definition"}, "archive": {"achievement_id"}, "event": {"achievement_id","event","amount","text"}, "daily_target": {"date","objectives"}}
    if version >= 2:fields.update(restore={"achievement_id"}, delete={"achievement_id","confirm"})
    if version >= 3:fields.update(progress_set={"achievement_id","current_value","target_value","unit"}, progress_increment={"achievement_id","amount"})
    if version >= 4:fields.update(icon_set={"achievement_id","icon"})
    base = {"achievement_version","operation","id","timestamp","source"}
    if operation not in fields or set(value) - base - fields[operation]:
        raise ValueError("Unknown achievement operation or unsupported fields")
    identifier(value.get("id")); timestamp(value.get("timestamp"))
    if not isinstance(value.get("source","GPT"),str) or not 1 <= len(value.get("source","GPT")) <= 100:
        raise ValueError("source must be 1..100 characters")
    if operation == "upsert":
        d = value.get("definition")
        if not isinstance(d,dict) or set(d)-({"id","title","category","kind","description","daily_participation","archived","created_date","metadata"} | ({"streak_enabled","progress_enabled","progress"} if version >= 3 else set())):
            raise ValueError("Invalid achievement definition")
        identifier(d.get("id"),definition=True)
        if not isinstance(d.get("title"),str) or not d["title"].strip() or len(d["title"])>200:
            raise ValueError("definition title required, maximum 200 characters")
        if version == 1:
            if d.get("category") not in {"projects","college","index"} or d.get("kind","scheduled") != "scheduled":raise ValueError("Only scheduled projects/college definitions can be created or updated in contract 1")
        elif d.get("category") not in {"discipline","health","learning","focus","projects","college","index"} or d.get("kind","scheduled") not in {"daily","scheduled","abstinence"}:raise ValueError("Custom cards require a valid category and daily, scheduled or abstinence kind")
        if version >= 3:
            for key in ("streak_enabled","progress_enabled"):
                if key in d and type(d[key]) is not bool:raise ValueError(f"{key} must be boolean")
            if not d.get("streak_enabled",True) and not d.get("progress_enabled",False):raise ValueError("At least one capability is required")
            if d.get("progress_enabled") and d.get("kind")=="abstinence":raise ValueError("Abstinence progress is unsupported")
            if "progress" in d:
                if not d.get("progress_enabled"):raise ValueError("progress requires progress_enabled")
                progress=d["progress"]
                if not isinstance(progress,dict) or set(progress)-{"current_value","target_value","unit"}:raise ValueError("Invalid initial progress")
                validate_progress_fields(progress)
        for field in ("daily_participation","archived"):
            if field in d and type(d[field]) is not bool: raise ValueError(f"{field} must be boolean")
        if not isinstance(d.get("description",""),str) or len(d.get("description",""))>1000: raise ValueError("Invalid description")
        metadata=d.get("metadata",{})
        if not isinstance(metadata,dict) or len(metadata)>30 or any(not isinstance(k,str) or len(k)>100 or not isinstance(v,str) or len(v)>2000 for k,v in metadata.items()): raise ValueError("Invalid metadata")
        if "created_date" in d and not isinstance(d["created_date"],str): raise ValueError("created_date must be a string; phone assigns it")
    elif operation == "daily_target":
        try:
            date=dt.date.fromisoformat(value.get("date",""))
            if date.isoformat()!=value["date"]:raise ValueError()
        except (TypeError,ValueError):raise ValueError("date must be YYYY-MM-DD") from None
        objectives=value.get("objectives")
        if not isinstance(objectives,list) or not 1<=len(objectives)<=100:raise ValueError("Target needs 1..100 objectives")
        seen=set()
        for o in objectives:
            if not isinstance(o,dict) or set(o)-{"kind","minimum","achievement_id","text"} or o.get("kind") not in {"focus_blocks","focus_time","achievement"}:raise ValueError("Invalid objective")
            integer(o.get("minimum",1),"minimum",604800000)
            if not isinstance(o.get("text",""),str) or len(o.get("text",""))>2000:raise ValueError("Invalid objective text")
            if o["kind"]=="achievement":identifier(o.get("achievement_id"),definition=True)
            elif o.get("achievement_id") is not None:raise ValueError("Focus objectives cannot reference achievement_id")
            key=(o["kind"],o.get("achievement_id"))
            if key in seen:raise ValueError("Duplicate target objective")
            seen.add(key)
    else:
        identifier(value.get("achievement_id"),definition=True)
        if operation=="icon_set":validate_icon(value.get("icon"))
        if operation=="progress_set":
            if not any(k in value for k in ("current_value","target_value","unit")):raise ValueError("SET needs current_value, target_value or unit")
            validate_progress_fields(value)
        if operation=="progress_increment":integer(value.get("amount"),"amount",100000)
        if operation=="delete" and value.get("confirm") is not True:raise ValueError("Permanent deletion requires confirm:true")
        if operation=="event":
            if value.get("event") not in {"initialize","streak_break","completed","objective_completed"}:raise ValueError("Unsupported achievement event")
            integer(value.get("amount",1),"amount",100000)
            if not isinstance(value.get("text",""),str) or len(value.get("text",""))>2000:raise ValueError("Invalid event text")
    if len(json.dumps(value,ensure_ascii=False).encode())>131072:raise ValueError("Achievement command exceeds 128 KiB")
    return value

class SnapshotReceiver:
    """Ordered chunks keep every WebSocket envelope below the existing 1 MiB bound.

    Incomplete snapshots never replace the previous durable cache. No history-age cap.
    """
    def __init__(self, root):
        self.root=Path(root);self.file=None;self.snapshot_id=None;self.index=0;self.count=0

    def close(self):
        if self.file:self.file.close()
        self.file=None;self.snapshot_id=None;self.index=0

    def receive(self,msg):
        if msg.get("achievement_version")!=1:raise ValueError("Unsupported achievement snapshot")
        identifier(msg.get("snapshot_id"),"snapshot_id")
        count,index,data=msg.get("count"),msg.get("index"),msg.get("data")
        if type(count) is not int or count<1 or type(index) is not int or not 0<=index<count or not isinstance(data,str) or len(data)>50000:raise ValueError("Invalid snapshot chunk")
        if index==0:
            self.close();self.file=tempfile.TemporaryFile(mode="w+",encoding="utf-8",dir=self.root)
            self.snapshot_id=msg["snapshot_id"];self.count=count
        if not self.file or msg["snapshot_id"]!=self.snapshot_id or index!=self.index or count!=self.count:
            self.close();raise ValueError("Out-of-order achievement snapshot")
        self.file.write(data);self.index+=1
        if self.index<count:return None
        try:
            self.file.seek(0);snapshot=json.load(self.file)
            if not isinstance(snapshot,dict) or snapshot.get("achievement_version")!=1 or not isinstance(snapshot.get("ledger"),dict) or not isinstance(snapshot.get("summary"),list):raise ValueError("Invalid achievement snapshot payload")
            if type(snapshot.get("as_of_ms")) is not int or snapshot["as_of_ms"]<0 or not isinstance(snapshot.get("zone"),str):raise ValueError("Invalid achievement snapshot clock")
            try:ZoneInfo(snapshot["zone"])
            except (ZoneInfoNotFoundError,ValueError):raise ValueError("Invalid achievement snapshot timezone") from None
            ledger=snapshot["ledger"]
            if any(not isinstance(ledger.get(k),list) for k in ("definitions","events","segments","targets")) or not isinstance(ledger.get("days"),dict):raise ValueError("Invalid achievement ledger entities")
            if any(not isinstance(d,dict) or not isinstance(d.get("id"),str) for d in ledger["definitions"]):raise ValueError("Invalid achievement definitions")
            if any(not isinstance(s,dict) or not isinstance(s.get("id"),str) for s in snapshot["summary"]):raise ValueError("Invalid achievement summaries")
            # Persist BEFORE accepting this snapshot as a complete PC cache.
            atomic_json(self.root/"achievements.json",snapshot)
            return snapshot
        finally:self.close()

def query(root, achievement_id=None):
    path=Path(root)/"achievements.json"
    if not path.exists():return {"ok":True,"cached":True,"summary":[],"notice":"No phone achievement snapshot received yet"}
    # Achievement history is intentionally unbounded; the bounded control wire is not its file format.
    with path.open() as f:snapshot=json.load(f)
    if achievement_id:
        ledger=snapshot["ledger"]
        if not any(d["id"]==achievement_id for d in ledger["definitions"]):raise ValueError("Unknown achievement")
        return {"ok":True,"cached":True,"as_of_ms":snapshot["as_of_ms"],"zone":snapshot["zone"],
            "summary":[s for s in snapshot["summary"] if s["id"]==achievement_id],
            "segments":[s for s in ledger["segments"] if s["achievementId"]==achievement_id],
            "events":[e for e in ledger["events"] if e["achievementId"]==achievement_id],
            "days":ledger["days"],"targets":ledger["targets"],
            "progress_history":[h for h in ledger.get("progressHistory",[]) if h["achievementId"]==achievement_id]}
    return {"ok":True,"cached":True,"as_of_ms":snapshot["as_of_ms"],"zone":snapshot["zone"],"summary":snapshot["summary"]}
