package util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteUtil {

    public static ByteBuffer buildByteBigStream(byte[] res){
        ByteBuffer data = ByteBuffer.allocate(res.length);
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.put(res);
        return data;
    }

    public static ByteBuffer buildByteBigStream(byte[] res,ByteOrder byteOrder){
        ByteBuffer data = ByteBuffer.allocate(res.length);
        data.order(byteOrder);
        data.put(res);
        return data;
    }

    public static ByteBuffer buildByteBigStream(int length){
        ByteBuffer data = ByteBuffer.allocate(length);
        data.order(ByteOrder.LITTLE_ENDIAN);
        return data;
    }

    public static ByteBuffer buildByteBigStream(int length,ByteOrder byteOrder){
        ByteBuffer data = ByteBuffer.allocate(length);
        data.order(byteOrder);
        return data;
    }
}


