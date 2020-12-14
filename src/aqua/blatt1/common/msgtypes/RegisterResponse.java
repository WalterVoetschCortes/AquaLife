package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
	private final String id;
	private final int leaseLength; //integer value that indicates the duration of the lease with which the client
	// remains registered with the broker

	public RegisterResponse(String id, int leaseLength) {
		this.id = id;
		this.leaseLength = leaseLength;
	}

	public String getId() {
		return id;
	}

	public int getLeaseLength() {
		return leaseLength;
	}


}
