package protocol;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import lombok.extern.log4j.Log4j;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import protocol.model.UsbMuxConnection;
import util.ByteUtil;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static util.PEMImporter.createSSLFactory;

@Log4j
public class PlistSocket implements AutoCloseable{
    public Socket connectionSocket;
    private final UsbMuxConnection usbMuxSock = new UsbMuxConnection();
    private boolean first = true;
    private final int tag;

    public PlistSocket(int tag) {
        init();
        this.tag = tag;
    }
    public PlistSocket(PlistSocket data) {
        init();
        this.tag = data.tag;
        this.first = data.first;
    }

    public void init() {
        String property = System.getProperty("os.name");
        try {
            if (property.startsWith("Window")) {
                connectionSocket = new Socket();
                connectionSocket.connect(new InetSocketAddress("127.0.0.1", 27015));
            } else {
                connectionSocket = AFUNIXSocket.newInstance();
                connectionSocket.connect(new AFUNIXSocketAddress(new File("/var/run/usbmuxd")));
            }
            usbMuxSock.inputStream = connectionSocket.getInputStream();
            usbMuxSock.outputStream = connectionSocket.getOutputStream();
        } catch (IOException e) {
            connectionSocket=null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sslStart(String privateKeyPem) throws Exception {
        SSLSocketFactory sslSocketFactory = createSSLFactory(new File(privateKeyPem));
        SSLSocket connectionSocket1 = (SSLSocket) sslSocketFactory.createSocket(connectionSocket, usbMuxSock.inputStream, true);
        connectionSocket1.setUseClientMode(true);
        usbMuxSock.inputStream = connectionSocket1.getInputStream();
        usbMuxSock.outputStream = connectionSocket1.getOutputStream();
    }

    protected NSDictionary decodeByteMsg(byte[] bytes) {
        try {
            NSObject data = PropertyListParser.parse(bytes);
            log.debug(data.toXMLPropertyList());
            return (NSDictionary) data;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public NSDictionary sendPacket(NSDictionary payload){
        try {
            ByteBuffer buffer;
            String data = payload.toXMLPropertyList();
            byte[] bytes=data.getBytes(StandardCharsets.UTF_8);
            if (this.first){
                int len = (16 + bytes.length);
                buffer = ByteUtil.buildByteBigStream(len);
                buffer.putInt(0, len);
                buffer.putInt(4, 1);// version
                buffer.putInt(8, 8);  //  request
                buffer.putInt(12, tag);
                int i = 16;
                for (byte aByte : bytes) {
                    buffer.put(i++, aByte);
                }
            }else{
                buffer = ByteUtil.buildByteBigStream(4+bytes.length, ByteOrder.BIG_ENDIAN);
                buffer.putInt(0,bytes.length);
                int i = 4;
                for (byte aByte : bytes) {
                    buffer.put(i++, aByte);
                }
            }
            sendAll(buffer.array());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return payload;
    }

    public NSDictionary recvPacket() {
        try {
            int length = 0;
            if (this.first){
                byte[] header = recvAll(16);
                ByteBuffer buffer = ByteUtil.buildByteBigStream(header);
                length = buffer.getInt(0);
                length -= 16;
                this.first = false;
            }else {
                byte[] header  = recvAll(4);
                ByteBuffer buffer = ByteUtil.buildByteBigStream(header,ByteOrder.BIG_ENDIAN);
                length = buffer.getInt(0);
            }
            byte[] body_data = recvAll(length);
            return decodeByteMsg(body_data);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public NSDictionary sendRecvPacket(NSDictionary payload){
        sendPacket(payload);
        return recvPacket();

    }

    private  void sendAll(byte[] data) throws IOException {
        usbMuxSock.outputStream.write(data);
    }

    public byte[] recvAll(int size) throws IOException {
        int readBytes=0;
        byte[] data= new byte[size];
        int len = data.length;
        while (readBytes < len) {
            int read = 0;
            read = usbMuxSock.inputStream.read(data, readBytes, len - readBytes);
            if (read==-1){
                break;
            }
            readBytes += read;
        }
        return data;
    }

    protected DataInputStream decodeByte(byte[] data){
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        return new DataInputStream(bais);
    }


    @Override
    public void close() throws Exception {
        connectionSocket.close();

    }

}

