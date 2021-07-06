package util;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.*;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import javax.net.ssl.*;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

public class Cart {
    public String certPem;
    public String devCertPem;
    public String publicKeyPem;

    public static final String Default_KeyPairGenerator="RSA";
    public static final String Default_Signature="SHA1withRSA";
    public static final Integer Default_KeySize=2048;

    public static SSLSocketFactory createSSLFactory(File pemPath) throws Exception {
        byte[] certAndKey= FileUtils.readFileToByteArray(pemPath);

        byte[] certBytes = parseDERFromPEM(certAndKey, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
        byte[] keyBytes = parseDERFromPEM(certAndKey, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");

        X509Certificate cert = generateCertificateFromDER(certBytes);
        RSAPrivateKey key  = generatePrivateKeyFromDER(keyBytes);

        KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        caKeyStore.load(null, null);
        caKeyStore.setCertificateEntry("ca-certificate", cert);


        KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        clientKeyStore.load(null, null);
        clientKeyStore.setCertificateEntry("certificate", cert);
        clientKeyStore.setKeyEntry("private-key", key, "".toCharArray(), new Certificate[] { cert });

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(clientKeyStore, "".toCharArray());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), getUnsafeTrustManagers(caKeyStore), null);


        return context.getSocketFactory();
    }

    public static byte[] parseDERFromPEM(byte[] pem, String beginDelimiter, String endDelimiter) {
        String token = splitPem(pem, beginDelimiter, endDelimiter);
        return DatatypeConverter.parseBase64Binary(token);
    }

    public static String splitPem(byte[] pem, String beginDelimiter, String endDelimiter){
        String data = new String(pem);
        String[] tokens = data.split(beginDelimiter);
        tokens = tokens[1].split(endDelimiter);
        return tokens[0];
    }


    protected static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes) throws InvalidKeySpecException, NoSuchAlgorithmException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

        KeyFactory factory = KeyFactory.getInstance("RSA");

        return (RSAPrivateKey)factory.generatePrivate(spec);
    }

    protected static X509Certificate generateCertificateFromDER(byte[] certBytes) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        return (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private static TrustManager[] getTrustManagers(KeyStore caKeyStore)
            throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(caKeyStore);
        return trustManagerFactory.getTrustManagers();
    }

    /**
     * This method checks server and client certificates but overrides server hostname verification.
     * @param caKeyStore
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
    ' */
    private static TrustManager[] getUnsafeTrustManagers(KeyStore caKeyStore)
            throws NoSuchAlgorithmException, KeyStoreException {
        X509TrustManager standardTrustManager = (X509TrustManager) getTrustManagers(caKeyStore)[0];
        return new TrustManager[] { new X509ExtendedTrustManager() {

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                standardTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                standardTrustManager.checkServerTrusted(chain, authType);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return standardTrustManager.getAcceptedIssuers();
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                    throws CertificateException {
                standardTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                    throws CertificateException {
                standardTrustManager.checkServerTrusted(chain, authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                    throws CertificateException {
                standardTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                    throws CertificateException {
                standardTrustManager.checkServerTrusted(chain, authType);
            }
        } };

    }

    /**
     * 解析 pem 格式证书
     * @param PublicKey key
     * @return
     * @throws Exception
     */
    public Key parsePEMKey(String PublicKey)  throws Exception{
        Security.addProvider(new BouncyCastleProvider());
        try (StringReader reader = new StringReader(PublicKey); //
             PEMParser pemParser = new PEMParser(reader)) {
            final Object object = pemParser.readObject();
            final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);

            final KeyPair kp;

            if (object instanceof PEMEncryptedKeyPair) {
                final PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build("".toCharArray());
                kp = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
            } else if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
                try {
                    final PKCS8EncryptedPrivateKeyInfo encryptedInfo = (PKCS8EncryptedPrivateKeyInfo) object;
                    final InputDecryptorProvider provider = new JceOpenSSLPKCS8DecryptorProviderBuilder().build("".toCharArray());
                    final PrivateKeyInfo privateKeyInfo = encryptedInfo.decryptPrivateKeyInfo(provider);
                    return converter.getPrivateKey(privateKeyInfo);
                } catch (PKCSException | OperatorCreationException e) {
                    throw new IOException("Unable to decrypt private key.", e);
                }
            } else if (object instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else if (object instanceof SubjectPublicKeyInfo) {
                return converter.getPublicKey((SubjectPublicKeyInfo) object);
            } else if (object instanceof X509CertificateHolder) {
                return converter.getPublicKey(((X509CertificateHolder) object).getSubjectPublicKeyInfo());
            } else {
                kp = converter.getKeyPair((PEMKeyPair) object);
            }
            return kp.getPrivate();
        }
    }

    /**
     * 生成 String 类型证书
     * @param cert cert
     * @return String
     * @throws Exception
     */
    public String x509CertificateToPem(Object cert) throws Exception {
        final StringWriter sw = new StringWriter();
        try (final JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(cert);
        }
        return sw.toString();

    }

    public Cart createCert(String devicePublicKey) throws Exception{
        //产生公私钥对
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(Default_KeyPairGenerator);
        kpg.initialize(Default_KeySize);

        KeyPair keyPair = kpg.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        PublicKey devKey = (PublicKey) parsePEMKey(devicePublicKey);

        certPem = makeCert(publicKey,privateKey);
        devCertPem = makeCert(devKey,privateKey);

        JcaPKCS8Generator gen1 = new JcaPKCS8Generator(privateKey, null); // 需要转成 pkcs8 ios 才能识别
        publicKeyPem = x509CertificateToPem(gen1.generate());

        return this;
    }
    public static Date calculateDate(int hoursInFuture)
    {
        long secs = System.currentTimeMillis() / 1000;
        return new Date((secs + (hoursInFuture * 60 * 60)) * 1000);
    }
    private static long serialNumberBase = System.currentTimeMillis();

    public static synchronized BigInteger calculateSerialNumber()
    {
        return BigInteger.valueOf(serialNumberBase++);
    }


    /**
     * 生成私钥对
     * @param certKey 公钥
     * @param signerKey 私钥
     * @return
     * @throws Exception
     */
    public String makeCert(PublicKey certKey, PrivateKey signerKey) throws Exception {
        X500NameBuilder x500NameBld = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.O, "The Organization Otherwise Known as YueChen CA, Inc.")
                .addRDN(BCStyle.CN, "The YueChen Certificate");

        X500Name subject = x500NameBld.build();

        JcaX509v1CertificateBuilder certBldr = new JcaX509v1CertificateBuilder(
                subject,
                calculateSerialNumber(),
                calculateDate(0),
                calculateDate(24 * 31),
                subject,
                certKey);

        ContentSigner signer = new JcaContentSignerBuilder(Default_Signature)
                .setProvider("BC").build(signerKey);
        X509CertificateHolder data =certBldr.build(signer);

        return x509CertificateToPem(data);


    }

}
