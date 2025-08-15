MCMConductor {
    var <client, <serverAddress, <groupName, <group, <serverPort, <clientPort;
    var <isConnected = false;

    *new { |serverAddress, groupName, serverPort, clientPort|
        ^super.new.init(
            serverAddress ? MCMConfig.defaultServerAddress,
            groupName ? MCMConfig.defaultGroupName,
            serverPort ? MCMConfig.defaultServerPort,
            clientPort ? MCMConfig.defaultConductorPort
        );
    }

    init { |serverAddr, groupNm, serverPrt, clientPrt|
        serverAddress = serverAddr;
        groupName = groupNm;
        serverPort = serverPrt;
        clientPort = clientPrt;
    }

    connect { |action|
        fork {
            client = AooClient(clientPort);

            Server.default.sync;
            
            client.connect(serverAddress, serverPort, "_", action: { |err|
                if (err.isNil) {
                    client.joinGroup(groupName, "conductor-" ++ clientPort, "_", "_",
                        action: { |err, grp, usr|
                            if (err.isNil) {
                                isConnected = true;
                                group = grp;
                                "MCMConductor: successfully joined group % as user %"
                                    .format(grp.name, usr.name).postln;
                                action.value();
                            } {
                                "MCMConductor: failed to join group: %".format(err).postln;
                            };
                        }
                    );
                } {
                    "MCMConductor: connection failed: %".format(err).postln;
                };
            });
        };
    }
    
    disconnect {
        if (isConnected) {
            client.disconnect();
            isConnected = false;
            "MCMConductor: disconnected".postln;
        } {
            "MCMConductor: not connected".postln;
        };
    }
    
    // Global control methods
    setTempo { |bpm| 
        if (isConnected) {
            client.sendMsg(group, msg: ["/tempo/bpm", bpm]);
            "MCMConductor: tempo set to %".format(bpm).postln;
        } {
            "MCMConductor: not connected, cannot set tempo".postln;
        };
    }
    
    startClock {
        if (isConnected) {
            client.sendMsg(group, msg: ["/tempo/playing", 1]);
            "MCMConductor: clock started".postln;
        } {
            "MCMConductor: not connected, cannot start clock".postln;
        };
    }
    
    stopClock {
        if (isConnected) {
            client.sendMsg(group, msg: ["/tempo/playing", 0]);
            "MCMConductor: clock stopped".postln;
        } {
            "MCMConductor: not connected, cannot stop clock".postln;
        };
    }
    
    setScale { |degrees|
        if (isConnected) {
            client.sendMsg(group, msg: ["/scale/degrees"] ++ degrees);
            "MCMConductor: scale set to %".format(degrees).postln;
        } {
            "MCMConductor: not connected, cannot set scale".postln;
        };
    }
    
    setRoot { |root|
        if (isConnected) {
            client.sendMsg(group, msg: ["/scale/root", root]);
            "MCMConductor: root set to %".format(root).postln;
        } {
            "MCMConductor: not connected, cannot set root".postln;
        };
    }
}
