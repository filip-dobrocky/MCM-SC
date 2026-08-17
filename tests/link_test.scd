// MCMLink check: tempo mapping and phase alignment, without the network.
// Creates a real Link session briefly. Run this whole block; posts "link OK" or throws.
(
var l, target, wrap, check;

wrap = { |e, q| var x = e % q; if (x > (q/2)) { x - q } { x } };

check = { |label, got, expected, tol = 0.005|
    if ((got - expected).abs > tol) {
        Error("link/%: expected % got %".format(label, expected, got)).throw;
    };
};

protect {
    l = MCMLink.new; // no conductor: listen-only

    l.prSetTempo(90);
    check.("bpm -> link tempo", l.link.tempo * 60, 90);

    // First pulse after start force-aligns however far out of phase it is
    target = l.link.beats + 8 + 1.7;
    l.prAlign(target);
    check.("force align", wrap.(target - l.link.beats, l.quantum), 0);

    // Jitter below the threshold is left alone rather than chased
    target = l.link.beats + 0.05;
    l.prAlign(target);
    check.("below threshold", wrap.(target - l.link.beats, l.quantum), 0.05);

    // Real drift is corrected
    target = l.link.beats + 0.4;
    l.prAlign(target);
    check.("above threshold", wrap.(target - l.link.beats, l.quantum), 0);

    // Link only syncs phase within the quantum, so a whole-quantum offset is no error
    target = l.link.beats + l.quantum;
    l.prAlign(target);
    check.("whole quantum is in phase", wrap.(target - l.link.beats, l.quantum), 0);

    "link OK".postln;
} { |err|
    l !? { l.free };
    if (err.notNil) { err.throw };
};
)
