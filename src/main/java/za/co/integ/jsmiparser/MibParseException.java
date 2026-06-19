package za.co.integ.jsmiparser;

/** Thrown when MIB text cannot be lexed, parsed, or semantically resolved. */
public class MibParseException extends Exception {

    public MibParseException(String message) {
        super(message);
    }

    public MibParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
