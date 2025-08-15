MCMClock {
    var <server, <serverPort, <ppqn, <bpm, <isPlaying = false;
    var <clockRoutine, <beatCounter = 0, <subdivCounter = 0;
    var <groupName;
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
            server.start;
            
            Server.default.sync;
            
            "MCMClock: server started on port %".format(serverPort).postln;
            
            // Create clock client
            clockClient = AooClient(serverPort + 1);
            clockClient.connect("localhost", serverPort, "_", action: { |err|
                if (err.isNil) {
                    clockClient.joinGroup(groupName, "clock", "_", "_", action: { |err, group, user|
                        if (err.isNil) {
                            "MCMClock: clock client joined group %".format(group.name).postln;
                            this.prStartListening(clockClient);
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
    prStartListening { |clockClient|
        clockClient.addListener(\msg, { |msg, time, peer|
            switch (msg.data[0])
            { "/tempo/bpm" } { 
                this.tempo_(msg.data[1]);
            }
            { "/tempo/playing" } { 
                this.playing_(msg.data[1] == 1);
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
                server.sendMsgToGroup(groupName, "/clock/pulse", beatCounter, subdivCounter);
                
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
            server.sendMsgToGroup(groupName, "/clock/ppqn", ppqn);
        };
    }
}
