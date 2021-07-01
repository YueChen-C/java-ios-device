package protocol.model;

import protocol.PlistSocket;
import protocol.UsbMux;
import exception.MuxError;

/*
* Device Information
 */
public class Device {
	public String serialNumber;
	public String connectionType;
	public String productId;
	public String locationId;
	public Integer deviceId;
	public Integer port = 62078;
	public PlistSocket connect() throws MuxError {
		return new UsbMux().deviceConnect(deviceId,port);
	}
}
