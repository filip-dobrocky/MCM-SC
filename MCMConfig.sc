MCMConfig {
    // Network configuration
    classvar <>serverAddress = "localhost";
    classvar <>groupName = "ensemble";
    classvar <>username = "user";
    classvar <>serverPort = 8080;
    classvar <>playerPort = 9014;
    classvar <>conductorPort = 9015;
    classvar <>linkPort = 9016;

    // Tidal's OSC control input (cF / cS) and sclang's own OSC port
    classvar <>tidalCtrlPort = 6010;

    // Clock configuration
    classvar <>defaultPPQN = 24;
    classvar <>defaultBPM = 120;

    // Utility methods for getting incremented ports for multiple instances
    *nextPlayerPort {
        ^this.playerPort + (0..100).choose;
    }
    
    *nextConductorPort {
        ^this.conductorPort + (0..100).choose;
    }

    *nextLinkPort {
        ^this.linkPort + (0..100).choose;
    }
}
