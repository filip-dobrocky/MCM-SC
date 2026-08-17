MCM-SC
------

Tools for [Minimal Computer Music](https://github.com/filip-dobrocky/MinimalComputerMusic) for SuperCollider.

Work in progress.

Classes:

- `MCMClock` - hosts the AOO server, broadcasts `/clock/pulse`
- `MCMConductor` - sends tempo, transport and scale to the group
- `MCMPlayer` - sequences a pattern against the shared clock
- `MCMLink` - bridges the shared clock to Ableton Link, so [TidalCycles](https://tidalcycles.org)
  can play (and conduct) as a member of the ensemble
- `MCMConfig` - network and clock defaults

Dependencies: [AOO](https://git.iem.at/aoo/aoo) and [Pmini](https://github.com/j0py/Pmini).
Pmini is only needed for `MCMPlayer.setMini`; everything else works without it.

Patterns
--------

Three ways to give a player notes, all of them scale degrees:

```supercollider
p.setSequence("0:1 4:3 x:1 7:2 8-13:1"); // MCM notation, durations in beats
p.setMini("0 [2 4] <7 9>*2 ~");          // Tidal mini-notation, 4 beats per cycle
p.degrees = Pwhite(0, 14, inf);          // any SuperCollider pattern
```

`cycleBeats` is how many beats one pattern cycle lasts - `setSequence` sets it to 1
(durations are beats), `setMini` sets it to 4 (durations are cycles). Set it yourself
for other subdivisions.

Chords have no mini-notation equivalent of MCM's `0-2-5`; use Tidal's stack instead:
`"[0,2,5]"`.

Tests
-----

Plain `.scd` files, no framework. Evaluate the whole block; each posts `OK` or throws:

- `tests/scheduler_test.scd` - tick math: fractional durations, stretch, shift, dropped pulses
- `tests/mini_test.scd` - mini-notation, and switching back to MCM notation (needs Pmini)
- `tests/link_test.scd` - `MCMLink` tempo mapping and phase alignment (creates a Link session)

TODO:
----
- Test `Clock`, `Conductor` and `Player` locally and in a network.
- `MCMLink` corrects Link phase by jumping when the error exceeds a threshold. If a
  correction ever becomes audible over a long set, nudge the tempo instead.
