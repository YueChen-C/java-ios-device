package protocol.model;

import java.io.InputStream;
import java.io.OutputStream;

/*
* I/O of the established connection and device information.
 */
public class UsbMuxConnection {
	public InputStream inputStream;
	public OutputStream outputStream;
	public Device device;
}
