package util.pblist;

import com.dd.plist.BinaryPropertyListWriter;
import com.dd.plist.NSDictionary;
import com.dd.plist.UID;
import util.pblist.exception.MissingClassMappingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static util.pblist.NsKeysClass.ARCHIVE_CLASS_MAP;
import static util.pblist.NsKeysClass.NSKeyedArchiveVersion;

public class NSArchive {

    public Class<?>[] primitiveTypes = {int.class, float.class, Boolean.class, String.class, byte.class, UID.class};
    public Class<?>[] inlineTypes = {int.class, float.class, Boolean.class};
    public Object inputObj;
    public HashMap<Object,Object> classMap= new HashMap<>();
    public HashMap<Object,Object> refMap= new HashMap<>();
    public ArrayList<Object> objects = new ArrayList<>();

    public boolean contains(Class<?>[] objectList,Object val){
        for (Class<?> obj:objectList) {
            if (val.equals(obj)){
                return true;
            }
        }
        return false;
    }


    public NSArchive(Object inputObj){
        this.inputObj=inputObj;
        objects.add("$null");
    }

    public UID uidForArchiver(Object archiver){
        UID val = (UID) classMap.get(archiver);
        if (val!=null){
            return val;
        }
        val = new UID("", new byte[]{1});
        classMap.put(archiver,val);
        HashMap<Object,Object> tmp = new HashMap<>();
        tmp.put("$classes", new Object[]{archiver});
        tmp.put("$classname", archiver);
        objects.add(tmp);
        return val;
    }

    public UID encode(Object val){
        Class<?> cls = val.getClass();
        if (contains(inlineTypes,cls)){
            return (UID) val;
        }
        return archive(val);
    }

    public UID archive(Object obj){

        if (obj==null){
            return new UID("", new byte[]{0});
        }

        Object ref = refMap.get(System.identityHashCode(obj));
        if (ref!=null){
            return (UID) obj;
        }
        UID index = new UID("",new BigInteger(String.valueOf(objects.size())).toByteArray());
        refMap.put(System.identityHashCode(obj),index);
        Class<?> cls = obj.getClass();
        if (contains(primitiveTypes,cls)){
            objects.add(obj);
            return index;
        }
        HashMap<Object, Object> archiveObjMap = new HashMap<>();
        objects.add(archiveObjMap);
        encodeTopLevel(obj, archiveObjMap);
        return null;

    }
    public void encodeList(List<?> objs,HashMap<Object, Object> archiveObjMap){
        UID archiverUid = uidForArchiver("NSArray");
        archiveObjMap.put("$class",archiverUid);
        ArrayList<Object> nsObjects = new ArrayList<>();
        for (Object obj:objs) {
            nsObjects.add(archive(obj));
        }
        archiveObjMap.put("NS.objects",nsObjects);
    }
    public void encodeMap(HashMap<Object, Object> obj,HashMap<Object, Object> archiveObjMap){
        UID archiverUid = uidForArchiver("NSDictionary");
        archiveObjMap.put("$class",archiverUid);
        ArrayList<Object> keys = new ArrayList<>();
        ArrayList<Object> values = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : obj.entrySet()) {
            keys.add(archive(entry.getKey()));
            values.add(archive(entry.getValue()));
        }
        archiveObjMap.put("NS.keys",keys);
        archiveObjMap.put("NS.objects",values);

    }
    public void encodeSet(Set<?> objs,HashMap<Object, Object> archiveObjMap){
        UID archiverUid = uidForArchiver("NSSet");
        archiveObjMap.put("$class",archiverUid);
        ArrayList<Object> nsObjects = new ArrayList<>();
        for (Object obj:objs) {
            nsObjects.add(archive(obj));
        }
        archiveObjMap.put("NS.objects",nsObjects);
    }

    public void encodeTopLevel(Object obj, HashMap<Object, Object> archiveObjMap ){

        try {
            if (obj instanceof List){
                encodeList((List<?>) obj, archiveObjMap);
            }
            else if (obj instanceof Map){
                encodeMap((HashMap) obj, archiveObjMap);
            }
            else if (obj instanceof Set){
                encodeSet((Set<?>) obj, archiveObjMap);
            }else { // 其他类型使用自定义编码
                Class<?> cls = obj.getClass();

                String archiver = ARCHIVE_CLASS_MAP.get(cls);
                if (archiver==null){
                    throw new MissingClassMappingException(obj.toString());
                }
                UID archiver_uid = uidForArchiver(archiver);
                archiveObjMap.put("$class",archiver_uid);

                ArchivingObject archiveWrapper = new ArchivingObject(archiveObjMap, this);
                NSArchiveImpl ArchiveObj= (NSArchiveImpl) obj;
                ArchiveObj.encodeArchive(obj, archiveWrapper);
            }

        } catch (Exception e){
            e.printStackTrace();

        }


    }
    public byte[] toBytes() throws IOException {
        if (objects.size()==1){
            archive(inputObj);
        }

        NSDictionary data = new NSDictionary();
        data.put("$archiver","NSKeyedArchiver");
        data.put("$version",NSKeyedArchiveVersion);
        data.put("$objects",objects);

        NSDictionary root = new NSDictionary();
        root.put("UID", new UID("", new byte[]{0}));

        data.put("$top",root);
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        BinaryPropertyListWriter.write(fos,data);
        byte[] bytes = fos.toByteArray();
        fos.close();
        return bytes;

    }

}

