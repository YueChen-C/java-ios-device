package util.pblist;

import com.dd.plist.NSDictionary;
import com.dd.plist.UID;

public class ArchivedObject {
    public NSDictionary object;
    public NSUnarchive unarchive;


    public Object decodeIndex(UID index ){
        return unarchive.decodeObject(index);
    }
    public Object decode(String key){
        return unarchive.decodeKey(object,key);
    }

    public ArchivedObject(NSDictionary object, NSUnarchive unarchive){
        this.object=object;
        this.unarchive=unarchive;

    }


}



