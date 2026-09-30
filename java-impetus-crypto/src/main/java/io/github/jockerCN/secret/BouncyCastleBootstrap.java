package io.github.jockerCN.secret;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

/** Registers Bouncy Castle for the legacy crypto APIs in this module. */
public final class BouncyCastleBootstrap {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private BouncyCastleBootstrap() {
    }


    public static void init() {
        //noop
    }
}
