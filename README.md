# ao

The portable artificial-organism model, extracted from `kotoba-lang/tamaki`.

Model only. No CLI, no runner, no storage, no clock — every function takes
`now-ms` from its caller. That is what makes it usable from a Worker, a JVM
job and an nbb script without three copies drifting apart.

```
src/ao/run.cljc       AgentRun contract + state machine + event fold
src/ao/lineage.cljc   finite organism, wellbecoming, governed succession
src/ao/policy.cljc    human-in-the-loop capability policy
```

## Why this exists

tamaki is "the single CLI for Kotoba's durable agent execution stack", and
its ADR-0001 defines the model everything else borrows: an **Actor** is a
durable role with an objective and a policy, an **AgentRun** is one bounded
execution created to perform it, and `codex`/`claude`/`grok` are replaceable
runner profiles rather than Actor names.

That model kept getting re-derived. A fourth spelling of the HIL decision
set was drafted while scaffolding an AO repo before anyone checked what
tamaki already had. `ao` exists so the next one doesn't.

## What was deliberately left behind

**The capability contract.** `kotoba.tamaki.capability`'s own docstring says
vocabulary, ABI, effect classes, validation and envelope shape are owned by
`kotoba-lang/kotoba-core-contracts` — it is already just an adapter. Copying
it here would have created the exact duplication this repo exists to end.

**tamaki's execution stack.** Runner pool, node dispatch, `reconcile-plan`,
murakumo and kotoba-code integration are tamaki's job and stay there.

## What changed on the way out

- `:tamaki.event/*` → `:ao.event/*`. The only tamaki-specific thing in the
  run model was the event prefix.
- `lineage/organism` no longer defaults `family-name` to `"Tamaki"`, and no
  longer assumes 30 days is everyone's cap. Both are parameters now; the
  30-day default is preserved so an unconfigured fleet inherits tamaki
  ADR-0002's reviewed bound rather than none.

## The decisions worth knowing

**Refusal is a dead end.** `:rejected` and `:cancelled` have no outgoing
transitions. `:failed` can be requeued because a failure is often retryable,
but re-deriving a run from a human's *no* would launder the refusal.

**Illegal transitions throw.** A run whose history no longer explains its
state is worse than a crash.

**An unlisted capability is `:blocked`.** A capability nobody wrote a rule
for is one nobody reviewed, and defaulting those open is how an AO acquires
powers by omission. Unparseable decisions fail closed the same way.

**Vitality is a geometric mean.** No wellbeing dimension can be compensated
away by maximizing another — an AO with perfect throughput and zero human
agency scores near zero, which is the intended reading. An arithmetic mean
would have scored that same organism 0.8.

**Reproduction always needs signed human consent.** There is no vitality
score high enough to substitute for it, and `succession-plan` produces no
child when the gate blocks.

**A lease cannot be extended from inside.** `organism` refuses a lifetime
longer than the fleet maximum.

## Legacy spellings

`ao.policy` accepts the drafted `:self-executing` / `:propose` /
`:forbidden` and maps them onto `:autonomous` / `:approval-required` /
`:blocked`. `deprecated-spellings` reports them, because a fleet that never
reports them keeps two vocabularies alive forever.

## Test

```sh
npm test          # nbb / JS host
clojure -M:test   # JVM host — must agree exactly
```

20 tests, 64 assertions, both hosts.

## Status

Extracted 2026-07-29. **tamaki has not yet been switched over to it** — the
namespaces there are unchanged, so for now this is a second copy rather than
a shared one. Pointing `kotoba.tamaki.model`/`lineage` at `ao` is the
follow-up that makes the extraction real.
