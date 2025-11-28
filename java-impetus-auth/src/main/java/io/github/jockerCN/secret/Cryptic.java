package io.github.jockerCN.secret;

import io.github.jockerCN.common.SpringProvider;

import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface Cryptic {

    byte[] decrypt(Object data) throws Exception;

    byte[] encrypt(Object data) throws Exception;

    boolean support(Class<?> data);

    default String decryptAsString(Object data) throws Exception {
        return new String(decrypt(data));
    }

    default String encryptAsString(Object data) throws Exception {
        return new String(encrypt(data));
    }


    static Cryptic getInstance(Class<?> obj) {
        Collection<Cryptic> cryptos = SpringProvider.getBeans(Cryptic.class);
        for (Cryptic crypto : cryptos) {
            if (crypto.support(obj)) {
                return crypto;
            }
        }
        return DEFAULT_CRYPTIC;
    }

    Cryptic DEFAULT_CRYPTIC = new Cryptic() {

        @Override
        public byte[] decrypt(Object data) throws Exception {
            return CryptoProvider.simpleDecrypt(data.toString());
        }

        @Override
        public byte[] encrypt(Object data) throws Exception {
            return CryptoProvider.simpleEncrypt(data.toString());
        }

        @Override
        public boolean support(Class<?> data) {
            return true;
        }

        @Override
        public String encryptAsString(Object data) throws Exception {
            return CryptoProvider.simpleEncryptAsString(data.toString());
        }

        @Override
        public String decryptAsString(Object data) throws Exception {
            return CryptoProvider.simpleDecryptAsString(data.toString());
        }
    };


}

