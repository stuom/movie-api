@AGENTS.md

## Claude Code specifics
- Use plan mode for every new slice and for any change touching more than three files.
- You (the main agent) orchestrate the project subagents: subagents cannot call other subagents.
  Delegate by name, for example "use the code-reviewer agent on the current diff".
- Keep `/clear`-ing between slices. All durable knowledge lives in AGENTS.md, `docs/` and the skills, not in chat history.
- When you make the same mistake twice, propose an addition to AGENTS.md instead of just correcting it.
