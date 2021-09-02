package protocol;

import com.dd.plist.*;
import lombok.extern.log4j.Log4j;
import org.xml.sax.SAXException;
import protocol.model.Device;
import exception.MuxError;
import exception.NoMuxDeviceFound;
import util.Proto;
import util.pblist.NSKeyedArchiver;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;

@Log4j
public class UsbMux {
    private int tag = 0;
    private Object ArrayList;

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
        java.util.ArrayList<Device> devices = getDeviceList(network);
        if (devices.size()<=0){
            throw new NoMuxDeviceFound("not found Device:");
        }
        return devices.get(0);
    }

    public Device findDevice(String serial, boolean network) throws NoMuxDeviceFound {
        java.util.ArrayList<Device> devices = getDeviceList(network);
        for (Device device:devices) {
            if (serial.equals(device.serialNumber)){
                return device;
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


    public java.util.ArrayList<Device> getDeviceList(boolean network){
        NSDictionary root = new NSDictionary();
        root.put("MessageType","ListDevices");
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName", Proto.PROGRAM_NAME);
        root.put("kLibUSBMuxVersion",3);
        NSDictionary data = sendRecv(root);

        NSArray DeviceList = (NSArray) data.get("DeviceList");
        ArrayList<Device> devices = new ArrayList<>();
        for (NSObject d:DeviceList.getArray()) {
            NSDictionary deviceData = (NSDictionary) d;
            NSDictionary Properties= (NSDictionary) deviceData.get("Properties");
            String ConnectionType= Properties.get("ConnectionType").toString();
            if (!network && ConnectionType.equals("Network")){
                continue;
            }
            devices.add(buildDevice(Properties));
        }
        return devices;
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
        try {
            NSDictionary root = new NSDictionary();
            root.put("MessageType","ReadPairRecord");
            root.put("PairRecordID",udid);
            root.put("ClientVersionString","libusbmuxd");
            root.put("ProgName",Proto.PROGRAM_NAME);
            root.put("kLibUSBMuxVersion",3);
            NSDictionary data =  sendRecv(root);
            NSData PairRecordData = (NSData) data.get("PairRecordData");
            return (NSDictionary) PropertyListParser.parse(PairRecordData.bytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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

        String data = pairRecord.toXMLPropertyList();
        byte[] bytes=data.getBytes(StandardCharsets.UTF_8);
        NSDictionary root = new NSDictionary();
        root.put("MessageType","SavePairRecord");
        root.put("PairRecordData",bytes);
        root.put("DeviceID",deviceID);
        root.put("ClientVersionString","libusbmuxd");
        root.put("ProgName",Proto.PROGRAM_NAME);
        root.put("PairRecordID",udid);
        return sendRecv(root);
    }

}

