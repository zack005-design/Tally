# Tally project instructions

## Publish completed chat work

The user authorizes each chat working in this repository to update GitHub when its requested work is complete. Apply this to the changes owned by that chat, after relevant verification succeeds. Do not ask again for routine commits and pushes covered by this instruction.

- Inspect the working tree and current GitHub branch state before publishing. Multiple chats may share this checkout.
- Stage explicit paths or hunks for your own completed work. Never use blanket staging to include another chat's unfinished changes. If ownership of a shared file is unclear, isolate your changes before committing.
- Run checks appropriate to the change. Validate documentation links for documentation changes; run relevant tests and build checks for app changes. Do not claim a pending check passed.
- Review the staged diff for credentials, private financial records, generated outputs, and unrelated files before committing.
- Fetch before pushing. Publish to the existing branch according to the user's current branch policy; this repository currently uses `main`. Never force-push, discard someone else's work, or bypass a failing check to finish a chat.
- Keep README, user guides, and release information consistent with changes that affect them. Link only to releases and assets that actually exist.
- APKs and plugin ZIPs belong in GitHub Releases, not the source tree. Creating a new release requires completing the applicable verification and preserving the development/pre-release status unless a production release was explicitly requested.
- Keep this repository and its downloads private unless the user explicitly requests a visibility change.
- Never upload the separate LLM workspace under `tools/tally-lm/`, its checkpoints, datasets, caches, or credentials. The app's existing specialised expense-command model under `ml/tally-command/` and its bundled app assets are distinct and may be maintained within the authorized app scope.
- If publishing fails, preserve the completed local work and report the exact blocker. Finish with the commit or release link and a concise statement of verification and remaining limitations.

These instructions guide chats that load this repository. They are not a background service or a system-level chat-completion trigger. Chats already running must read the latest instructions before their next publication.
