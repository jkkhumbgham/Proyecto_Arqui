package com.puj.email.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SmtpEmailService {

    private static final Logger LOG = Logger.getLogger(SmtpEmailService.class.getName());

    private static final String SMTP_HOST = System.getenv().getOrDefault("SMTP_HOST", "smtp.puj.edu.co");
    private static final int    SMTP_PORT = Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "587"));
    private static final String SMTP_USER = System.getenv().getOrDefault("SMTP_USER", "");
    private static final String SMTP_PASS = System.getenv().getOrDefault("SMTP_PASSWORD", "");
    private static final String FROM_ADDR = System.getenv().getOrDefault("SMTP_FROM", "no-reply@puj.edu.co");
    private static final String FROM_NAME = System.getenv().getOrDefault("SMTP_FROM_NAME",
            "Plataforma de Aprendizaje PUJ");
    private static final boolean SMTP_ENABLED =
            Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_ENABLED", "true"));

    private Session mailSession;

    @PostConstruct
    void init() {
        Properties props = new Properties();
        props.put("mail.smtp.host",            SMTP_HOST);
        props.put("mail.smtp.port",            SMTP_PORT);
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust",       SMTP_HOST);
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout",           "10000");

        mailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER, SMTP_PASS);
            }
        });

        LOG.info("SmtpEmailService inicializado — host=" + SMTP_HOST + ":" + SMTP_PORT
                + " enabled=" + SMTP_ENABLED);
    }

    /**
     * Envía un correo HTML. Siempre incluye una parte texto plano como fallback.
     *
     * @return true si el envío fue exitoso, false si falló (el llamador decide si reintentar)
     */
    public boolean send(String toAddress, String toName, String subject, String htmlBody) {
        if (!SMTP_ENABLED) {
            LOG.info("SMTP deshabilitado — simulando envío a " + toAddress + " | " + subject);
            return true;
        }

        try {
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(FROM_ADDR, FROM_NAME));
            message.setRecipient(Message.RecipientType.TO,
                    new InternetAddress(toAddress, toName));
            message.setSubject(subject, "UTF-8");

            // multipart/alternative: text/plain + text/html
            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(stripHtml(htmlBody), "UTF-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);
            message.setContent(multipart);

            Transport.send(message);
            LOG.info("Correo enviado a " + toAddress + " | " + subject);
            return true;

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error enviando correo a " + toAddress, e);
            return false;
        }
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s{2,}", " ").trim();
    }
}
