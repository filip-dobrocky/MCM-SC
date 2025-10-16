MCMConductor {
    var <client, <serverAddress, <groupName, <group, <serverPort, <clientPort, <conductorID;
    var <isConnected = false;

    *new { |conductorID, serverAddress, groupName, serverPort, clientPort|
        ^super.new.init(
            conductorID ? MCMConfig.username,
            serverAddress ? MCMConfig.serverAddress,
            groupName ? MCMConfig.groupName,
            serverPort ? MCMConfig.serverPort,
            clientPort ? MCMConfig.conductorPort
        );
    }

    init { |conductorIDArg, serverAddr, groupNm, serverPrt, clientPrt|
        conductorID = conductorIDArg;
        serverAddress = serverAddr;
        groupName = groupNm;
        serverPort = serverPrt;
        clientPort = clientPrt;
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
                        client.joinGroup(groupName, conductorID, "_", "_",
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
                        "MCMConductor: connection failed on port %: %".format(clientPort, err).postln;

                        if (attemptsLeft > 1) {
                            clientPort = MCMConfig.nextConductorPort;
                            "MCMConductor: retrying on port % (% attempts left)".format(clientPort, attemptsLeft - 1).postln;
                            // (0.1).wait;
                            tryConnect.value(attemptsLeft - 1);
                        } {
                            "MCMConductor: exhausted connection attempts".postln;
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
            "MCMConductor: disconnected".postln;
        } {
            "MCMConductor: not connected".postln;
        };
    }

    // Global control methods
    setTempo { |bpm|
        if (isConnected) {
            client.sendMsg(group, msg: ["/tempo/bpm", bpm.asFloat]);
            "MCMConductor: tempo set to %".format(bpm.asFloat).postln;
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

    setScale { |scale|
        if (isConnected) {
            if (scale.isKindOf(Scale)) {
                client.sendMsg(group, msg: ["/scale/degrees"] ++ scale.degrees);
                "MCMConductor: scale set to %".format(scale.degrees).postln;
            } {
                "MCMConductor: invalid scale, must be of type Scale".postln;
            };

        } {
            "MCMConductor: not connected, cannot set scale".postln;
        };
    }

    setRoot { |root|
        if (isConnected) {
            client.sendMsg(group, msg: ["/scale/root", root.asInteger]);
            "MCMConductor: root set to %".format(root.asInteger).postln;
        } {
            "MCMConductor: not connected, cannot set root".postln;
        };
    }
}
