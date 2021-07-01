import servers.SyslogService;

public class Main {
    public static void main(String[] args) throws Exception {
        new SyslogService(false).watch();
    }
}

