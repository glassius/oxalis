package eu.peppol.as2;

import javax.activation.DataHandler;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MimeMessageHelper {


    /**
     * Creates a simple MimeMessage with a Mime type of text/plain with a single MimeBodyPart
     *
     * @param msgTxt
     * @return
     */
    public static MimeMessage createSimpleMimeMessage(String msgTxt) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(msgTxt.getBytes());

        try {
            MimeType mimeType = new MimeType("text", "plain");
            MimeBodyPart mimeBodyPart = createMimeBodyPart(byteArrayInputStream, mimeType);

            MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties()));
            mimeMessage.setContent(mimeMessage, mimeType.toString());
            return mimeMessage;

        } catch (MimeTypeParseException e) {
            throw new IllegalArgumentException("Unable to create MimeType" + e.getMessage(), e);
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to set content of mime message " + e.getMessage(), e);
        }
    }

    /**
     * Creates a MIME message from the supplied stream, which <em>must</em> contain headers, especially
     * the header "Content-Type:"
     *
     * @param inputStream
     * @return
     */
    public static MimeMessage createMimeMessage(InputStream inputStream) {
        try {
            Properties properties = System.getProperties();
            Session session = Session.getDefaultInstance(properties, null);
            MimeMessage mimeMessage = new MimeMessage(session, inputStream);

            return mimeMessage;
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to create MimeMessage from input stream. " + e.getMessage(), e);
        }
    }

    /**
     * Creates a MimeMultipart MIME message from an input stream, which does not contain the header "Content-Type:". Thus
     * the mime type must be supplied as an argument.
     *
     * @param inputStream
     * @param mimeType
     * @return
     */
    public static MimeMessage parseMultipart(InputStream inputStream, MimeType mimeType) {

        try {
            ByteArrayDataSource dataSource = new ByteArrayDataSource(inputStream, mimeType.getBaseType());
            return multipartMimeMessage(dataSource);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static MimeMessage parseMultipart(String contents, MimeType mimeType) {
        try {
            ByteArrayDataSource dataSource = new ByteArrayDataSource(contents, mimeType.getBaseType());
            return multipartMimeMessage(dataSource);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create ByteArrayDataSource; " + e.getMessage(), e);
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to create Multipart mime message; " + e.getMessage(), e);
        }

    }


    public static MimeMessage parseMultipart(InputStream inputStream) {
        try {
            return new MimeMessage(Session.getDefaultInstance(System.getProperties()), inputStream);
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }


    public static MimeMessage parseMultipart(String contents) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(contents.getBytes());
        return parseMultipart(byteArrayInputStream);
    }


    static MimeMessage multipartMimeMessage(ByteArrayDataSource dataSource) throws MessagingException {
        MimeMultipart mimeMultipart = new MimeMultipart(dataSource);
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties()));
        mimeMessage.setContent(mimeMultipart);
        return mimeMessage;
    }


    static MimeBodyPart createMimeBodyPart(InputStream inputStream, MimeType mimeType) {
        MimeBodyPart mimeBodyPart = new MimeBodyPart();


        ByteArrayDataSource byteArrayDataSource = null;
        try {
            byteArrayDataSource = new ByteArrayDataSource(inputStream, mimeType.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create ByteArrayDataSource from inputStream." + e.getMessage(), e);
        }


        try {
            DataHandler dh = new DataHandler(byteArrayDataSource);
            mimeBodyPart.setDataHandler(dh);
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to set data handler on mime body part." + e.getMessage(), e);
        }

        try {
            mimeBodyPart.setHeader("Content-Type", mimeType.toString());
            mimeBodyPart.setHeader("Content-Transfer-Encoding", "binary");   // No content-transfer-encoding needed for http
        } catch (MessagingException e) {
            throw new IllegalStateException("Unable to set headers." + e.getMessage(), e);
        }

        return mimeBodyPart;
    }

    public static String toString(MimeMessage mimeMessage) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            mimeMessage.writeTo(byteArrayOutputStream);
            return byteArrayOutputStream.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write Mime message to byte array outbput stream:" + e.getMessage(), e);
        }
    }
}
