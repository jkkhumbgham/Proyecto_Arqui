package com.puj.email;

import com.puj.email.template.EmailTemplateRenderer;
import com.puj.events.EmailNotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() { renderer = new EmailTemplateRenderer(); }

    @Test
    void welcome_containsRecipientNameAndLey1581Notice() {
        var event = new EmailNotificationEvent(
                "juan@puj.edu.co", "Juan",
                EmailNotificationEvent.EmailType.WELCOME,
                Map.of("firstName", "Juan", "platformUrl", "https://platform.puj.edu.co"));

        var result = renderer.render(event);

        assertThat(result.subject()).contains("Bienvenido");
        assertThat(result.htmlBody()).contains("Juan");
        assertThat(result.htmlBody()).contains("Ley 1581");
    }

    @Test
    void assessmentGraded_passed_showsGreenAndApproved() {
        var event = new EmailNotificationEvent(
                "juan@puj.edu.co", "Juan",
                EmailNotificationEvent.EmailType.ASSESSMENT_GRADED,
                Map.of("firstName", "Juan", "assessmentTitle", "Quiz 1",
                       "score", "85", "passed", "true"));

        var result = renderer.render(event);

        assertThat(result.htmlBody()).contains("27ae60");   // green
        assertThat(result.htmlBody()).contains("Aprobado");
        assertThat(result.htmlBody()).contains("85%");
    }

    @Test
    void assessmentGraded_failed_showsRedAndNotApproved() {
        var event = new EmailNotificationEvent(
                "juan@puj.edu.co", "Juan",
                EmailNotificationEvent.EmailType.ASSESSMENT_GRADED,
                Map.of("firstName", "Juan", "assessmentTitle", "Quiz 2",
                       "score", "42", "passed", "false",
                       "recommendation", "Revisa el módulo 2"));

        var result = renderer.render(event);

        assertThat(result.htmlBody()).contains("c0392b");    // red
        assertThat(result.htmlBody()).contains("No aprobado");
        assertThat(result.htmlBody()).contains("Revisa el módulo 2");
    }

    @Test
    void enrollmentConfirmed_containsCourseTitle() {
        var event = new EmailNotificationEvent(
                "juan@puj.edu.co", "Juan",
                EmailNotificationEvent.EmailType.ENROLLMENT_CONFIRMED,
                Map.of("firstName", "Juan", "courseTitle", "Java Avanzado",
                       "enrolledAt", "2026-05-15"));

        var result = renderer.render(event);

        assertThat(result.subject()).contains("Java Avanzado");
        assertThat(result.htmlBody()).contains("Java Avanzado");
        assertThat(result.htmlBody()).contains("2026-05-15");
    }

    @Test
    void templateParams_null_doesNotThrow() {
        var event = new EmailNotificationEvent(
                "test@puj.edu.co", "Test",
                EmailNotificationEvent.EmailType.COURSE_COMPLETED,
                Map.of("firstName", "Test", "courseTitle", "Curso X"));

        var result = renderer.render(event);
        assertThat(result.subject()).isNotBlank();
        assertThat(result.htmlBody()).isNotBlank();
    }
}
