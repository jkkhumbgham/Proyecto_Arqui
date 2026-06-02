package com.puj.cursos.aceptacion;

import org.junit.platform.suite.api.*;

/**
 * Suite JUnit Platform para los escenarios Cucumber del course-service.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue",
        value = "com.puj.cursos.aceptacion.steps")
@ConfigurationParameter(key = "cucumber.plugin",
        value = "pretty, html:target/cucumber-reports/course-service.html")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
public class AceptacionCursosSuiteTest {
}
