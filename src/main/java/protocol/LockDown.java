package protocol;

import com.dd.plist.*;
import exception.lockdown.InitializationError;
import exception.lockdown.StartServiceError;
import lombok.extern.log4j.Log4j;
import protocol.model.Device;
import util.Cart;
import util.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.cert.CertificateParsingException;

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
    public Device device;

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
        this.device = device;
        deviceID = device.deviceId;
        uniqueDeviceID = device.serialNumber;
        svc = device.connect();
        verifyQueryType();
        getValue();
    }

    public PlistSocket startService(String name) throws Exception {
        if (!paired){
            paired=Pair();
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

    /**
     * 证书重新配对
     * @return pairRecord 证书信息
     * @throws Exception
     */
    public NSDictionary pairFull() throws Exception {
        log.info(" DevicePublicKey pair full");
        NSData devicePublicKey = (NSData) getValueKey("DevicePublicKey");
        NSString wifiAddress = (NSString) getValueKey("WiFiAddress");
        String systemBUID = new UsbMux().readSystemBUID();

        if (devicePublicKey==null){
            log.error("Unable to retrieve DevicePublicKey");
        }
        log.debug("Creating host key & certificate");

        String devicePublicKeyStr = new String(devicePublicKey.bytes(),"UTF-8");
        Cart cart =new Cart().createCert(devicePublicKeyStr);

        NSDictionary pairRecord = new NSDictionary(); //  PublicKey
        pairRecord.put("DevicePublicKey", devicePublicKeyStr.getBytes());
        pairRecord.put("DeviceCertificate",cart.devCertPem.getBytes());
        pairRecord.put("HostCertificate",cart.certPem.getBytes());
        pairRecord.put("HostID","22D256B8-132D-3CEF-96D4-B1852CCC8117");
        pairRecord.put("RootCertificate",cart.certPem.getBytes());
        pairRecord.put("SystemBUID",systemBUID);

        NSDictionary pairingOptions = new NSDictionary();
        pairingOptions.put("ExtendedPairingErrors",true);

        NSDictionary root = new NSDictionary(); // Pair pull
        root.put("Request","Pair");
        root.put("PairRecord",pairRecord);
        root.put("ProtocolVersion","2");
        root.put("PairingOptions",pairingOptions);

        NSDictionary resp = svc.sendRecvPacket(root);

        if (resp!=null &&  resp.containsKey("EscrowBag")){
            pairRecord.put("HostPrivateKey", cart.publicKeyPem.getBytes());
            pairRecord.put("EscrowBag",resp.get("EscrowBag"));
            pairRecord.put("WiFiMACAddress",wifiAddress.toString());
            new UsbMux().savePairRecord(uniqueDeviceID,pairRecord,deviceID);
            sslFile = writeCachePair(uniqueDeviceID, pairRecord);
            return pairRecord;
        }else if(resp!=null && resp.get("Error")!=null){
            svc.close();
            log.error(resp.get("Error").toString());
        }
        return null;
    }

    protected NSDictionary validatePairing() throws Exception { // 进行校验配对
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
        sessionId = resp.get("SessionID").toString();

        if (resp.containsKey("EnableSessionSSL")){
            sslFile = writeCachePair(uniqueDeviceID, pairRecord);
            try {
                svc.sslStart(sslFile.getPath());
            } catch (CertificateParsingException ignored){
                svc = device.connect();
                log.error("ssl Handshake error");
                return null;
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
    }

    public NSDictionary getValue(String domain,String key){
        NSDictionary root = new NSDictionary();
        root.put("Request","GetValue");
        root.put("Domain",domain);
        NSDictionary data = svc.sendRecvPacket(root);
        return (NSDictionary) data.get("Value");
    }

    public NSObject getValueKey(String key){
        NSDictionary root = new NSDictionary();
        root.put("Request","GetValue");
        root.put("Key",key);
        NSDictionary data = svc.sendRecvPacket(root);
        return data.get("Value");
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

    static File writeCachePair(String uniqueDeviceID,NSDictionary pairRecord) {
        NSData NSHostCertificate = (NSData) pairRecord.get("HostCertificate");
        NSData HostPrivateKey = (NSData) pairRecord.get("HostPrivateKey");
        byte[] allByteArray = new byte[HostPrivateKey.bytes().length + 1 + NSHostCertificate.bytes().length];
        ByteBuffer buff = ByteBuffer.wrap(allByteArray);
        buff.put(NSHostCertificate.bytes());
        buff.put("\n".getBytes());
        buff.put(HostPrivateKey.bytes());

        try {
            File home = getHomeFile();
            File cache = new File(home,uniqueDeviceID+ ".pem");
            try (FileOutputStream fileOutSt = new FileOutputStream(cache)) {
                fileOutSt.write(buff.array());
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
