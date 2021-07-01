package servers;

import protocol.LockDown;
import protocol.PlistSocket;

import java.io.IOException;

public class SyslogService {

    private static final String SERVICE_NAME = "com.apple.syslog_relay";
    private final PlistSocket serve;

    public SyslogService(boolean network) throws Exception {
        LockDown lockDown =new LockDown(network);
        serve = lockDown.startService(SERVICE_NAME);
    }

    public void watch() throws IOException {
        while (true){
            byte[] data=serve.recvAll(4096);
            if (data.length==0){
                break;
            }
            System.out.print( new String(data));

        }

    }
}

