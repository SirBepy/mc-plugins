---
name: listener-chat
description: Turns a chat into a long-lived listener - blocks on a background watch/wait process, reacts to events as they arrive, re-arms itself on every respawn, sends nothing when quiet. Use when setting up or resuming any hub/watcher chat that waits on an external source (a daemon, a queue, a log tail) instead of being driven by a single request-response turn.
---

# Listener chat

A listener chat is not a request/response chat. It has no "task" that finishes; it sits blocked on a
background process and reacts only when that process reports something. Most turns start with no new
human message at all - they start because a background wait completed.

This skill is the reusable shape. `docs/player-claude.md` in this repo is the concrete instance that
follows it (Minecraft console events, player subagents); read it as a worked example, not as part of
this skill's own contract.

## The shape

1. **A daemon or watcher runs outside the chat's lifetime.** It owns the actual connection to whatever
   is being watched (a socket, a log file, a queue) and writes events somewhere durable (a file, a
   state dir) rather than straight into the chat. The chat never holds that connection itself - if it
   did, the connection would die every time the chat respawns or closes.
2. **The chat blocks on a `wait`-style call**, run via `/supervised-run` if the project has one (so it
   outlives the chat) or a backgrounded Bash/PowerShell call otherwise, and does nothing else until
   that call returns.
3. **A wait returning is the wake-up.** Handle every event it reported, in whatever way the specific
   project's routing rules say to, then immediately re-arm the same wait call before ending the turn.
   Never poll; never sleep-loop; the backgrounded call's completion notification is the only wake
   signal this pattern needs.
4. **Nothing is lost while the chat is down.** Events queue wherever the daemon writes them, so a
   wait call made after a gap (a closed chat, a crashed daemon that got restarted) picks up everything
   that happened in between. The chat never needs to "catch up" by re-deriving state itself.
5. **Quiet is the default output.** A wake-up with nothing worth telling the human about ends with no
   message at all, not a "no new events" bubble. Only real events, decisions, or blockers get sent.
6. **Respawn re-arms, it doesn't restart.** On `/respawn` (or any fresh start of this chat, including
   one resumed from a handoff prompt that doesn't literally say "/respawn"), the LITERAL FIRST tool
   call of the chat, before reading any state file or checking anything else, invokes the
   `auto-respawn` skill (it opts this chat into Conductor's 150k context-cap note; the opt-in does not
   carry over to a successor - skipping this step means the chat silently never respawns on its own
   and just keeps growing). Only after that does the chat check the daemon/watcher is alive and
   re-arm the wait call - before picking up any other carried-over work. A listener chat that respawns
   without re-arming silently stops reacting to anything until a human notices; one that forgets
   `auto-respawn` silently never gets the context-cap note either, which is just as easy to miss since
   there's no error, just a chat that quietly runs past 150k tokens with no one told.
   When the "Context cap" note arrives, finish the event in hand, then call `respawn` with a full
   handoff whose first instruction is to run `/auto-respawn` and re-arm.
7. **Any state needed to resume routing (which background agent/session owns which conversation,
   cursors, etc.) lives in a file the chat re-reads on start**, not in the chat's own memory - the chat
   itself is disposable and respawns often; the state file is what survives.

## When this chat learns something new about running the pattern

If, while operating as a listener chat, this chat discovers something that would help the *next*
listener chat of this same kind - a gotcha in how the wait call behaves, a better way to detect the
daemon died, a mistake this pattern makes it easy to fall into - update this file (not just the
project's own domain docs) with that finding before respawning or closing. This file is the one that's
supposed to accumulate that knowledge; the project-specific doc (e.g. `docs/player-claude.md`) stays
scoped to the domain's own routing and business rules.

## Gotcha: replying outside the normal routing path

If the hub ever answers an event directly instead of routing it through the usual per-conversation
state (a player agent, a ticket thread, whatever owns that conversation's history), read that
state first - don't treat the event as novel just because it arrived through the hub's own wake-up.
A hub that skips this can contradict an answer its own routed agent already gave minutes earlier
(happened 2026-10-13: the hub answered a repeated player question directly without checking the
player's notes file, and gave an answer that directly contradicted what the player's own agent had
already told them).

## Not this skill's job

- What the events mean, how to route them, who may act on what without asking: that's the hosting
  project's own domain doc. This skill only covers the listen/wait/respawn mechanics.
- Picking `/supervised-run` vs. a plain backgrounded process is a judgment call per project (does the
  project have a supervisor set up already?), not something this skill prescribes.
