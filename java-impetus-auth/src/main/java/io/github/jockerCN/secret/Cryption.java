package io.github.jockerCN.secret;

import io.github.jockerCN.common.SpringProvider;

import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface Cryption {

    byte[] decrypt(Object data) throws Exception;

    byte[] encrypt(Object data) throws Exception;

    boolean support(Class<?> data);

    default String decryptAsString(Object data) throws Exception {
        return new String(decrypt(data));
    }

    default String encryptAsString(Object data) throws Exception {
        return new String(encrypt(data));
    }


    static Cryption getInstance(Class<?> obj) {
        Collection<Cryption> cryptos = SpringProvider.getBeans(Cryption.class);
        for (Cryption crypto : cryptos) {
            if (crypto.support(obj)) {
                return crypto;
            }
        }
        return defaultCryption;
    }

    Cryption defaultCryption = new Cryption() {

        @Override
        public byte[] decrypt(Object data) throws Exception {
            return CryptoUtils.simpleDecrypt(data.toString());
        }

        @Override
        public byte[] encrypt(Object data) throws Exception {
            return CryptoUtils.simpleEncrypt(data.toString());
        }

        @Override
        public boolean support(Class<?> data) {
            return true;
        }

        @Override
        public String encryptAsString(Object data) throws Exception {
            return CryptoUtils.simpleEncryptAsString(data.toString());
        }

        @Override
        public String decryptAsString(Object data) throws Exception {
            return CryptoUtils.simpleDecryptAsString(data.toString());
        }
    };


}

