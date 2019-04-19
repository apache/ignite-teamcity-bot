/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.mail;

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.ignite.ci.tcbot.conf.NotificationsConfig;

/**
 * Class for sending email with configured credentials.
 */
public class EmailSender {
    public static boolean sendEmail(String to, String subject, String html, String plainText,
        NotificationsConfig notifications) {

        String user = notifications.emailUsernameMandatory();

        String from = user;

        final String pwd = notifications.emailPasswordClearMandatory();

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");

        Session ses = Session.getInstance(props,
            new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pwd);
                }
            });

        try {
            // Create a default MimeMessage object.
            MimeMessage msg = new MimeMessage(ses);

            // Set From: header field of the header.
            msg.setFrom(new InternetAddress(from));

            // Set To: header field of the header.
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // Set Subject: header field
            msg.setSubject(subject);

            final MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(plainText, "text/plain");
            // HTML version
            final MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html");

            // Create the Multipart.  Add BodyParts to it.
            final Multipart mp = new MimeMultipart("alternative");
            mp.addBodyPart(textPart);
            mp.addBodyPart(htmlPart);
            // Set Multipart as the message's content
            msg.setContent(mp);

            // Send message
            Transport.send(msg);

            System.out.println("Sent message successfully to [" + to + "]...");

            return true;
        }
        catch (MessagingException mex) {
            mex.printStackTrace();
        }
        return false;
    }
}
