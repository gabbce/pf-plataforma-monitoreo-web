package ar.edu.utn.frsf.pfc.management.station;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class StationToken {

    static final int LENGTH = 64;

    private StationToken() {
    }

    static boolean isValid(byte[] token) {
        if (token == null || token.length != LENGTH) {
            return false;
        }
        for (byte character : token) {
            if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    static byte[] digest(byte[] token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
