# ao

The portable artificial-organism model, extracted from `kotoba-lang/tamaki`.

Model only. No CLI, no runner, no storage, no clock — every function takes
`now-ms` from its caller. That is what makes it usable from a Worker, a JVM
job and an nbb script without three copies drifting apart.

```
src/ao/identity.cljc    durable AO id, dormancy, family membership
src/ao/lineage.cljc     the incarnation lease, wellbecoming, succession
src/ao/evolution.cljc   the self-evolution gate and git write authority
```

## Where it sits

```
ao        self-evolves + self-judges   → holds git write authority, needs a lease
yakuwari  self-judges                  → no lease          (kotoba-lang/yakuwari)
agent     neither                      → bounded by its request (kotoba-lang/agent)
```

Residency is **orthogonal**. All three can be resident on murakumo, which is
a kotoba computing cloud in the wasmCloud sense; being resident changes
latency and cost, never authority.

## Why only an AO has a lease

An AO rewrites its own definition and holds commit/push/merge authority over
its own repository. Nothing above it is fixed, so no structural bound
applies: a policy it can rewrite is not a bound, and a review of code it can
replace expires the moment it does.

That leaves exactly one bound that survives self-modification — a temporal
one. The lease is not a biological flourish; it is the only point at which a
human is *required* to re-consent to what the AO has become.

And the lease belongs to an **incarnation**, not to the AO itself. The
repository-bound AO persists; archiving makes it dormant, never gone. What
expires is the named individual currently stewarding it.

## Why this exists

tamaki is "the single CLI for Kotoba's durable agent execution stack", and
its ADR-0001 defines the model everything else borrows: an **Actor** is a
durable role with an objective and a policy, an **AgentRun** is one bounded
execution created to perform it, and `codex`/`claude`/`grok` are replaceable
runner profiles rather than Actor names.

That model kept getting re-derived. A fourth spelling of the HIL decision
set was drafted while scaffolding an AO repo before anyone checked what
tamaki already had. `ao` exists so the next one doesn't.

## What lives elsewhere

`ao/run.cljc` and `ao/policy.cljc` were here briefly and were wrong to be:
this repo first held all three layers before the axes were separated. They
are now `kotoba-lang/agent` and `kotoba-lang/yakuwari`.

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

16 tests, 46 assertions, both hosts.

## Status

Extracted 2026-07-29. **tamaki has not yet been switched over to it** — the
namespaces there are unchanged, so for now this is a second copy rather than
a shared one. Pointing `kotoba.tamaki.model`/`lineage` at `ao` is the
follow-up that makes the extraction real.
