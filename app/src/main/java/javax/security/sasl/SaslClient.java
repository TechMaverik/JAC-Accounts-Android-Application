package javax.security.sasl;

/** Minimal stand-in for the JDK interface, which is missing on Android. See {@link SaslException}. */
public interface SaslClient {
    String getMechanismName();
    boolean hasInitialResponse();
    byte[] evaluateChallenge(byte[] challenge) throws SaslException;
    boolean isComplete();
    byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException;
    byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException;
    Object getNegotiatedProperty(String propName);
    void dispose() throws SaslException;
}
