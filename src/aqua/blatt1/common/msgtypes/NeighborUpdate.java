package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

/*
Message type NeighborUpdate with which the Broker can give
the InetSocketAddress of a new left or right neighbor to a client
*/
public class NeighborUpdate implements Serializable {
    private final InetSocketAddress addressRight;
    private final InetSocketAddress addressLeft;

    public NeighborUpdate (InetSocketAddress addressLeft,InetSocketAddress addressRight) {
        this.addressRight = addressRight;
        this.addressLeft = addressLeft;
    }

    public InetSocketAddress getAddressRight() {
        return addressRight;
    }

    public InetSocketAddress getAddressLeft() {
        return addressLeft;
    }
}
