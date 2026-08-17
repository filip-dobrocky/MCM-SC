// Bridges the MCM ensemble clock to Ableton Link, so TidalCycles (or anything else
// speaking Link) can play as a member of the ensemble.
//
//   /clock/pulse, /tempo/bpm  ->  LinkClock  ->  Tidal
//   /scale/degrees, /scale/root  ->  /ctrl mcmscale | mcmroot  ->  Tidal (cS / cF)
//   Tidal  ->  /mcm/conduct  ->  MCMConductor  ->  the ensemble
//
// Link discovers peers on loopback, so this only bridges the local machine.
// The ensemble still spans the network over AOO as before.
MCMLink {
    var <linkID, <serverAddress, <groupName, <serverPort, <clientPort, <group;
    var client, <isConnected = false, <isListening = false;
    var <link, <conductor, <tidalAddr;
    var <ppqn, <quantum, <resyncThreshold;
    var echoGuard, forceAlign = true, tempoWatcher, conductDef;

    *new { |conductor, linkID, serverAddress, groupName, serverPort, clientPort|
        ^super.new.init(
            conductor,
            linkID ? MCMConfig.username,
            serverAddress ? MCMConfig.serverAddress,
            groupName ? MCMConfig.groupName,
            serverPort ? MCMConfig.serverPort,
            clientPort ? MCMConfig.linkPort
        );
    }

    init { |cond, idArg, serverAddr, groupNm, serverPrt, clientPrt|
        conductor = cond;
        linkID = idArg;
        serverAddress = serverAddr;
        groupName = groupNm;
        serverPort = serverPrt;
        clientPort = clientPrt;

        ppqn = MCMConfig.defaultPPQN;
        quantum = 4;              // must match Tidal's cQuantum / cBeatsPerCycle
        resyncThreshold = 0.125;  // beats
        tidalAddr = NetAddr("127.0.0.1", MCMConfig.tidalCtrlPort);

        link = LinkClock.new(MCMConfig.defaultBPM / 60);
        link.quantum = quantum;
        // This clock schedules nothing itself - it only publishes the ensemble's tempo
        // and phase to Link. With a latency offset, `beats` reads later than it is set,
        // so every alignment would overshoot by latency * tempo. Tidal and SuperDirt
        // apply their own output latency downstream.
        link.latency = 0;

        if (conductor.notNil) { this.prEnableConducting };
    }

    connect { |action|
        var maxAttempts = 5;

        fork {
            var tryConnect;
            tryConnect = { |attemptsLeft|
                client = AooClient(clientPort);

                Server.default.sync;

                client.connect(serverAddress, serverPort, "_", action: { |err|
                    if (err.isNil) {
                        client.joinGroup(groupName, "link-" ++ linkID.asString, "_", "_",
                            action: { |err, grp, usr|
                                if (err.isNil) {
                                    isConnected = true;
                                    group = grp;
                                    "MCMLink: successfully joined group % as user %"
                                        .format(grp.name, usr.name).postln;
                                    action.value();
                                } {
                                    "MCMLink: failed to join group: %".format(err).postln;
                                };
                            }
                        );
                    } {
                        "MCMLink: connection failed on port %: %".format(clientPort, err).postln;

                        if (attemptsLeft > 1) {
                            clientPort = MCMConfig.nextLinkPort;
                            "MCMLink: retrying on port % (% attempts left)".format(clientPort, attemptsLeft - 1).postln;
                            tryConnect.value(attemptsLeft - 1);
                        } {
                            "MCMLink: exhausted connection attempts".postln;
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
            "MCMLink: disconnected".postln;
        } {
            "MCMLink: not connected".postln;
        };
    }

    start {
        if (isListening) {
            "MCMLink: already listening".postln;
            ^this;
        };

        isListening = true;
        forceAlign = true;
        client.addListener(\msg, { |msg, time, peer|
            switch (msg.data[0])
            { '/clock/pulse' } { this.prAlign((msg.data[1] + (msg.data[2] / ppqn))) }
            { '/clock/ppqn' } { ppqn = msg.data[1] }
            { '/tempo/bpm' } { this.prSetTempo(msg.data[1]) }
            { '/tempo/playing' } { if (msg.data[1] > 0) { forceAlign = true } }
            { '/scale/root' } { tidalAddr.sendMsg("/ctrl", "mcmroot", msg.data[1].asFloat) }
            { '/scale/degrees' } {
                tidalAddr.sendMsg("/ctrl", "mcmscale",
                    msg.data[1..].collect(_.asFloat).join(" "));
            };
        });

        "MCMLink: started, % Link peer(s)".format(link.numPeers).postln;
    }

    stop {
        if (isListening) {
            client.removeListener(\msg);
            isListening = false;
            "MCMLink: stopped".postln;
        } {
            "MCMLink: not listening".postln;
        };
    }

    free {
        this.stop;
        tempoWatcher !? { link.removeDependant(tempoWatcher); tempoWatcher = nil };
        conductDef !? { conductDef.free; conductDef = nil };
        link.stop;
    }

    // Internal methods
    prSetTempo { |bpm|
        echoGuard = bpm / 60;
        link.tempo = echoGuard;
    }

    // Link does not sync absolute beat numbers, only phase within the quantum,
    // so the error is only ever meaningful modulo the quantum.
    prAlign { |mcmBeats|
        var err = (mcmBeats - link.beats) % quantum;
        if (err > (quantum / 2)) { err = err - quantum };

        if (forceAlign or: { err.abs > resyncThreshold }) {
            link.beats = link.beats + err;
            forceAlign = false;
        };
    }

    // Conducting from Tidal. Tempo rides Link both ways so that `setcps` is an
    // ensemble change rather than a silent desync; everything else needs OSC,
    // because Link carries neither transport nor harmony.
    prEnableConducting {
        // ponytail: single-value echo guard. Enough for one bridge per machine; if two
        // peers change tempo inside the same notification window, use a time window.
        tempoWatcher = { |clock, what|
            if ((what == \tempo) and: { (clock.tempo - (echoGuard ? 0)).abs > 1e-6 }) {
                conductor.setTempo(clock.tempo * 60);
            };
        };
        link.addDependant(tempoWatcher);

        conductDef = OSCdef(\mcmConduct, { |msg|
            switch (msg[1].asSymbol)
            { \playing } { if (msg[2] > 0) { conductor.startClock } { conductor.stopClock } }
            { \root } { conductor.setRoot(msg[2]) }
            { \degrees } { conductor.setScale(Scale(msg[2..].collect(_.asFloat))) };
        }, '/mcm/conduct');
    }
}
