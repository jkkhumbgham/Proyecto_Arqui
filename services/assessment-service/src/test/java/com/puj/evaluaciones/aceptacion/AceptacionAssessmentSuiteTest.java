package com.puj.evaluaciones.aceptacion;

import org.junit.platform.suite.api.*;

/**
 * Suite JUnit Platform para los escenarios Cucumber del assessment-service.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue",
        value = "com.puj.evaluaciones.aceptacion.steps")
@ConfigurationParameter(key = "cucumber.plugin",
        value = "pretty, html:target/cucumber-reports/assessment-service.html")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
public class AceptacionAssessmentSuiteTest {
}
