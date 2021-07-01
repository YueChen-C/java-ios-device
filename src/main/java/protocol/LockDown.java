package protocol;

import com.dd.plist.*;
import lombok.extern.log4j.Log4j;
import protocol.model.Device;
import util.Version;
import exception.lockdown.InitializationError;
import exception.lockdown.NotPairedError;
import exception.lockdown.StartServiceError;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

@Log4j
public class LockDown {
    public PlistSocket svc;
    public Integer deviceID;
    public String productVersion;
    public String uniqueDeviceID;
    public boolean network;
    public NSDictionary record;
    public String hostId;
    public String sessionId;
    public File sslFile;
    public boolean paired;

    public LockDown(String serial, boolean network) throws Exception {
        this.network = network;
        Device device = new UsbMux().findDevice(serial,network);
        init(device);
    }
    public LockDown(boolean network) throws Exception {
        this.network = network;
        Device device = new UsbMux().findDevice(network);
        init(device);
    }
    protected void init(Device device) throws Exception {
        deviceID = device.deviceId;
        svc = device.connect();
        verifyQueryType();
        getValue();
        paired=Pair();
    }

    public PlistSocket startService(String name) throws Exception {
        if (!paired){
            throw new NotPairedError("Unable to start service={name!r} - not paired");
        }
        NSData escrow_bag = (NSData) record.get("EscrowBag");
        NSDictionary root = new NSDictionary();
        root.put("Request","StartService");
        root.put("Service",name);
        root.put("EscrowBag",escrow_bag);
        NSDictionary resp = svc.sendRecvPacket(root);
        if (resp==null){
            throw new StartServiceError("Unable to start service={name!r} - not paired");
        }
        if (resp.containsValue("Error")){
            throw new StartServiceError("Unable to start service={name!r} - "+resp.get("Error").toString());
        }
        NSNumber Port = (NSNumber) resp.get("Port");
        NSNumber EnableServiceSSL = (NSNumber) resp.get("EnableServiceSSL");
        PlistSocket plistSocket = new UsbMux().deviceConnect(deviceID,Port.intValue());
        if (EnableServiceSSL.isBoolean()){
            plistSocket.sslStart(sslFile.getPath());
        }
        return plistSocket;

    }
    protected void  verifyQueryType() throws InitializationError {
        NSDictionary root = new NSDictionary();
        root.put("Request","QueryType");
        NSDictionary data = svc.sendRecvPacket(root);
        NSString queryType= (NSString) data.get("Type");
        if (!queryType.toString().equals("com.apple.mobile.lockdown")){
            throw new InitializationError("Unexpected"+queryType);
        }
    }
    protected boolean Pair() throws Exception {
        if (validatePairing()!=null){
            return true;
        }
        pairFull();
        svc.close();
        svc = new UsbMux().deviceConnect(deviceID,62078);
        if (validatePairing()!=null){
            return true;
        }
        return false;
    }

    protected NSDictionary pairFull(){
        return null;
    }

    protected NSDictionary validatePairing(){ // 进行校验配对
        NSDictionary pairRecord = getPairRecord();
        if (pairRecord==null){
            return null;
        }
        record = pairRecord;

        if (!new Version(productVersion).compareTo(new Version("11.0"))){ // 11 以下需要双向认证
            NSDictionary root = new NSDictionary();
            root.put("Request","ValidatePair");
            root.put("PairRecord",pairRecord);
            NSDictionary data = svc.sendRecvPacket(root);
            if (data == null || data.containsKey("Error")){
                return null;
            }
        }

        hostId = pairRecord.get("HostID").toString();
        String systemBUID = pairRecord.get("SystemBUID").toString();
        NSDictionary root = new NSDictionary();
        root.put("Request","StartSession");
        root.put("HostID",hostId);
        root.put("SystemBUID",systemBUID);
        NSDictionary resp = svc.sendRecvPacket(root);
        sessionId = resp.get("SessionID").toString();
        if (resp.containsKey("Error")){
            if (resp.get("Error").toString().equals("InvalidHostID")){ //  InvalidHostID 乱了的话重置一下
                new UsbMux().deletePairRecord(uniqueDeviceID);
                pairRecord = getPairRecord();
                if (pairRecord == null){
                    pairRecord = pairFull();
                }
                return pairRecord;
            }
        }

        if (resp.containsKey("EnableSessionSSL")){
            NSData NSHostCertificate = (NSData) pairRecord.get("HostCertificate");
            NSData HostPrivateKey = (NSData) pairRecord.get("HostPrivateKey");
            byte[] allByteArray = new byte[HostPrivateKey.bytes().length + 1 + NSHostCertificate.bytes().length];
            ByteBuffer buff = ByteBuffer.wrap(allByteArray);
            buff.put(NSHostCertificate.bytes());
            buff.put("\n".getBytes());
            buff.put(HostPrivateKey.bytes());
            sslFile = writeCachePair(uniqueDeviceID, buff.array());
            try {
                svc.sslStart(sslFile.getPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return pairRecord;
    }

    protected NSDictionary getPairRecord(){
        File itunesLockdownPath = new File(getLockdownPath(),uniqueDeviceID+ ".plist");
        try {
            return (NSDictionary) PropertyListParser.parse(itunesLockdownPath);
        } catch (Exception e) {
            return new UsbMux().getPairRecord(uniqueDeviceID);
        }
    }

    public void getValue(){
        NSDictionary root = new NSDictionary();
        root.put("Request","GetValue");
        NSDictionary data = svc.sendRecvPacket(root);
        NSDictionary value= (NSDictionary) data.get("Value");
        productVersion = value.get("ProductVersion").toString();
        uniqueDeviceID = value.get("UniqueDeviceID").toString();
    }

    public NSDictionary getValue(String domain){
        NSDictionary root = new NSDictionary();
        root.put("Request","GetValue");
        root.put("Domain",domain);
        NSDictionary data = svc.sendRecvPacket(root);
        return (NSDictionary) data.get("Value");
    }

    public NSDictionary getValue(String domain,String key){
        NSDictionary root = new NSDictionary();
        root.put("Request","GetValue");
        root.put("Domain",domain);
        root.put("Key",key);
        NSDictionary data = svc.sendRecvPacket(root);
        return (NSDictionary) data.get("Value");
    }

    static File getLockdownPath() {
        String property = System.getProperty("os.name");
        if (property.startsWith("Window")) {
            return new File(System.getenv().get("ALLUSERSPROFILE") + "/Apple/Lockdown/");
        } else {
            return new File("/var/db/lockdown/");
        }
    }

    static NSDictionary getCachePlist(String uniqueDeviceID) {
        try {
            File home = getHomeFile();
            File cache = new File(home,uniqueDeviceID+ ".plist");
            return (NSDictionary) PropertyListParser.parse(cache);
        } catch (Exception e){
            return null;
        }
    }

    static File writeCachePair(String uniqueDeviceID,byte[] data) {
        try {
            File home = getHomeFile();
            File cache = new File(home,uniqueDeviceID+ ".pem");
            try (FileOutputStream fileOutSt = new FileOutputStream(cache)) {
                fileOutSt.write(data);
            }
            return cache;
        } catch (Exception e){
            return null;
        }
    }

    static File getHomeFile() {
        return new File(System.getProperty("user.home"), ".cache/pymobiledevice");
    }




}
