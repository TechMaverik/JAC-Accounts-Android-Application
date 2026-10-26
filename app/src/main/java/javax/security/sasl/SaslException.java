package javax.security.sasl;

import java.io.IOException;

/**
 * Android does not ship javax.security.sasl, but the MongoDB driver's authenticator
 * (SCRAM) is built on it. This is the minimal API surface the driver needs.
 */
public class SaslException extends IOException {
    public SaslException() { super(); }
    public SaslException(String detail) { super(detail); }
    public SaslException(String detail, Throwable ex) { super(detail, ex); }
}
