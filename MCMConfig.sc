MCMConfig {
    // Network configuration
    classvar <defaultServerAddress = "localhost";
    classvar <defaultGroupName = "ensemble";
    classvar <defaultServerPort = 8080;
    classvar <defaultPlayerPort = 9014;
    classvar <defaultConductorPort = 9015;
    
    // Clock configuration
    classvar <defaultPPQN = 24;
    classvar <defaultBPM = 120;
    
    // Utility methods for getting incremented ports for multiple instances
    *nextPlayerPort {
        ^this.defaultPlayerPort + (0..100).choose;
    }
    
    *nextConductorPort {
        ^this.defaultConductorPort + (0..100).choose;
    }
}
