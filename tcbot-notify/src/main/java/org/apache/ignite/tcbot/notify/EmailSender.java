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

package org.apache.ignite.tcbot.notify;

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

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Class for sending email with configured credentials.
 */
class EmailSender implements IEmailSender {
    /** {@inheritDoc} */
    @Override public void sendEmail(String to, String subject, String html, String plainText,
        ISendEmailConfig emailConfig) throws MessagingException {

        String user = emailConfig.usernameMandatory();

        Authenticator authenticator;
        Boolean authRequired = emailConfig.isAuthRequired();
        boolean auth = authRequired == null || authRequired;
        if (auth) {
            String pwd = emailConfig.passwordClearMandatory();

            authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pwd);
                }
            };
        } else {
            authenticator = null;
        }

        String smtpHost = emailConfig.smtpHost();

        Boolean sslCfg = emailConfig.isSmtpSsl();
        boolean useSsl = sslCfg == null || sslCfg;
        int defaultPort = useSsl ? 465 : 25;
        Integer smtpPortCfg = emailConfig.smtpPort();
        String smtpPort = Integer.toString(smtpPortCfg == null ? defaultPort : smtpPortCfg);

        Properties props = new Properties();
        props.put("mail.smtp.host", isNullOrEmpty(smtpHost) ? "smtp.gmail.com" : smtpHost);

        if (useSsl) {
            props.put("mail.smtp.socketFactory.port", smtpPort);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }

        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.port", smtpPort);

        Session ses = Session.getInstance(props, authenticator);

        // Create a default MimeMessage object.
        MimeMessage msg = new MimeMessage(ses);

        // Set From: header field of the header.
        msg.setFrom(new InternetAddress(user));

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
    }
}
