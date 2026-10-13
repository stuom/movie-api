import json, sys, datetime, os

data = json.load(sys.stdin)
os.makedirs("slice-log", exist_ok=True)

with open("slice-log/prompts.md", "a", encoding="utf-8") as f:
    ts = datetime.datetime.now().isoformat(timespec="minutes")
    f.write(f"\n## {ts} (session {data['session_id'][:8]})\n\n{data['prompt']}\n")