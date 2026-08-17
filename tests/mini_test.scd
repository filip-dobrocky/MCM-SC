// MCMPlayer mini-notation check. Requires the Pmini quark. No server, no sound.
// Run this whole block; it posts "mini OK" or throws.
(
var p, take, check;

take = { |player, n| n.collect({ player.stream.next(()) }) };

check = { |label, got, expected|
    if (got != expected) {
        Error("mini/%: expected % got %".format(label, expected, got)).throw;
    };
};

p = MCMPlayer.new("mini-test");

// "0 [2 4] ~" is three steps per cycle; the group halves its two members
p.setMini("0 [2 4] ~");
check.("cycleBeats", p.cycleBeats, 4);
check.("degrees", take.(p, 4).collect({ |e| e[\degree] }), [0.0, 2.0, 4.0, Rest()]);
check.("deltas", take.(p, 4).collect({ |e| e[\delta].round(0.001) }), [0.333, 0.167, 0.167, 0.333]);

// \sustain must follow the mini-notation \dur, not the pre-mini default of 1.
// beatTimeDur 0.5 (120bpm) * stretch 1 * cycleBeats 4 * dur 1/3 = 0.667
check.("sustain", take.(p, 1).collect({ |e| e[\sustain].round(0.001) }), [0.667]);

// Switching back to MCM notation must leave no Pmini keys behind
p.setSequence("0:1 4:2");
check.("back to notation", take.(p, 4).collect({ |e| [e[\degree], e[\dur]] }),
    [[0, 1.0], [4, 2.0], [0, 1.0], [4, 2.0]]);
check.("cycleBeats reset", p.cycleBeats, 1);
check.("sustain after switch", take.(p, 1).collect({ |e| e[\sustain] }), [0.5]);

// ...and switching back again still works
p.setMini("0 2");
check.("mini again", take.(p, 2).collect({ |e| e[\degree] }), [0.0, 2.0]);

// Setting durations alone after setMini must not leave \degree reading Pmini's
// keys, which are gone by then
p.setMini("0 2 4");
p.durations = Pseq([2], inf);
check.("durations after mini", take.(p, 2).collect({ |e| [e[\degree], e[\dur]] }),
    [[0, 2], [0, 2]]);

// Same for degrees alone
p.setMini("0 2 4");
p.degrees = Pseq([5, 7], inf);
check.("degrees after mini", take.(p, 2).collect({ |e| [e[\degree], e[\dur]] }),
    [[5, 1], [7, 1]]);

"mini OK".postln;
)
