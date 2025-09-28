MCMClock {
    var <server, <serverPort, <ppqn, <bpm, <isPlaying = false;
    var <clockRoutine, <beatCounter = 0, <subdivCounter = 0;
    var <groupName, <group;
    var clockClient;

    *new { |serverPort, ppqn, bpm, groupName|
        ^super.new.init(
            serverPort ? MCMConfig.defaultServerPort,
            ppqn ? MCMConfig.defaultPPQN,
            bpm ? MCMConfig.defaultBPM,
            groupName ? MCMConfig.defaultGroupName
        );
    }

    init { |serverPrt, ppqnVal, bpmVal, groupNm|
        serverPort = serverPrt;
        ppqn = ppqnVal;
        bpm = bpmVal;
        groupName = groupNm;
        beatCounter = 0;
        subdivCounter = 0;
    }

    start {
        if (server.notNil) {
            "MCMClock: already running".postln;
            ^this;
        };

        fork {
            // Ensure audio server is running
            server = AooServer(serverPort);

            Server.default.sync;

            "MCMClock: server started on port %".format(serverPort).postln;

            // Create clock client
            clockClient = AooClient(serverPort + 1);

            Server.default.sync;

            clockClient.connect("localhost", serverPort, "_", action: { |err|
                if (err.isNil) {
                    clockClient.joinGroup(groupName, "clock", "_", "_", action: { |err, grp, usr|
                        if (err.isNil) {
                            group = grp;
                            "MCMClock: clock client joined group %".format(grp.name).postln;
                            this.prStartListening();
                            this.prBroadcastPPQN();
                        } {
                            "MCMClock: failed to join group: %".format(err).postln;
                        };
                    });
                } {
                    "MCMClock: clock client connection failed: %".format(err).postln;
                };
            });
        };
    }

    stop {
        isPlaying = false;
        clockRoutine !? { clockRoutine.stop };
        clockRoutine = nil;
        server !? { server.stop };
        server = nil;
        beatCounter = 0;
        subdivCounter = 0;
        "MCMClock: stopped".postln;
    }

    tempo_ { |newBpm|
        bpm = newBpm;
        "MCMClock: tempo set to %".format(bpm).postln;
    }

    playing_ { |bool|
        if (bool) {
            this.prStartClock();
        } {
            this.prStopClock();
        };
    }

    ppqn_ { |value|
        ppqn = value;
        this.prBroadcastPPQN();
        "MCMClock: PPQN set to %".format(ppqn).postln;
    }

    // Private methods
    prStartListening {
        clockClient.addListener(\msg, { |msg, time, peer|
            msg.data[0].postln;
            switch (msg.data[0])
            { '/tempo/bpm' } {
                this.tempo_(msg.data[1]);
                "MCMClock: tempo set to %".format(bpm).postln;
            }
            { '/tempo/playing' } {
                this.playing_(msg.data[1] == 1);
                "MCMClock: playing set to %".format(msg.data[1] == 1).postln;
            };
        });
    }

    prStartClock {
        if (isPlaying) {
            "MCMClock: already playing".postln;
            ^this;
        };

        isPlaying = true;
        beatCounter = 0;
        subdivCounter = 0;

        clockRoutine = Routine({
            loop {
                var sleepTime;

                // Broadcast clock pulse
                clockClient.sendMsg(group, msg: ["/clock/pulse", beatCounter, subdivCounter], reliable: true);

                subdivCounter = subdivCounter + 1;
                if (subdivCounter >= ppqn) {
                    subdivCounter = 0;
                    beatCounter = beatCounter + 1;
                };

                // Calculate sleep time based on BPM and PPQN
                sleepTime = 60.0 / (bpm * ppqn);
                sleepTime.wait;
            };
        }).play;

        "MCMClock: started playing at % BPM".format(bpm).postln;
    }

    prStopClock {
        isPlaying = false;
        clockRoutine !? { clockRoutine.stop };
        clockRoutine = nil;
        "MCMClock: stopped playing".postln;
    }

    prBroadcastPPQN {
        server !? {
            clockClient.sendMsg(group, msg: ["/clock/ppqn", ppqn], reliable: true);
        };
    }
}
