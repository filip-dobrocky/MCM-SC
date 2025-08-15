MCMPlayer {
    var <playerID, <serverAddress, <groupName, <serverPort, <clientPort, <group;
    var client, <isConnected = false, <isListening = false;
    var <scale, <root, <octave, <amp, <instrument;
    var <stretch, <shift, <ppqn, <bpm, <beatTimeDur;
    var <stream, <beatCounter, <currentDuration;

    *new { |playerID, serverAddress, groupName, serverPort, clientPort|
        ^super.new.init(
            playerID, 
            serverAddress ? MCMConfig.defaultServerAddress,
            groupName ? MCMConfig.defaultGroupName,
            serverPort ? MCMConfig.defaultServerPort,
            clientPort ? MCMConfig.defaultPlayerPort
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
        ppqn = MCMConfig.defaultPPQN;
        bpm = MCMConfig.defaultBPM;
        beatTimeDur = 60 / bpm;
        beatCounter = 0;
        currentDuration = 1;
        
        // Initialize pattern system using Pbindef
        Pbindef(playerID.asSymbol,
            \instrument, Pfunc({ instrument }),
            \scale, Pfunc({ scale }),
            \root, Pfunc({ root }),
            \octave, Pfunc({ octave }),
            \amp, Pfunc({ amp }),
            \degree, 0,
            \dur, 1,
            \sustain, Pfunc({ |ev| beatTimeDur * stretch * ev[\dur] })
        );
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    // Connection methods
    connect { |action|
        fork {
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
                    "MCMPlayer: connection failed: %".format(err).postln;
                };
            });
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
        var durations = arr.collect({ |x| if (x[1] == nil) { 1 } { x[1].asInteger.max(1) } });
        Pbindef(playerID.asSymbol, \degree, Pseq(notes, inf));
        Pbindef(playerID.asSymbol, \dur, Pseq(durations, inf));
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    degrees_ { |pattern|
        Pbindef(playerID.asSymbol, \degree, pattern);
        stream = Pbindef(playerID.asSymbol).asStream;
    }

    durations_ { |pattern|
        Pbindef(playerID.asSymbol, \dur, pattern);
        stream = Pbindef(playerID.asSymbol).asStream;
    }
    
    // Musical parameter setters
    instrument_ { |synthDef|
        instrument = synthDef;
        Pbindef(playerID.asSymbol, \instrument, Pfunc({ instrument }));
        stream = Pbindef(playerID.asSymbol).asStream;
    }
    stretch_ { |value|
        stretch = value;
        Pbindef(playerID.asSymbol, \sustain, Pfunc({ |ev| beatTimeDur * stretch * ev[\dur] }));
        stream = Pbindef(playerID.asSymbol).asStream;
    }
    shift_ { |value|
        shift = value;
    }
    octave_ { |value|
        octave = value;
        Pbindef(playerID.asSymbol, \octave, Pfunc({ octave }));
        stream = Pbindef(playerID.asSymbol).asStream;
    }
    amp_ { |value|
        amp = value;
        Pbindef(playerID.asSymbol, \amp, Pfunc({ amp }));
        stream = Pbindef(playerID.asSymbol).asStream;
    }
    
    // Control methods
    start { 
        if (isListening) {
            "MCMPlayer: already listening".postln;
            ^this;
        };
        
        isListening = true;
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
        var clockVal = (beat * ppqn) + subdiv;
        var beatDur = (ppqn * stretch).round;

        if ((clockVal - (shift.ceil)) % beatDur == 0) {
            beatCounter = beatCounter + 1;

            if (beatCounter == currentDuration) {
                var event = stream.next(());
                currentDuration = event['dur'];
                event.play;
                beatCounter = 0;
            };
        };
    }
}