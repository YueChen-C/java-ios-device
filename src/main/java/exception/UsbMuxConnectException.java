package exception;

/*
* Exception happening when attempting to connect a Device.
 */
public class UsbMuxConnectException extends UsbMuxdException {
	public UsbMuxConnectException(String s) {
		super(s);
	}
}


