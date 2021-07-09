package util.pblist;

import java.util.HashMap;

public class ArchivingObject{

    public HashMap<Object,Object> archiveObj;
    public NSArchive archiver;

    public ArchivingObject(HashMap<Object,Object> archiveObj,NSArchive archiver){
        this.archiveObj=archiveObj;
        this.archiver=archiver;

    }
    public void encode(Object key,Object val){
        val = archiver.encode(val);
        archiveObj.put(key,val);

    }

}