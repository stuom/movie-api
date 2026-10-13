import json, sys, datetime, os

data = json.load(sys.stdin)
msgs = []
with open(data["transcript_path"], encoding="utf-8") as f:
    for line in f:
        try:
            msgs.append(json.loads(line))
        except json.JSONDecodeError:
            pass

# Find the most recent real user prompt (plain string content, not a tool result)
last_user = -1
for i, m in enumerate(msgs):
    if m.get("type") == "user" and isinstance(m.get("message", {}).get("content"), str):
        last_user = i

# Collect Claude's text blocks since that prompt; the last one is its closing summary
texts = []
for m in msgs[last_user + 1:]:
    if m.get("type") == "assistant":
        for block in m.get("message", {}).get("content", []):
            if isinstance(block, dict) and block.get("type") == "text":
                texts.append(block["text"])

summary = texts[-1] if texts else "(no text output)"

os.makedirs("slice-log", exist_ok=True)
with open("slice-log/slices.md", "a", encoding="utf-8") as f:
    ts = datetime.datetime.now().isoformat(timespec="minutes")
    f.write(f"\n## {ts} (session {data['session_id'][:8]})\n\n{summary}\n")