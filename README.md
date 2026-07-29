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

- `lineage/organism` no longer defaults `family-name` to `"Tamaki"`, and no
  longer assumes 30 days is everyone's cap. Both are parameters now; the
  30-day default is preserved so an unconfigured fleet inherits tamaki
  ADR-0002's reviewed bound rather than none.

## The decisions worth knowing

**Vitality is a geometric mean.** No wellbeing dimension can be compensated
away by maximizing another — an AO with perfect throughput and zero human
agency scores near zero, which is the intended reading. An arithmetic mean
would have scored that same organism 0.8.

**Reproduction always needs signed human consent.** There is no vitality
score high enough to substitute for it, and `succession-plan` produces no
child when the gate blocks.

**A lease cannot be extended from inside.** `organism` refuses a lifetime
longer than the fleet maximum.

AgentRun transition decisions now live in `kotoba-lang/agent`; role policy and
legacy policy spellings live in `kotoba-lang/yakuwari`.

## Test

```sh
npm test          # nbb / JS host
clojure -M:test   # JVM host — must agree exactly
```

16 tests, 46 assertions, both hosts.

## Status

Extracted and adopted by Tamaki on 2026-07-29.
`kotoba.tamaki.lineage` is now a compatibility adapter over `ao.lineage`;
Tamaki supplies only its family name and reviewed 30-day incarnation bound.
