package com.puj.usuarios.aceptacion;

import org.junit.platform.suite.api.*;

/**
 * Suite JUnit Platform que ejecuta todos los escenarios Cucumber
 * definidos en {@code src/test/resources/features/}.
 *
 * <p>Configuración:
 * <ul>
 *   <li>Idioma: español (language=es en cada feature)</li>
 *   <li>Glue: paquete de steps {@code com.puj.usuarios.aceptacion.steps}</li>
 *   <li>Plugin: HTML report + pretty console</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue",
        value = "com.puj.usuarios.aceptacion.steps")
@ConfigurationParameter(key = "cucumber.plugin",
        value = "pretty, html:target/cucumber-reports/user-service.html")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
public class AceptacionSuiteTest {
    // La suite se ejecuta automáticamente a través del motor JUnit Platform
}
