package util.pblist.exception;

public class UnsupportedArchiverException extends NSArchiverException {
    public UnsupportedArchiverException(String s) {
        super(String.format("unsupported encoder{}",s));
    }

}

