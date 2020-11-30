package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

/*
This message type transports the tank ID of the aquarium whose address is to be found as well
a request ID that helps the requesting client to match the request and response.
 */
public class NameResolutionRequest implements Serializable {
    private final String tankID;
    private final String requestID;

    public NameResolutionRequest(String tankID, String requestID){
        this.tankID = tankID;
        this.requestID = requestID;
    }

    public String getTankID(){
        return tankID;
    }

    public String getRequestID(){
        return requestID;
    }
}
