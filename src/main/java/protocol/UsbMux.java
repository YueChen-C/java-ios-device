package protocol;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import lombok.extern.log4j.Log4j;
import protocol.model.Device;
import exception.MuxError;
import exception.NoMuxDeviceFound;
import util.Proto;

@Log4j
public class UsbMux {
    private int tag = 0;
    public PlistSocket createConnection(){
        return new PlistSocket(next_tag());
    }

    public int next_tag(){
        this.tag+=1;
        return this.tag;
    }

    public NSDictionary sendRecv(NSDictionary payload)  {
        try (PlistSocket c = createConnection()) {
            c.sendPacket(payload);
            return c.recvPacket();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Device buildDevice(NSDictionary properties) {
        Device deviceAttach = new Device();
        if (properties != null) {
            deviceAttach.serialNumber = properties.get("SerialNumber").toString();
            deviceAttach.connectionType = properties.get("ConnectionType").toString();
            deviceAttach.deviceId = Integer.valueOf(properties.get("DeviceID").toString());
        }
        return deviceAttach;
    }


    public Device findDevice(boolean network) throws NoMuxDeviceFound {
        NSArray devices = (NSArray) getDeviceList().get("DeviceList");
        NSObject[] deviceList = devices.getArray();
        if (deviceList.length<=0){
            throw new NoMuxDeviceFound("not found Device:");
        }
        NSDictionary device= (NSDictionary) deviceList[0];
        NSDictionary properties = (NSDictionary) device.get("Properties");

        return buildDevice(properties);
    }

    public Device findDevice(String serial, boolean network) throws NoMuxDeviceFound {
        NSArray devices = (NSArray) getDeviceList().get("DeviceList");
        for (NSObject d:devices.getArray()) {
            NSDictionary device = (NSDictionary) d;
            NSDictionary Properties= (NSDictionary) device.get("Properties");
            String SerialNumber= Properties.get("SerialNumber").toString();
            if (serial.equals(SerialNumber)){
                return buildDevice(Properties);
            }
        }
        throw new NoMuxDeviceFound("not found serial:"+serial);
    }

    public PlistSocket deviceConnect(Integer deviceId, int port) throws MuxError {
        PlistSocket c = createConnection();
        NSDictionary root = new NSDictionary();
        root.put("MessageType", "Connect");
        root.put("ClientVersionString", "libusbmuxd");
        root.put("ProgName", "Proto.PROGRAM_NAME");
        root.put("DeviceID", new NSNumber(deviceId));
        root.put("PortNumber", new NSNumber(((port << 8) & 0xFF00) | (port >> 8)));
        c.sendPacket(root);
        NSDictionary res = c.recvPacket();
        NSNumber number = (NSNumber) res.get("Number");
        if (number.intValue()!=0){
            throw new MuxError(String.format("Connect failed: error %d", number.intValue()));
        }
        log.info("Connecting Device :"+deviceId+" port:"+port);
        return c;
    }


    public NSDictionary getDeviceList(){
        NSDictionary root = new NSDictionary();
        root.put("MessageType","ListDevices");
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName", Proto.PROGRAM_NAME);
        root.put("kLibUSBMuxVersion",3);
        return sendRecv(root);
    }

    public String readSystemBUID(){
        NSDictionary root = new NSDictionary();
        root.put("MessageType","ReadBUID");
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName",Proto.PROGRAM_NAME);
        root.put("kLibUSBMuxVersion",3);
        NSDictionary data = sendRecv(root);
        return data.get("BUID").toString();
    }

    public NSDictionary getPairRecord(String udid){
        NSDictionary root = new NSDictionary();
        root.put("MessageType","ReadPairRecord");
        root.put("PairRecordID",udid);
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName",Proto.PROGRAM_NAME);
        root.put("kLibUSBMuxVersion",3);
        return sendRecv(root);
    }

    public NSDictionary deletePairRecord(String udid){
        NSDictionary root = new NSDictionary();
        root.put("MessageType","DeletePairRecord");
        root.put("PairRecordID",udid);
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName",Proto.PROGRAM_NAME);
        root.put("kLibUSBMuxVersion",3);
        return sendRecv(root);
    }

    public NSDictionary savePairRecord(String udid,NSDictionary pairRecord,int deviceID){
        NSDictionary root = new NSDictionary();
        root.put("MessageType","SavePairRecord");
        root.put("PairRecordData",pairRecord);
        root.put("DeviceID",deviceID);
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName",Proto.PROGRAM_NAME);
        root.put("kLibUSBMuxVersion",3);
        return sendRecv(root);
    }

}

