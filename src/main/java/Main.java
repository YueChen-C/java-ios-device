import protocol.LockDown;
import servers.SyslogService;

import java.util.UUID;

public class Main {
    public static void main(String[] args) throws Exception {
        new SyslogService(false).watch();
//        new LockDown("00008030-001A690E2EC3802E",false).pairFull();
//        System.out.print(UUID.randomUUID());
    }
}

