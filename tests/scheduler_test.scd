// MCMPlayer scheduler check. No server, no network, no sound - just tick math.
// Run this whole block; it posts "scheduler OK" or throws.
(
var fired, currentTick, run, check;

// Swallow events so nothing reaches a server, and record when each one fired
Event.addEventType(\mcmTestSilent, { fired = fired.add(currentTick) });

// Build a player, apply `setup`, feed `n` pulses, return the ticks that fired
run = { |name, setup, n = 200, ppqn = 24|
    var p = MCMPlayer.new(name);
    p.setParam(\type, \mcmTestSilent);
    setup.value(p);
    fired = [];
    n.do({ |tick|
        currentTick = tick;
        p.prClockEvent(tick.div(ppqn), tick % ppqn);
    });
    fired;
};

check = { |label, got, expected|
    if (got[..(expected.size - 1)] != expected) {
        Error("scheduler/%: expected % got %".format(label, expected, got[..(expected.size - 1)])).throw;
    };
};

// ppqn 24, stretch 1, cycleBeats 1: dur 1 -> 24 ticks, 0.5 -> 12, 0.25 -> 6
check.("fractional durations",
    run.("test-frac", { |p| p.durations = Pseq([1, 0.5, 0.25], inf) }),
    [0, 24, 36, 42, 66, 78, 84, 108]
);

// Triplets: 1/3 beat -> 8 ticks
check.("triplets",
    run.("test-trip", { |p| p.durations = Pseq([1/3], inf) }),
    [0, 8, 16, 24, 32]
);

// Non-integer stretch: the grid becomes 24 * 0.75 = 18 ticks
check.("fractional stretch",
    run.("test-stretch", { |p| p.stretch = 0.75; p.durations = Pseq([1], inf) }),
    [0, 18, 36, 54]
);

// Phase shift offsets the whole grid
check.("shift",
    run.("test-shift", { |p| p.shift = 6; p.durations = Pseq([1], inf) }),
    [6, 30, 54]
);

// cycleBeats 4: a dur of 0.25 cycles == 1 beat == 24 ticks (the mini-notation timebase)
check.("cycleBeats",
    run.("test-cycles", { |p| p.cycleBeats = 4; p.durations = Pseq([0.25], inf) }),
    [0, 24, 48, 72]
);

// MCM notation still counts beats, and now parses fractional durations.
// No `x` here: SC plays rests as the \rest event type, which never reaches the
// recorder - their timing is covered by the duration checks above.
check.("setSequence",
    run.("test-seq", { |p| p.setSequence("0:1 4:0.5 7:0.5") }),
    [0, 24, 36, 48, 72, 84]
);

// A dropped pulse must delay the event, not swallow it
check.("dropped pulse",
    { var p = MCMPlayer.new("test-drop");
      p.setParam(\type, \mcmTestSilent);
      p.durations = Pseq([1], inf);
      fired = [];
      100.do({ |tick|
          if (tick != 24) { // pulse 24 never arrives
              currentTick = tick;
              p.prClockEvent(tick.div(24), tick % 24);
          };
      });
      fired;
    }.value,
    [0, 25, 48, 72]
);

"scheduler OK".postln;
)
