package eu.peppol.outbound.api;

import com.google.inject.Inject;
import eu.peppol.as2.*;
import eu.peppol.identifier.ParticipantId;
import eu.peppol.security.KeystoreManager;
import eu.peppol.smp.SmpLookupManager;
import eu.peppol.identifier.PeppolDocumentTypeId;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

/**
 * @author steinar
 *         Date: 29.10.13
 *         Time: 11:35
 */
public class As2Sender {

    private SmpLookupManager smpLookupManager;

    public As2Sender(final SmpLookupManager smpLookupManager) {
        this.smpLookupManager = smpLookupManager;
    }

    public void send(InputStream inputStream, String recipient, String sender, PeppolDocumentTypeId peppolDocumentTypeId) {


        X509Certificate ourCertificate = KeystoreManager.INSTANCE.getOurCertificate();

        SmimeMessageFactory SmimeMessageFactory = new SmimeMessageFactory(KeystoreManager.INSTANCE.getOurPrivateKey(), ourCertificate);
        MimeMessage signedMimeMessage = null;
        try {
            signedMimeMessage = SmimeMessageFactory.createSignedMimeMessage(inputStream, new MimeType("application/xml"));
        } catch (MimeTypeParseException e) {
            throw new IllegalStateException("Problems with MIME types: " + e.getMessage(), e);
        }


        CloseableHttpClient httpClient = createCloseableHttpClient();

        URL endpointAddress = smpLookupManager.getEndpointAddress(new ParticipantId(recipient), peppolDocumentTypeId);

        HttpPost httpPost = new HttpPost(endpointAddress.toExternalForm());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            signedMimeMessage.writeTo(byteArrayOutputStream);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to stream S/MIME message into byte array output steram");
        }

        As2SystemIdentifier asFrom = null;
        try {
            asFrom = new As2SystemIdentifier(ourCertificate.getSubjectX500Principal());
        } catch (InvalidAs2SystemIdentifierException e) {
            throw new IllegalStateException("AS2 System Identifier could not be obtained from " + ourCertificate.getSubjectX500Principal(), e);
        }

        httpPost.addHeader(As2Header.AS2_FROM.getHttpHeaderName(), asFrom.toString());
        httpPost.addHeader(As2Header.AS2_TO.getHttpHeaderName(), "AS2-TEST");
        httpPost.addHeader(As2Header.DISPOSITION_NOTIFICATION_OPTIONS.getHttpHeaderName(), As2DispositionNotificationOptions.getDefault().toString());
        httpPost.addHeader(As2Header.AS2_VERSION.getHttpHeaderName(), As2Header.VERSION);
        httpPost.addHeader(As2Header.SUBJECT.getHttpHeaderName(), "AS2 TEST MESSAGE");
        httpPost.addHeader(As2Header.MESSAGE_ID.getHttpHeaderName(), UUID.randomUUID().toString());
        httpPost.addHeader(As2Header.DATE.getHttpHeaderName(), As2DateUtil.format(new Date()));


        // Inserts the S/MIME message to be posted
        httpPost.setEntity(new ByteArrayEntity(byteArrayOutputStream.toByteArray(), ContentType.APPLICATION_XML));

        CloseableHttpResponse postResponse = null;      // EXECUTE !!!!
        try {
            postResponse = httpClient.execute(httpPost);
        } catch (HttpHostConnectException e) {
            throw new IllegalStateException("The Oxalis server does not seem to be running at " + endpointAddress);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected error during execution of http POST to " + endpointAddress + ": " + e.getMessage(), e);
        }

        HttpEntity entity = postResponse.getEntity();   // Any results?
        if (postResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {


            try {
                String contents = EntityUtils.toString(entity);
                MimeMessage mimeMessage = MimeMessageHelper.createMimeMessage(contents);

                MdnMimeMessageInspector mdnMimeMessageInspector = new MdnMimeMessageInspector(mimeMessage);
                String msg = mdnMimeMessageInspector.getPlainText();
                System.out.println(msg);

            } catch (IOException e) {
                throw new IllegalStateException("Unable to obtain the contents of the response: " + e.getMessage(), e);
            } finally {
                try {
                    postResponse.close();
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to close http connection: " + e.getMessage(),e);
                }
            }
        }
    }


    private CloseableHttpClient createCloseableHttpClient() {
        SSLContext sslcontext = SSLContexts.createSystemDefault();
        // Use custom hostname verifier to customize SSL hostname verification.
        X509HostnameVerifier hostnameVerifier = new AllowAllHostnameVerifier();

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", new SSLConnectionSocketFactory(sslcontext, hostnameVerifier))
                .build();
        return httpclient;
    }
}