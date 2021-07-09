package util.pblist;

import com.dd.plist.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
class NSArchiveImpl {
    public Object decodeArchive(ArchivedObject archivedObject){ return null; }
    public void encodeArchive(Object object,ArchivingObject archivedObject){}
}

/**
 * 处理不同类型数据编解码数据
 */
public class NsKeysClass {

    static class DictArchive extends NSArchiveImpl {

        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            NSArray keyUids = (NSArray) archivedObject.decode("NS.keys");
            NSArray valUids = (NSArray) archivedObject.decode("NS.objects");
            HashMap<Object, Object>  data = new HashMap<>();
            int count = keyUids.count();
            for (int i = 0; i < count; i++) {
                Object key =  archivedObject.decodeIndex((UID) keyUids.objectAtIndex(i));
                Object val =  archivedObject.decodeIndex((UID) valUids.objectAtIndex(i));
                data.put(key,val);
            }
            return data;
        }


    }

    static class ListArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            NSArray uids = (NSArray) archivedObject.decode("NS.objects");
            List<Object> data = new ArrayList<>();
            for (NSObject d:uids.getArray()) {
                UID uid=(UID) d;
                data.add(archivedObject.decodeIndex(uid));
            }
            return data;
        }


    }
    static class MutableDataArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return archivedObject.decode("NS.data");
        }
    }

    static class MutableStringArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return archivedObject.decode("NS.string");
        }
    }
    static class NSNull extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return null;
        }
    }

    static class ErrorArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){

            Object domain =  archivedObject.decode("NS.string");
            Object userInfo =  archivedObject.decode("NS.NSUserInfo");
            Object code =  archivedObject.decode("NS.NSCode");
            HashMap<String, Object>  data = new HashMap<>();
            data.put("$class","NSError");
            data.put("domain",domain);
            data.put("userInfo",userInfo);
            data.put("code",code);
            return data;
        }
    }
    static class ExceptionArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){

            Object name =  archivedObject.decode("NS.name");
            Object reason =  archivedObject.decode("NS.reason");
            Object userinfo =  archivedObject.decode("NS.userinfo");
            HashMap<String, Object>  data = new HashMap<>();
            data.put("$class","NSError");
            data.put("name",name);
            data.put("reason",reason);
            data.put("userinfo",userinfo);
            return data;
        }
    }
    static class DateArchive extends NSArchiveImpl {

        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return archivedObject.decode("NS.time");
        }
    }

    static class NSURL extends NSArchiveImpl {

        public String base;
        public String relative;

        public NSURL(String base, String relative){
            this.base = base;
            this.relative = relative;
        }
        public NSURL(String relative){
            this.base = null;
            this.relative = relative;
        }

        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return archivedObject.decode("NS.time");
        }

        @Override
        public void encodeArchive(Object object,ArchivingObject archivedObject){
            NSURL tmp = (NSURL) object;
            archivedObject.encode("NS.base",tmp.base );
            archivedObject.encode("NS.relative",tmp.relative );
        }
    }


    static class NSUUID extends NSArchiveImpl {
        private final byte[] bytes;
        private final long mostSigBits;
        private final long leastSigBits;

        private NSUUID(byte[] data) {
            long msb = 0;
            long lsb = 0;
            assert data.length == 16 : "data must be 16 bytes in length";
            for (int i=0; i<8; i++)
                msb = (msb << 8) | (data[i] & 0xff);
            for (int i=8; i<16; i++)
                lsb = (lsb << 8) | (data[i] & 0xff);
            this.mostSigBits = msb;
            this.leastSigBits = lsb;
            this.bytes = data;
        }

        public byte[] getBytes() {
            return bytes;
        }

        private static class Holder {
            static final SecureRandom numberGenerator = new SecureRandom();
        }
        private static String digits(long val, int digits) {
            long hi = 1L << (digits * 4);
            return Long.toHexString(hi | (val & (hi - 1))).substring(1);
        }
        public String toString() {
            return (digits(mostSigBits >> 32, 8) + "-" +
                    digits(mostSigBits >> 16, 4) + "-" +
                    digits(mostSigBits, 4) + "-" +
                    digits(leastSigBits >> 48, 4) + "-" +
                    digits(leastSigBits, 12));
        }


        public NSUUID randomUUID() {
            SecureRandom ng = NSUUID.Holder.numberGenerator;

            byte[] randomBytes = new byte[16];
            ng.nextBytes(randomBytes);
            randomBytes[6]  &= 0x0f;  /* clear version        */
            randomBytes[6]  |= 0x40;  /* set to version 4     */
            randomBytes[8]  &= 0x3f;  /* clear variant        */
            randomBytes[8]  |= 0x80;  /* set to IETF variant  */
            return new NSUUID(randomBytes);
        }

        public NSUUID nameUUIDFromBytes(byte[] name) {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException nsae) {
                throw new InternalError("MD5 not supported", nsae);
            }
            byte[] md5Bytes = md.digest(name);
            md5Bytes[6]  &= 0x0f;  /* clear version        */
            md5Bytes[6]  |= 0x30;  /* set to version 3     */
            md5Bytes[8]  &= 0x3f;  /* clear variant        */
            md5Bytes[8]  |= 0x80;  /* set to IETF variant  */
            return new NSUUID(md5Bytes);
        }

        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            NSData uuidbytes = (NSData) archivedObject.decode("NS.uuidbytes");
            return nameUUIDFromBytes(uuidbytes.bytes());
        }

        @Override
        public void encodeArchive(Object object,ArchivingObject archivedObject){
            NSUUID tmp = (NSUUID) object;
            archivedObject.encode("NS.uuidbytes",tmp.getBytes());
        }
    }

    static class XCTestConfigurationArchive extends NSArchiveImpl {

        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            NSData uuidbytes = (NSData) archivedObject.decode("NS.uuidbytes");
            return UUID.nameUUIDFromBytes(uuidbytes.bytes());
        }
    }

    static class XCActivityRecordArchive extends NSArchiveImpl {
        public  String[] keys = new String[]{"activityType", "attachments", "finish", "start", "title", "uuid"};
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            HashMap<String, Object> data = new HashMap<>();
            for (String key: keys) {
                data.put(key,archivedObject.decode(key));
            }
            return data;
        }
    }

    static class DTKTraceTapMessageArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            Object data="";
            if (archivedObject.object.get("$0")!=null){
                data=archivedObject.decode("$0");
            }else {
                data=archivedObject.decode("DTTapMessagePlist");
            }
            return data;
        }
    }

    static class XCTCapabilitiesArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return archivedObject.decode("capabilities-dictionary");
        }
    }

    static class DTTapHeartbeatMessageArchive extends NSArchiveImpl {
        @Override
        public Object decodeArchive(ArchivedObject archivedObject){
            return archivedObject.decode("DTTapMessagePlist");
        }
    }

    public static HashMap<String, Class<?>>  UNARCHIVE_CLASS_MAP = new HashMap<>();
    public static HashMap<Class<?>, String> ARCHIVE_CLASS_MAP = new HashMap<>();
    static { initUnarchiveClass(); initArchiveClass();}
    public static int NSKeyedArchiveVersion=100_000;

    public static class CycleToken{}

    public static void initUnarchiveClass(){
        UNARCHIVE_CLASS_MAP.put("NSDictionary",DictArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSMutableDictionary",DictArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSArray",ListArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSMutableArray",ListArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSSet",ListArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSMutableSet",ListArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSDate",DateArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSNull",NSNull.class);
        UNARCHIVE_CLASS_MAP.put("NSError",ErrorArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSException",ExceptionArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSMutableString",MutableStringArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSMutableData",MutableDataArchive.class);
        UNARCHIVE_CLASS_MAP.put("NSUUID", NSUUID.class);
        UNARCHIVE_CLASS_MAP.put("NSURL", NSURL.class);
        UNARCHIVE_CLASS_MAP.put("XCTestConfiguration",XCTestConfigurationArchive.class);
        UNARCHIVE_CLASS_MAP.put("XCActivityRecord",XCActivityRecordArchive.class);
        UNARCHIVE_CLASS_MAP.put("DTKTraceTapMessage",DTKTraceTapMessageArchive.class);
        UNARCHIVE_CLASS_MAP.put("XCTCapabilities",XCTCapabilitiesArchive.class);
        UNARCHIVE_CLASS_MAP.put("DTTapHeartbeatMessage",DTTapHeartbeatMessageArchive.class);
        UNARCHIVE_CLASS_MAP.put("DTSysmonTapMessage",DTTapHeartbeatMessageArchive.class);
    }


    public static void initArchiveClass(){
        ARCHIVE_CLASS_MAP.put(Map.class,"NSDictionary");
        ARCHIVE_CLASS_MAP.put(List.class,"NSArray");
        ARCHIVE_CLASS_MAP.put(Set.class,"NSSet");
//        ARCHIVE_CLASS_MAP.put(TimesTamp.class,"NSDictionary");
        ARCHIVE_CLASS_MAP.put(NSURL.class,"NSURL");
        ARCHIVE_CLASS_MAP.put(NSUUID.class,"NSUUID");
        ARCHIVE_CLASS_MAP.put(XCTestConfigurationArchive.class,"XCTestConfiguration");
    }


    public static void updateClassMap(HashMap<String, NSArchiveImpl> map){}
}


