package util.pblist;

import com.dd.plist.*;
import util.pblist.exception.*;

import java.math.BigInteger;
import java.util.HashMap;

import static util.pblist.NsKeysClass.UNARCHIVE_CLASS_MAP;

public class NSUnarchive {
    public Object[] objects;
    public UID topUid;
    public byte[] input;
    public HashMap<UID,Object> unpackedUid=new HashMap<>();

    /**
     * 检查头部数据是否正确
     */
    public void unpackArchiveHeader () {
        try {
            NSDictionary plist = (NSDictionary) BinaryPropertyListParser.parse(input);
            String archiver = plist.get("$archiver").toString();
            if (!archiver.equals("NSKeyedArchiver")){
                throw new UnsupportedArchiverException(archiver);
            }
            NSNumber version = (NSNumber) plist.get("$version");
            if(version.intValue() != NsKeysClass.NSKeyedArchiveVersion){
                throw new UnsupportedArchiveVersionException(version.toString());
            }
            NSDictionary top = (NSDictionary) plist.get("$top");
            if (top==null){
                throw new MissingTopObjectException(plist.toXMLPropertyList());
            }

            UID root = (UID) top.get("root");
            if (root==null){
                throw new MissingTopObjectUIDException(top.toXMLPropertyList());
            }
            this.topUid = root;

            NSArray tmpObjects = (NSArray) plist.get("$objects");

            if (tmpObjects ==null){
                throw new MissingObjectsArrayException(plist.toXMLPropertyList());
            }else {
                objects = tmpObjects.getArray();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public NSUnarchive(byte[] input) {
        this.input = input;

    }

    public Object toObject(){
        unpackArchiveHeader();
        return decodeObject(topUid);
    }

    public Object decodeObject(UID index){
        try {
            Object obj = unpackedUid.get(index);
            if (obj instanceof NsKeysClass.CycleToken){
                throw new CircularReferenceException(obj.toString());
            }
            if (obj!=null){
                return obj;
            }
            BigInteger unm= new BigInteger(1,index.getBytes());

            Object rawObj = objects[unm.intValue()];
            unpackedUid.put(topUid,new NsKeysClass.CycleToken());

            // 遇到基础类型返回否则一直递归
            if (!(rawObj.getClass().equals(NSObject.class)) && !(rawObj.getClass().equals(NSDictionary.class))){
                unpackedUid.put(topUid,obj);
                return rawObj;
            }

            NSDictionary data = (NSDictionary) rawObj;
            UID classUid = (UID) data.get("$class");
            if (classUid==null){
                throw new MissingClassUIDException(data.toString());
            }

            NSArchiveImpl klass = (NSArchiveImpl) classForUid(classUid).newInstance();
            obj = klass.decodeArchive(new ArchivedObject(data,this)); // 递归处理数据
            unpackedUid.put(topUid,obj);
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public Object decodeKey(NSDictionary obj, String key){

        Object val = obj.get(key);
        if (val instanceof UID) {
            return decodeObject((UID) val);
        }
        return val;
    }


    /**
     * 根据 uid 位置取出 objects 对应 class 类型进行解码
     * @param index uid 位置
     * @return
     */
    public Class<?> classForUid(UID index){
        try {
            BigInteger unm= new BigInteger(1,index.getBytes());

            if (objects[unm.intValue()] == null){
                throw new MissingClassUIDException(index.toString());
            }
            NSDictionary meta = (NSDictionary) objects[unm.intValue()];
            NSString name = (NSString) meta.get("$classname");
            Class<?> klass = UNARCHIVE_CLASS_MAP.get(name.toString());
            if (klass==null){
                throw new MissingClassUIDException(name.toString());
            }
            return klass;
        }catch (Exception e){
            e.printStackTrace();

        }
        return null;
    }

}