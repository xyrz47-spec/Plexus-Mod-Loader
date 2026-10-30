package dev.plexusmodloader.devtools.gradle.minecraft;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS that trusts what the JDK trusts and, on Windows, also what Windows trusts. Some machines reach the
 * internet through software that re-signs HTTPS with a root certificate installed only in the Windows
 * certificate store; the JDK's own store then rejects every server with a PKIX error. Certificates are still
 * verified: a server is accepted only if one of the two stores trusts its chain.
 */
final class SystemTrustedTls {

    private SystemTrustedTls() {}

    /** The TLS context to give an {@link java.net.http.HttpClient}. */
    static SSLContext context() {
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            managers.add(trustManager(null));
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                KeyStore windows = KeyStore.getInstance("Windows-ROOT");
                windows.load(null, null);
                managers.add(trustManager(windows));
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new AnyOf(managers)}, null);
            return context;
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("Cannot set up TLS", e);
        }
    }

    private static X509TrustManager trustManager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("No X.509 trust manager");
    }

    /** Trusts a chain when any of its managers does. */
    private record AnyOf(List<X509TrustManager> managers) implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            check(chain, authType, true);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            check(chain, authType, false);
        }

        private void check(X509Certificate[] chain, String authType, boolean client) throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager manager : managers) {
                try {
                    if (client) {
                        manager.checkClientTrusted(chain, authType);
                    } else {
                        manager.checkServerTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException e) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
            throw first;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return managers.stream()
                    .flatMap(manager -> java.util.Arrays.stream(manager.getAcceptedIssuers()))
                    .toArray(X509Certificate[]::new);
        }
    }
}
