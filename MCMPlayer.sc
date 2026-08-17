MCMPlayer {
    var <playerID, <serverAddress, <groupName, <serverPort, <clientPort, <group;
    var client, <isConnected = false, <isListening = false;
    var <scale, <root, <octave, <amp, <instrument;
    var <stretch, <shift, <ppqn, <bpm, <beatTimeDur, <cycleBeats;
    var <stream, <nextTick, miniActive = false;

    *new { |playerID, serverAddress, groupName, serverPort, clientPort|
        ^super.new.init(
            playerID ? MCMConfig.username, 
            serverAddress ? MCMConfig.serverAddress,
            groupName ? MCMConfig.groupName,
            serverPort ? MCMConfig.serverPort,
            clientPort ? MCMConfig.playerPort
        );
    }

    init { |playerIDArg, serverAddr, groupNm, serverPrt, clientPrt|
        playerID = playerIDArg;
        serverAddress = serverAddr;
        groupName = groupNm;
        serverPort = serverPrt;
        clientPort = clientPrt;
        
        // Initialize musical defaults
        scale = Scale.major;
        root = 0;
        octave = 5;
        amp = 1;
        instrument = \default;
        stretch = 1.0;
        shift = 0;
        cycleBeats = 1;
        ppqn = MCMConfig.defaultPPQN;
        bpm = MCMConfig.defaultBPM;
        beatTimeDur = 60 / bpm;

        // Initialize pattern system using Pbindef
        Pbindef(playerID.asSymbol,
            \instrument, Pfunc({ instrument }),
            \scale, Pfunc({ scale }),
            \root, Pfunc({ root }),
            \octave, Pfunc({ octave }),
            \amp, Pfunc({ amp }),
            \degree, 0,
            \dur, 1,
            \sustain, Pfunc({ |ev| beatTimeDur * stretch * cycleBeats * ev[\dur] })
        );
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    // Connection methods
    connect { |action|
        var maxAttempts = 5;

        fork {
            // Inner recursive attempt function
            var tryConnect;
            tryConnect = { |attemptsLeft|
                client = AooClient(clientPort);

                Server.default.sync;

                client.connect(serverAddress, serverPort, "_", action: { |err|
                    if (err.isNil) {
                        client.joinGroup(groupName, "player-" ++ playerID.asString, "_", "_", 
                            action: { |err, grp, usr|
                                if (err.isNil) {
                                    isConnected = true;
                                    group = grp;
                                    "MCMPlayer: successfully joined group % as user %"
                                        .format(grp.name, usr.name).postln;
                                    action.value();
                                } {
                                    "MCMPlayer: failed to join group: %".format(err).postln;
                                };
                            }
                        );
                    } {
                        "MCMPlayer: connection failed on port %: %".format(clientPort, err).postln;

                        if (attemptsLeft > 1) {
                            // pick a new client port and retry
                            clientPort = MCMConfig.nextPlayerPort;
                            "MCMPlayer: retrying on port % (% attempts left)".format(clientPort, attemptsLeft - 1).postln;
                            // small delay before retrying
                            // (0.1).wait;
                            tryConnect.value(attemptsLeft - 1);
                        } {
                            "MCMPlayer: exhausted connection attempts".postln;
                        };
                    };
                });
            };

            tryConnect.value(maxAttempts);
        };
    }
    
    disconnect {
        if (isConnected) {
            client.disconnect();
            isConnected = false;
            "MCMPlayer: disconnected".postln;
        } {
            "MCMPlayer: not connected".postln;
        };
    }
    
    // Pattern methods
    setSequence { |sequenceString|
        // Parse your custom notation and update degree/dur keys in Pbindef
        var split = sequenceString.split($ );
        var arr = split.collect({ |x| x.split($:) });
        var notes = arr.collect({ |x|
            var chord = x[0].split($-);
            chord = chord.collect({ |note|
                if (note.interpret.isNumber) { note.asInteger } { Rest() }
            });
            if (chord.size == 1) { chord[0] } { chord }
        });
        var durations = arr.collect({ |x| if (x[1] == nil) { 1 } { x[1].asFloat } });
        this.prClearMini;
        cycleBeats = 1; // MCM notation counts durations in beats
        Pbindef(playerID.asSymbol, \degree, Pseq(notes, inf));
        Pbindef(playerID.asSymbol, \dur, Pseq(durations, inf));
        // Only recreate stream when sequence structure changes
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    // Tidal mini-notation, e.g. "0 [2 4] <7 9>*2 ~ 5(3,8)"
    // Values are scale degrees, so the conductor's scale and root still apply.
    setMini { |miniString|
        // Pbindef keeps a replaced key in its original slot, so \degree and \sustain
        // have to be removed first - otherwise they evaluate before Pmini has filled
        // in \str and \dur, and read stale values.
        Pbindef(playerID.asSymbol, \degree, nil, \sustain, nil);
        Pbindef(playerID.asSymbol,
            [\coin, \delta, \dur, \str, \num], Pmini(miniString),
            \degree, Pfunc({ |ev| if (ev[\coin].coin) { ev[\str].asFloat } { Rest() } }),
            \sustain, Pfunc({ |ev| beatTimeDur * stretch * cycleBeats * ev[\dur] })
        );
        miniActive = true;
        cycleBeats = 4;
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    degrees_ { |pattern|
        this.prClearMini;
        Pbindef(playerID.asSymbol, \degree, pattern);
        // Only recreate stream when sequence structure changes
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    durations_ { |pattern|
        this.prClearMini;
        Pbindef(playerID.asSymbol, \dur, pattern);
        // Only recreate stream when sequence structure changes
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    // Pmini leaves \trig \delta \str \num behind; they corrupt a plain pattern if not cleared.
    // Guarded, because clearing a key Pbindef never had *adds* it with a nil source,
    // which ends the pattern.
    prClearMini {
        if (miniActive) {
            // Remove the multi-key entry as a whole - Pbindef matches it by identity,
            // and asking for \coin on its own would instead ADD a nil-sourced key,
            // which ends the pattern.
            Pbindef(playerID.asSymbol, [\coin, \delta, \dur, \str, \num], nil, \sustain, nil);
            Pbindef(playerID.asSymbol,
                // \degree back to the default too: setMini's version reads \coin and
                // \str, which no longer exist. Callers overwrite it straight after.
                \degree, 0,
                \dur, 1,
                \sustain, Pfunc({ |ev| beatTimeDur * stretch * cycleBeats * ev[\dur] })
            );
            miniActive = false;
        };
    }

    // Musical parameter setters
    instrument_ { |synthDef|
        if (synthDef.isKindOf(Pattern)) {
            Pbindef(playerID.asSymbol, \instrument, synthDef);
            // Only recreate stream when pattern structure changes
            stream = Pbindef(playerID.asSymbol).asStream;
        } {
            instrument = synthDef;
            Pbindef(playerID.asSymbol, \instrument, Pfunc({ instrument }));
            // No need to recreate stream - Pfunc will pick up the new value
        };
    }
    stretch_ { |value|
        stretch = value.asFloat;
        Pbindef(playerID.asSymbol, \sustain, Pfunc({ |ev| beatTimeDur * stretch * cycleBeats * ev[\dur] }));
        // No need to recreate stream - Pfunc will pick up the new value
    }
    shift_ { |value|
        var newShift = value.asInteger;
        if (nextTick.notNil) { nextTick = nextTick + (newShift - shift) };
        shift = newShift;
    }
    // Beats per pattern cycle: 1 for MCM notation, 4 for mini-notation / Tidal
    cycleBeats_ { |value|
        cycleBeats = value.asFloat.max(0.001);
    }
    octave_ { |value|
        if (value.isKindOf(Pattern)) {
            Pbindef(playerID.asSymbol, \octave, value);
            // Only recreate stream when pattern structure changes
            stream = Pbindef(playerID.asSymbol).asStream;
        } {
            octave = value.asFloat;
            Pbindef(playerID.asSymbol, \octave, Pfunc({ octave }));
            // No need to recreate stream - Pfunc will pick up the new value
        };
    }
    amp_ { |value|
        if (value.isKindOf(Pattern)) {
            Pbindef(playerID.asSymbol, \amp, value);
            // Only recreate stream when pattern structure changes
            stream = Pbindef(playerID.asSymbol).asStream;
        } {
            amp = value.asFloat;
            Pbindef(playerID.asSymbol, \amp, Pfunc({ amp }));
            // No need to recreate stream - Pfunc will pick up the new value
        };
    }

    setParam { |param, value|
        if (value.isKindOf(Pattern)) {
            Pbindef(playerID.asSymbol, param, value);
            // Only recreate stream when pattern structure changes
            stream = Pbindef(playerID.asSymbol).asStream;
        } {
            Pbindef(playerID.asSymbol, param, Pfunc({ value }));
            // No need to recreate stream - Pfunc will pick up the new value
        };
    }
    
    // Control methods
    start { 
        if (isListening) {
            "MCMPlayer: already listening".postln;
            ^this;
        };
        
        isListening = true;
        nextTick = nil; // re-snap to the grid on the next pulse
        client.addListener(\msg, { |msg, time, peer|
            switch (msg.data[0])
            { '/clock/pulse' } { this.prClockEvent(msg.data[1], msg.data[2]); }
	        { '/clock/ppqn' } { ppqn = msg.data[1] }
	        { '/tempo/bpm' } { bpm = msg.data[1]; beatTimeDur = 60 / bpm; }
	        { '/scale/root' } { root = msg.data[1] }
            { '/scale/degrees' } { scale = Scale(msg.data[1..]) };
        });
        
        "MCMPlayer: started listening for clock messages".postln;
    }

    stop { 
        if (isListening) {
            client.removeListener(\msg);
            isListening = false;
            "MCMPlayer: stopped listening for clock messages".postln;
        } {
            "MCMPlayer: not listening".postln;
        };
    }

    // Internal methods
    prClockEvent { |beat, subdiv|
        var tick = (beat * ppqn) + subdiv;
        var event, beats;

        if (stream.isNil) { ^this };

        // Enter on a grid boundary so late joiners align with the rest of the ensemble
        if (nextTick.isNil) {
            var grid = (ppqn * stretch * cycleBeats).round.max(1);
            nextTick = (((tick - shift) / grid).ceil * grid) + shift;
        };

        // >=, not ==: a dropped pulse must not swallow the event
        if (tick >= nextTick) {
            event = stream.next(());
            if (event.isNil) {
                stream = nil;
                "MCMPlayer: pattern ended".postln;
                ^this;
            };
            // \delta drives time (Pmini sets it), \dur is the sounding length
            beats = ((event[\delta] ? event[\dur] ? 1)) * cycleBeats;
            nextTick = nextTick + (beats * ppqn * stretch).round.max(1);
            event.play;
        };
    }
}
