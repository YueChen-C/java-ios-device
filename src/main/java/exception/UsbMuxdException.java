package exception;

/*
* Global Exception happening on UsbMuxd.
 */
public class UsbMuxdException extends Exception {
	public UsbMuxdException(String s) {
		super(s);
	}
	public UsbMuxdException(Exception e) {
		super(e);
	}
}
