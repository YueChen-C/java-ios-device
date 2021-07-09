package util.pblist;

import com.dd.plist.*;

import java.io.IOException;


/**
 *     Capable of unpacking an archived object tree in the NSKeyedArchive format.
 *
 *     Apple's implementation can be found here:
 *     https://github.com/apple/swift-corelibs-foundation/blob/master/Foundation\
 *     /NSKeyedUnarchiver.swift
 *
 */

public class NSKeyedArchiver {

    public static Object Unarchive(byte[] input){
        return new NSUnarchive(input).toObject();
    }

    public static byte[] Archive(Object inputObj) throws IOException {
        return new NSArchive(inputObj).toBytes();
    }


}

