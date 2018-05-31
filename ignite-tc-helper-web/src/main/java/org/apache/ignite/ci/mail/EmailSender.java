package org.apache.ignite.ci.mail;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.PasswordEncoder;

/**
 * Created by Дмитрий on 21.05.2016
 */
public class EmailSender {
    public static void main(String[] args) {

        // Recipient's email ID needs to be mentioned.
        String to = "dpavlov.spb@gmail.com";

        String html = "<p>This is actual message</p>";

        String subject = "This is the Subject Line!";

        sendEmail(to, subject, html);
    }

    public static void sendEmail(String to, String subject, String html) {
        Properties cfgProps = HelperConfig.loadEmailSettings();
        String username = HelperConfig.getMandatoryProperty(cfgProps, HelperConfig.USERNAME, HelperConfig. MAIL_PROPS);
        String enc = HelperConfig.getMandatoryProperty(cfgProps, HelperConfig.ENCODED_PASSWORD, HelperConfig.MAIL_PROPS);

        String password = PasswordEncoder.decode(enc);

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class",  "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");

        // Sender's email ID needs to be mentioned
        String from = username;
        // Setup mail server
        // Get the default Session object.

        Session session = Session.getInstance(props,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
        try {
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(from));

            // Set To: header field of the header.
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // Set Subject: header field
            message.setSubject(subject);

            // Send the actual HTML message, as big as you like
            message.setContent(html, "text/html");

            // Send message
            Transport.send(message);
            System.out.println("Sent message successfully....");
        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }
}
