#!/usr/bin/env python3
"""Seed script: creates 3 real courses with contents and evaluations."""

import json, sys, urllib.request, urllib.error

USER_SVC   = "http://localhost:8081/api/v1"
COURSE_SVC = "http://localhost:8082/api/v1"

INSTRUCTOR_EMAIL = "prof.garcia@puj.edu.co"
INSTRUCTOR_PASS  = "Profesor1234!"

STUDENT_CREDS = [
    ("andres.mora@puj.edu.co",   "Estudiante1!"),
    ("juan.perez@puj.edu.co",    "Estudiante1!"),
    ("maria.lopez@puj.edu.co",   "Estudiante1!"),
    ("sofia.ruiz@puj.edu.co",    "Estudiante1!"),
    ("valentina.gil@puj.edu.co", "Estudiante1!"),
]

# ── helpers ──────────────────────────────────────────────────────────────────

def req(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

def login(email, password):
    status, body = req("POST", f"{USER_SVC}/auth/login", {"email": email, "password": password})
    if status not in (200, 201):
        print(f"  [ERROR] login {email}: {body}")
        return None
    return body["accessToken"]

def post(url, body, token):
    status, resp = req("POST", url, body, token)
    if status not in (200, 201):
        print(f"  [WARN] POST {url}: {status} → {resp}")
        return None
    return resp

def ok(msg): print(f"  [OK] {msg}")
def info(msg): print(f"\n=== {msg} ===")

# ── data ─────────────────────────────────────────────────────────────────────

COURSES = [
  {
    "title": "Fundamentos de Programación en Java",
    "description": (
        "Aprende los conceptos esenciales de la programación orientada a objetos usando Java. "
        "Este curso cubre desde la instalación del entorno hasta el diseño de clases, herencia "
        "e interfaces. Ideal para estudiantes sin experiencia previa en programación."
    ),
    "maxStudents": 40,
    "modules": [
      {
        "title": "Introducción a Java",
        "description": "Primeros pasos: entorno de desarrollo, sintaxis básica y tipos de datos.",
        "lessons": [
          {
            "title": "¿Qué es Java y por qué aprenderlo?",
            "durationMinutes": 15,
            "contents": [
              {
                "title": "Introducción a Java",
                "contentType": "TEXT",
                "description": (
                    "Java es un lenguaje de programación orientado a objetos creado por Sun Microsystems en 1995. "
                    "Hoy es mantenido por Oracle y es uno de los lenguajes más utilizados del mundo.\n\n"
                    "Características principales:\n"
                    "• Portabilidad: 'Write once, run anywhere' gracias a la JVM (Java Virtual Machine).\n"
                    "• Orientado a objetos: Todo en Java se organiza en clases y objetos.\n"
                    "• Tipado estático: El tipo de cada variable se declara en tiempo de compilación.\n"
                    "• Gestión de memoria automática: El Garbage Collector libera memoria no utilizada.\n"
                    "• Amplio ecosistema: Millones de librerías disponibles vía Maven y Gradle.\n\n"
                    "Casos de uso:\n"
                    "Java se usa en aplicaciones empresariales (Spring Boot), Android, sistemas bancarios, "
                    "Big Data (Hadoop, Spark) y servicios en la nube. Su robustez y comunidad activa lo "
                    "convierten en una elección segura para proyectos de larga duración."
                )
              },
              {
                "title": "Video: Introducción a la JVM",
                "contentType": "VIDEO",
                "contentUrl": "https://www.youtube.com/watch?v=G1ubVOl9IBw",
                "description": "Explicación visual de cómo funciona la Java Virtual Machine y el proceso de compilación."
              }
            ]
          },
          {
            "title": "Instalación del entorno de desarrollo",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Guía de instalación: JDK e IntelliJ IDEA",
                "contentType": "TEXT",
                "description": (
                    "Para programar en Java necesitas dos herramientas fundamentales:\n\n"
                    "1. JDK (Java Development Kit)\n"
                    "   Descarga el JDK 21 LTS desde https://adoptium.net/\n"
                    "   • Windows: ejecuta el instalador .msi y sigue los pasos.\n"
                    "   • macOS: usa Homebrew: brew install temurin@21\n"
                    "   • Linux: sudo apt install openjdk-21-jdk\n"
                    "   Verifica la instalación: java -version en la terminal.\n\n"
                    "2. IntelliJ IDEA Community Edition\n"
                    "   Descarga gratuita en https://www.jetbrains.com/idea/\n"
                    "   Es el IDE más usado para Java. Ofrece autocompletado inteligente,\n"
                    "   depurador integrado y soporte para Maven/Gradle.\n\n"
                    "Tu primer programa:\n"
                    "public class HolaMundo {\n"
                    "    public static void main(String[] args) {\n"
                    "        System.out.println(\"¡Hola, Mundo!\");\n"
                    "    }\n"
                    "}\n\n"
                    "Crea un proyecto nuevo en IntelliJ, agrega esta clase y ejecútala con Shift+F10."
                )
              }
            ]
          },
          {
            "title": "Variables, tipos de datos y operadores",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Tipos primitivos y variables en Java",
                "contentType": "TEXT",
                "description": (
                    "Java tiene 8 tipos primitivos. Los más usados son:\n\n"
                    "int      → enteros de 32 bits    → int edad = 20;\n"
                    "long     → enteros de 64 bits    → long poblacion = 8_000_000_000L;\n"
                    "double   → decimales de 64 bits  → double precio = 19.99;\n"
                    "boolean  → verdadero/falso       → boolean activo = true;\n"
                    "char     → un carácter           → char letra = 'A';\n"
                    "String   → texto (no primitivo)  → String nombre = \"Carlos\";\n\n"
                    "Reglas de nomenclatura:\n"
                    "• Usa camelCase: nombreCompleto, fechaNacimiento.\n"
                    "• Las constantes van en UPPER_SNAKE_CASE: MAX_INTENTOS.\n"
                    "• Los nombres deben ser descriptivos: no uses x, a, b a menos que sea matemática.\n\n"
                    "Operadores aritméticos: +  -  *  /  %  (módulo)\n"
                    "Operadores relacionales: ==  !=  >  <  >=  <=\n"
                    "Operadores lógicos: &&  ||  !\n\n"
                    "Ejemplo de uso:\n"
                    "int a = 10, b = 3;\n"
                    "System.out.println(a / b);   // 3  (división entera)\n"
                    "System.out.println(a % b);   // 1  (resto)"
                )
              }
            ]
          },
          {
            "title": "Evaluación: Módulo 1 — Introducción a Java",
            "durationMinutes": 30,
            "contents": [
              {
                "title": "Instrucciones de la evaluación",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN — Módulo 1: Introducción a Java\n"
                    "Tiempo estimado: 30 minutos | Puntaje total: 100 puntos\n\n"
                    "Instrucciones:\n"
                    "Responde cada pregunta en tu cuaderno o en un documento. "
                    "Puedes consultar tus apuntes pero no a otros compañeros.\n\n"
                    "Parte 1 — Preguntas conceptuales (40 pts)\n"
                    "1. ¿Qué es la JVM y cuál es su función principal? (10 pts)\n"
                    "2. Menciona tres características que hacen a Java un lenguaje popular. (15 pts)\n"
                    "3. ¿Cuál es la diferencia entre int y long? ¿Cuándo usarías cada uno? (15 pts)\n\n"
                    "Parte 2 — Código (60 pts)\n"
                    "4. Escribe un programa Java que declare una variable de cada tipo primitivo "
                    "   (int, double, boolean, char) y las imprima con System.out.println. (20 pts)\n"
                    "5. Escribe un programa que lea dos números enteros e imprima: "
                    "   su suma, diferencia, producto, cociente y módulo. (20 pts)\n"
                    "6. Corrige los errores en el siguiente código y explica qué estaba mal:\n"
                    "   int precio = 9.99;\n"
                    "   String nombre = 'Juan';\n"
                    "   boolean valido = 1;\n"
                    "   System.out.println(nombre + 'cuesta' + precio); (20 pts)"
                )
              }
            ]
          }
        ]
      },
      {
        "title": "Estructuras de Control",
        "description": "Condicionales, bucles y manejo básico de excepciones.",
        "lessons": [
          {
            "title": "Condicionales: if, else if y switch",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Control de flujo con condicionales",
                "contentType": "TEXT",
                "description": (
                    "Los condicionales permiten ejecutar código solo cuando se cumple una condición.\n\n"
                    "Sintaxis if-else:\n"
                    "int nota = 75;\n"
                    "if (nota >= 90) {\n"
                    "    System.out.println(\"Sobresaliente\");\n"
                    "} else if (nota >= 70) {\n"
                    "    System.out.println(\"Aprobado\");\n"
                    "} else {\n"
                    "    System.out.println(\"Reprobado\");\n"
                    "}\n\n"
                    "Switch (Java 14+):\n"
                    "String dia = \"LUNES\";\n"
                    "String tipo = switch (dia) {\n"
                    "    case \"LUNES\", \"MARTES\", \"MIERCOLES\", \"JUEVES\", \"VIERNES\" -> \"Laborable\";\n"
                    "    case \"SABADO\", \"DOMINGO\" -> \"Fin de semana\";\n"
                    "    default -> \"Desconocido\";\n"
                    "};\n\n"
                    "Operador ternario (útil para asignaciones simples):\n"
                    "String resultado = (nota >= 70) ? \"Aprobado\" : \"Reprobado\";\n\n"
                    "Buenas prácticas:\n"
                    "• Usa switch cuando compares múltiples valores de la misma variable.\n"
                    "• Evita condicionales anidados profundos: extrae en métodos separados.\n"
                    "• Prefiere la negación explícita: if (!lista.isEmpty()) es más claro que if (lista.size() != 0)."
                )
              }
            ]
          },
          {
            "title": "Bucles: for, while y do-while",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Estructuras de repetición en Java",
                "contentType": "TEXT",
                "description": (
                    "Los bucles ejecutan un bloque de código repetidamente.\n\n"
                    "for (cuando conoces el número de iteraciones):\n"
                    "for (int i = 0; i < 10; i++) {\n"
                    "    System.out.println(\"Iteración: \" + i);\n"
                    "}\n\n"
                    "for-each (para recorrer colecciones):\n"
                    "int[] numeros = {1, 2, 3, 4, 5};\n"
                    "for (int n : numeros) {\n"
                    "    System.out.println(n * n);\n"
                    "}\n\n"
                    "while (cuando no sabes cuántas veces se repetirá):\n"
                    "Scanner sc = new Scanner(System.in);\n"
                    "int input = 0;\n"
                    "while (input != -1) {\n"
                    "    System.out.print(\"Ingresa un número (-1 para salir): \");\n"
                    "    input = sc.nextInt();\n"
                    "}\n\n"
                    "do-while (ejecuta al menos una vez):\n"
                    "do {\n"
                    "    System.out.println(\"Se ejecuta al menos una vez\");\n"
                    "} while (false);\n\n"
                    "Palabras clave útiles:\n"
                    "• break: sale del bucle inmediatamente.\n"
                    "• continue: salta a la siguiente iteración.\n"
                    "• return: sale del método completo."
                )
              },
              {
                "title": "Video: Bucles en Java explicados",
                "contentType": "VIDEO",
                "contentUrl": "https://www.youtube.com/watch?v=hz9OgRLuCzo",
                "description": "Explicación visual de los distintos tipos de bucles en Java con ejemplos prácticos."
              }
            ]
          },
          {
            "title": "Manejo de excepciones",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Try-catch y excepciones comunes",
                "contentType": "TEXT",
                "description": (
                    "Las excepciones son errores que ocurren en tiempo de ejecución. Java obliga a manejarlas.\n\n"
                    "Estructura básica:\n"
                    "try {\n"
                    "    int resultado = 10 / 0;  // lanza ArithmeticException\n"
                    "} catch (ArithmeticException e) {\n"
                    "    System.out.println(\"Error: \" + e.getMessage());\n"
                    "} finally {\n"
                    "    System.out.println(\"Esto siempre se ejecuta\");\n"
                    "}\n\n"
                    "Excepciones más comunes:\n"
                    "• NullPointerException: acceder a un objeto null.\n"
                    "• ArrayIndexOutOfBoundsException: índice fuera del arreglo.\n"
                    "• NumberFormatException: convertir un String no numérico a número.\n"
                    "• IOException: errores de entrada/salida (archivos, red).\n\n"
                    "Crear tu propia excepción:\n"
                    "public class SaldoInsuficienteException extends RuntimeException {\n"
                    "    public SaldoInsuficienteException(double monto) {\n"
                    "        super(\"Saldo insuficiente para retirar: \" + monto);\n"
                    "    }\n"
                    "}\n\n"
                    "Buenas prácticas:\n"
                    "• Nunca captures Exception genérico si puedes ser más específico.\n"
                    "• No uses excepciones para control de flujo normal.\n"
                    "• Registra (log) las excepciones; no las 'traguas' en silencio."
                )
              }
            ]
          },
          {
            "title": "Evaluación: Módulo 2 — Estructuras de Control",
            "durationMinutes": 35,
            "contents": [
              {
                "title": "Instrucciones de la evaluación",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN — Módulo 2: Estructuras de Control\n"
                    "Tiempo estimado: 35 minutos | Puntaje total: 100 puntos\n\n"
                    "Parte 1 — Análisis de código (30 pts)\n"
                    "Indica qué imprime cada fragmento:\n\n"
                    "Fragmento A (15 pts):\n"
                    "for (int i = 1; i <= 5; i++) {\n"
                    "    if (i % 2 == 0) continue;\n"
                    "    System.out.print(i + \" \");\n"
                    "}\n\n"
                    "Fragmento B (15 pts):\n"
                    "int x = 10;\n"
                    "while (x > 0) {\n"
                    "    x -= 3;\n"
                    "    if (x == 1) break;\n"
                    "}\n"
                    "System.out.println(x);\n\n"
                    "Parte 2 — Programación (70 pts)\n"
                    "5. Escribe un programa que imprima la tabla de multiplicar del 1 al 10 "
                    "   usando bucles anidados. (20 pts)\n"
                    "6. Escribe un programa que lea números del usuario hasta que ingrese 0, "
                    "   y al final muestre el máximo, el mínimo y el promedio. (25 pts)\n"
                    "7. Escribe un método que reciba un String que representa un número entero "
                    "   y lo convierta usando Integer.parseInt. Maneja la excepción si el String "
                    "   no es un número válido e imprime un mensaje de error descriptivo. (25 pts)"
                )
              }
            ]
          }
        ]
      },
      {
        "title": "Programación Orientada a Objetos",
        "description": "Clases, objetos, herencia, polimorfismo e interfaces.",
        "lessons": [
          {
            "title": "Clases, objetos y encapsulamiento",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Fundamentos de POO en Java",
                "contentType": "TEXT",
                "description": (
                    "La Programación Orientada a Objetos (POO) modela el software como un conjunto "
                    "de objetos que interactúan entre sí.\n\n"
                    "Clase (plantilla):\n"
                    "public class Persona {\n"
                    "    private String nombre;   // atributo privado\n"
                    "    private int    edad;\n\n"
                    "    public Persona(String nombre, int edad) {  // constructor\n"
                    "        this.nombre = nombre;\n"
                    "        this.edad   = edad;\n"
                    "    }\n\n"
                    "    public String getNombre() { return nombre; }  // getter\n"
                    "    public void   setNombre(String n) { this.nombre = n; }  // setter\n\n"
                    "    public String saludar() {\n"
                    "        return \"Hola, soy \" + nombre + \" y tengo \" + edad + \" años.\";\n"
                    "    }\n"
                    "}\n\n"
                    "Objeto (instancia):\n"
                    "Persona p = new Persona(\"Carlos\", 25);\n"
                    "System.out.println(p.saludar());\n\n"
                    "Encapsulamiento: ocultar el estado interno (private) y exponer "
                    "solo lo necesario (public). Esto protege la integridad de los datos.\n\n"
                    "Los 4 pilares de la POO:\n"
                    "1. Encapsulamiento: ocultar detalles de implementación.\n"
                    "2. Herencia: reutilizar código de una clase padre.\n"
                    "3. Polimorfismo: el mismo método se comporta distinto según el objeto.\n"
                    "4. Abstracción: modelar solo lo relevante del dominio."
                )
              }
            ]
          },
          {
            "title": "Herencia y polimorfismo",
            "durationMinutes": 30,
            "contents": [
              {
                "title": "Herencia en Java",
                "contentType": "TEXT",
                "description": (
                    "La herencia permite que una clase hija reutilice el código de una clase padre.\n\n"
                    "public class Animal {\n"
                    "    protected String nombre;\n"
                    "    public Animal(String nombre) { this.nombre = nombre; }\n"
                    "    public String hacerSonido() { return \"...\"; }\n"
                    "}\n\n"
                    "public class Perro extends Animal {\n"
                    "    public Perro(String nombre) { super(nombre); }\n\n"
                    "    @Override\n"
                    "    public String hacerSonido() { return \"¡Guau!\"; }\n"
                    "}\n\n"
                    "public class Gato extends Animal {\n"
                    "    public Gato(String nombre) { super(nombre); }\n\n"
                    "    @Override\n"
                    "    public String hacerSonido() { return \"¡Miau!\"; }\n"
                    "}\n\n"
                    "Polimorfismo en acción:\n"
                    "List<Animal> animales = List.of(new Perro(\"Rex\"), new Gato(\"Luna\"));\n"
                    "for (Animal a : animales) {\n"
                    "    System.out.println(a.nombre + \" dice: \" + a.hacerSonido());\n"
                    "}\n"
                    "// Rex dice: ¡Guau!\n"
                    "// Luna dice: ¡Miau!\n\n"
                    "Reglas importantes:\n"
                    "• Java solo admite herencia simple (una clase padre).\n"
                    "• Usa @Override para documentar que estás reemplazando un método.\n"
                    "• La palabra super() llama al constructor o método del padre.\n"
                    "• final en una clase impide que se extienda; final en un método impide el override."
                )
              }
            ]
          },
          {
            "title": "Interfaces y clases abstractas",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Interfaces vs clases abstractas",
                "contentType": "TEXT",
                "description": (
                    "Cuando necesitas definir un contrato sin implementación, usa interfaces.\n\n"
                    "Interfaz:\n"
                    "public interface Pagable {\n"
                    "    double calcularMonto();   // método abstracto (sin cuerpo)\n"
                    "    default String moneda() { return \"COP\"; }  // método con implementación\n"
                    "}\n\n"
                    "Clase que implementa la interfaz:\n"
                    "public class Factura implements Pagable {\n"
                    "    private double subtotal;\n"
                    "    public Factura(double subtotal) { this.subtotal = subtotal; }\n\n"
                    "    @Override\n"
                    "    public double calcularMonto() { return subtotal * 1.19; }  // con IVA\n"
                    "}\n\n"
                    "Clase abstracta (cuando hay código compartido + partes variables):\n"
                    "public abstract class Figura {\n"
                    "    protected String color;\n"
                    "    public abstract double area();        // obliga a implementar\n"
                    "    public void describir() {\n"
                    "        System.out.println(color + \", área = \" + area());\n"
                    "    }\n"
                    "}\n\n"
                    "¿Cuándo usar cuál?\n"
                    "• Interfaz: contrato puro, sin estado. Una clase puede implementar varias.\n"
                    "• Clase abstracta: código compartido + estado común + partes variables.\n"
                    "  Solo se puede extender una."
                )
              }
            ]
          },
          {
            "title": "Evaluación Final: Programación en Java",
            "durationMinutes": 60,
            "contents": [
              {
                "title": "Instrucciones de la evaluación final",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN FINAL — Fundamentos de Programación en Java\n"
                    "Tiempo: 60 minutos | Puntaje: 100 puntos\n\n"
                    "Proyecto: Sistema de Gestión de Biblioteca\n\n"
                    "Diseña e implementa un sistema orientado a objetos con las siguientes clases:\n\n"
                    "1. Clase Material (abstracta) — 20 pts\n"
                    "   Atributos: titulo, autor, anioPublicacion\n"
                    "   Método abstracto: String getTipo()\n"
                    "   Método concreto: String descripcion() que retorne una cadena formateada.\n\n"
                    "2. Clases Libro y Revista que extiendan Material — 20 pts\n"
                    "   Libro: agrega atributo isbn y número de páginas.\n"
                    "   Revista: agrega atributo numeroEdicion y periodicidad.\n\n"
                    "3. Interfaz Prestable — 10 pts\n"
                    "   Métodos: prestar(String usuario) y devolver()\n"
                    "   Solo Libro implementa esta interfaz.\n\n"
                    "4. Clase Biblioteca — 30 pts\n"
                    "   Almacena una lista de Material.\n"
                    "   Métodos: agregar(Material), buscarPorTitulo(String), listarPorTipo(String)\n\n"
                    "5. Main — 20 pts\n"
                    "   Crea al menos 3 libros y 2 revistas, agrégalos a la biblioteca,\n"
                    "   realiza búsquedas y demuestra el polimorfismo llamando a descripcion()\n"
                    "   sobre todos los materiales."
                )
              }
            ]
          }
        ]
      }
    ]
  },

  {
    "title": "Bases de Datos Relacionales",
    "description": (
        "Domina el diseño de bases de datos y el lenguaje SQL desde cero. "
        "Aprende el modelo relacional, normalización, consultas básicas y avanzadas, "
        "índices, transacciones y principios ACID. Usaremos PostgreSQL como motor principal."
    ),
    "maxStudents": 40,
    "modules": [
      {
        "title": "Fundamentos del Modelo Relacional",
        "description": "Conceptos base: tablas, claves, relaciones y normalización.",
        "lessons": [
          {
            "title": "Introducción a las bases de datos relacionales",
            "durationMinutes": 15,
            "contents": [
              {
                "title": "¿Qué es una base de datos relacional?",
                "contentType": "TEXT",
                "description": (
                    "Una base de datos relacional (RDBMS) organiza los datos en tablas con filas y columnas, "
                    "y utiliza relaciones entre tablas para evitar redundancia.\n\n"
                    "Historia y motores populares:\n"
                    "• El modelo relacional fue propuesto por Edgar Codd (IBM, 1970).\n"
                    "• Motores más usados: PostgreSQL, MySQL, Oracle, SQL Server, SQLite.\n"
                    "• PostgreSQL es el más avanzado y completo de código abierto.\n\n"
                    "Conceptos fundamentales:\n"
                    "• Tabla (relación): conjunto de filas (tuplas) y columnas (atributos).\n"
                    "• Fila (registro): una instancia de datos (ej: un cliente específico).\n"
                    "• Columna (campo): un atributo de la entidad (ej: nombre, email).\n"
                    "• Clave primaria (PK): identifica de forma única cada fila. No puede ser NULL.\n"
                    "• Clave foránea (FK): referencia a la PK de otra tabla. Garantiza integridad.\n\n"
                    "Ventajas frente a hojas de cálculo:\n"
                    "• Consistencia: restricciones que garantizan datos válidos.\n"
                    "• Concurrencia: múltiples usuarios simultáneos sin corrupción.\n"
                    "• Consultas complejas: SQL permite cruzar millones de registros en milisegundos.\n"
                    "• Transacciones: operaciones atómicas (todo o nada)."
                )
              }
            ]
          },
          {
            "title": "Modelo entidad-relación y normalización",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Diseño conceptual: diagramas ER",
                "contentType": "TEXT",
                "description": (
                    "El Diagrama Entidad-Relación (ER) es el plano arquitectónico de tu base de datos.\n\n"
                    "Componentes principales:\n"
                    "• Entidad: objeto del mundo real que quieres almacenar (Cliente, Producto, Pedido).\n"
                    "• Atributo: propiedad de la entidad (nombre, precio, fecha).\n"
                    "• Relación: asociación entre entidades (Cliente REALIZA Pedido).\n"
                    "• Cardinalidad: cuántas instancias se relacionan (1:1, 1:N, N:M).\n\n"
                    "Normalización (eliminar redundancia):\n\n"
                    "1FN (Primera Forma Normal):\n"
                    "• Cada celda contiene un valor atómico (no listas).\n"
                    "• Cada columna tiene un nombre único.\n"
                    "• No hay filas duplicadas.\n\n"
                    "2FN (Segunda Forma Normal):\n"
                    "• Cumple 1FN.\n"
                    "• Todos los atributos no clave dependen de TODA la clave primaria.\n"
                    "• Aplica solo cuando la PK es compuesta.\n\n"
                    "3FN (Tercera Forma Normal):\n"
                    "• Cumple 2FN.\n"
                    "• No hay dependencias transitivas: los atributos no clave no dependen\n"
                    "  de otros atributos no clave.\n\n"
                    "Regla práctica: si repites el mismo dato en múltiples filas, probablemente\n"
                    "necesitas extraerlo a una tabla separada y usar una FK."
                )
              },
              {
                "title": "Video: Normalización de bases de datos",
                "contentType": "VIDEO",
                "contentUrl": "https://www.youtube.com/watch?v=GFQaEYEc8_8",
                "description": "Guía visual paso a paso sobre las tres primeras formas normales con ejemplos reales."
              }
            ]
          },
          {
            "title": "Creación de tablas con DDL",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "CREATE TABLE y tipos de datos en PostgreSQL",
                "contentType": "TEXT",
                "description": (
                    "El DDL (Data Definition Language) define la estructura de la base de datos.\n\n"
                    "Tipos de datos más usados en PostgreSQL:\n"
                    "• INTEGER / BIGINT: números enteros.\n"
                    "• DECIMAL(p,s) / NUMERIC: decimales exactos (ej: precios).\n"
                    "• VARCHAR(n) / TEXT: texto de longitud variable.\n"
                    "• BOOLEAN: true/false.\n"
                    "• DATE / TIMESTAMP / TIMESTAMPTZ: fechas y horas.\n"
                    "• UUID: identificadores únicos globales.\n"
                    "• JSONB: JSON binario indexable.\n\n"
                    "Ejemplo — esquema de e-commerce:\n\n"
                    "CREATE TABLE clientes (\n"
                    "    id         SERIAL PRIMARY KEY,\n"
                    "    email      VARCHAR(255) NOT NULL UNIQUE,\n"
                    "    nombre     VARCHAR(100) NOT NULL,\n"
                    "    creado_en  TIMESTAMP DEFAULT NOW()\n"
                    ");\n\n"
                    "CREATE TABLE productos (\n"
                    "    id       SERIAL PRIMARY KEY,\n"
                    "    nombre   VARCHAR(200) NOT NULL,\n"
                    "    precio   DECIMAL(10,2) NOT NULL CHECK (precio > 0),\n"
                    "    stock    INTEGER NOT NULL DEFAULT 0\n"
                    ");\n\n"
                    "CREATE TABLE pedidos (\n"
                    "    id          SERIAL PRIMARY KEY,\n"
                    "    cliente_id  INTEGER NOT NULL REFERENCES clientes(id),\n"
                    "    total       DECIMAL(12,2) NOT NULL,\n"
                    "    estado      VARCHAR(20) DEFAULT 'PENDIENTE',\n"
                    "    creado_en   TIMESTAMP DEFAULT NOW()\n"
                    ");\n\n"
                    "Restricciones importantes:\n"
                    "• NOT NULL: el campo es obligatorio.\n"
                    "• UNIQUE: no se pueden repetir valores.\n"
                    "• CHECK: validación de dominio.\n"
                    "• REFERENCES: clave foránea con integridad referencial."
                )
              }
            ]
          },
          {
            "title": "Evaluación: Módulo 1 — Modelo Relacional",
            "durationMinutes": 35,
            "contents": [
              {
                "title": "Instrucciones de la evaluación",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN — Módulo 1: Modelo Relacional\n"
                    "Tiempo: 35 minutos | Puntaje: 100 puntos\n\n"
                    "Contexto: Sistema de gestión de una biblioteca universitaria.\n\n"
                    "Parte 1 — Conceptos (30 pts)\n"
                    "1. Explica la diferencia entre clave primaria y clave foránea. (10 pts)\n"
                    "2. ¿Qué problema resuelve la 3FN? Da un ejemplo de una tabla que viola la 3FN "
                    "   y muestra cómo normalizarla. (20 pts)\n\n"
                    "Parte 2 — Diseño (40 pts)\n"
                    "3. Diseña el diagrama ER para el sistema de biblioteca con las siguientes entidades:\n"
                    "   • Estudiante (código, nombre, carrera, semestre)\n"
                    "   • Libro (ISBN, título, autor, editorial, año)\n"
                    "   • Préstamo (fecha_inicio, fecha_devolucion, estado)\n"
                    "   Indica la cardinalidad de cada relación y justifica. (40 pts)\n\n"
                    "Parte 3 — DDL (30 pts)\n"
                    "4. Traduce el diagrama ER a sentencias CREATE TABLE en PostgreSQL.\n"
                    "   Incluye todas las restricciones (PK, FK, NOT NULL, CHECK) apropiadas. (30 pts)"
                )
              }
            ]
          }
        ]
      },
      {
        "title": "SQL — Consultas Básicas y JOINs",
        "description": "SELECT, filtros, agrupaciones y combinación de tablas.",
        "lessons": [
          {
            "title": "SELECT, FROM, WHERE y ORDER BY",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Consultas SELECT en PostgreSQL",
                "contentType": "TEXT",
                "description": (
                    "SELECT es el comando más usado en SQL. Recupera datos de una o más tablas.\n\n"
                    "Estructura básica:\n"
                    "SELECT columnas\n"
                    "FROM   tabla\n"
                    "WHERE  condicion\n"
                    "ORDER BY columna [ASC|DESC]\n"
                    "LIMIT  n;\n\n"
                    "Ejemplos con la tabla clientes:\n\n"
                    "-- Todos los clientes\n"
                    "SELECT * FROM clientes;\n\n"
                    "-- Solo nombre y email\n"
                    "SELECT nombre, email FROM clientes;\n\n"
                    "-- Clientes registrados en 2025\n"
                    "SELECT nombre, email, creado_en\n"
                    "FROM   clientes\n"
                    "WHERE  EXTRACT(YEAR FROM creado_en) = 2025\n"
                    "ORDER BY nombre ASC;\n\n"
                    "-- Búsqueda de texto (ILIKE es case-insensitive)\n"
                    "SELECT * FROM clientes\n"
                    "WHERE  nombre ILIKE '%carlos%';\n\n"
                    "-- Los 10 productos más caros\n"
                    "SELECT nombre, precio\n"
                    "FROM   productos\n"
                    "ORDER BY precio DESC\n"
                    "LIMIT 10;\n\n"
                    "Operadores en WHERE:\n"
                    "• = != > < >= <=\n"
                    "• BETWEEN x AND y\n"
                    "• IN (val1, val2, ...)\n"
                    "• IS NULL / IS NOT NULL\n"
                    "• LIKE / ILIKE (patrones con %)"
                )
              }
            ]
          },
          {
            "title": "GROUP BY, HAVING y funciones de agregación",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Agregaciones y agrupaciones en SQL",
                "contentType": "TEXT",
                "description": (
                    "Las funciones de agregación calculan un resultado sobre un conjunto de filas.\n\n"
                    "Funciones principales:\n"
                    "• COUNT(*): número de filas.\n"
                    "• SUM(col): suma de valores.\n"
                    "• AVG(col): promedio.\n"
                    "• MAX(col) / MIN(col): máximo y mínimo.\n\n"
                    "GROUP BY: agrupa filas con el mismo valor en una columna.\n\n"
                    "Ejemplo — ventas por categoría:\n"
                    "SELECT   categoria,\n"
                    "         COUNT(*)        AS total_productos,\n"
                    "         AVG(precio)     AS precio_promedio,\n"
                    "         SUM(stock)      AS stock_total\n"
                    "FROM     productos\n"
                    "GROUP BY categoria\n"
                    "ORDER BY total_productos DESC;\n\n"
                    "HAVING: filtra grupos (como WHERE pero para grupos):\n"
                    "SELECT   cliente_id,\n"
                    "         COUNT(*)    AS total_pedidos,\n"
                    "         SUM(total)  AS gasto_total\n"
                    "FROM     pedidos\n"
                    "WHERE    estado = 'ENTREGADO'\n"
                    "GROUP BY cliente_id\n"
                    "HAVING   SUM(total) > 500000\n"
                    "ORDER BY gasto_total DESC;\n\n"
                    "Regla importante:\n"
                    "En el SELECT de una query con GROUP BY, solo puedes incluir:\n"
                    "• Las columnas que están en GROUP BY.\n"
                    "• Funciones de agregación.\n"
                    "Mezclar otras columnas genera un error."
                )
              }
            ]
          },
          {
            "title": "JOINs: cruzar tablas relacionadas",
            "durationMinutes": 30,
            "contents": [
              {
                "title": "Tipos de JOIN en SQL",
                "contentType": "TEXT",
                "description": (
                    "Los JOINs combinan filas de dos tablas basándose en una condición (generalmente FK = PK).\n\n"
                    "INNER JOIN (el más común):\n"
                    "Retorna solo las filas que tienen coincidencia en ambas tablas.\n\n"
                    "SELECT c.nombre, p.total, p.estado\n"
                    "FROM   pedidos   p\n"
                    "JOIN   clientes  c ON c.id = p.cliente_id\n"
                    "WHERE  p.estado = 'PENDIENTE';\n\n"
                    "LEFT JOIN:\n"
                    "Retorna TODAS las filas de la tabla izquierda, y NULL donde no hay coincidencia.\n\n"
                    "-- Clientes que aún NO han hecho pedidos\n"
                    "SELECT c.nombre, c.email\n"
                    "FROM   clientes c\n"
                    "LEFT   JOIN pedidos p ON p.cliente_id = c.id\n"
                    "WHERE  p.id IS NULL;\n\n"
                    "RIGHT JOIN: igual que LEFT pero desde la tabla derecha (poco usado).\n\n"
                    "FULL OUTER JOIN: todas las filas de ambas tablas, NULL donde no hay match.\n\n"
                    "JOIN múltiple:\n"
                    "SELECT c.nombre, pr.nombre AS producto, dp.cantidad, dp.precio_unitario\n"
                    "FROM   detalle_pedido dp\n"
                    "JOIN   pedidos    ped ON ped.id       = dp.pedido_id\n"
                    "JOIN   clientes   c   ON c.id         = ped.cliente_id\n"
                    "JOIN   productos  pr  ON pr.id        = dp.producto_id\n"
                    "ORDER BY c.nombre;\n\n"
                    "Tip: siempre usa alias de tabla (c, p, dp) para queries con múltiples JOINs."
                )
              },
              {
                "title": "Video: JOINs explicados visualmente",
                "contentType": "VIDEO",
                "contentUrl": "https://www.youtube.com/watch?v=9yeOJ0ZMUYw",
                "description": "Visualización interactiva de todos los tipos de JOIN con diagramas de Venn y ejemplos."
              }
            ]
          },
          {
            "title": "Evaluación: Módulo 2 — SQL Básico",
            "durationMinutes": 40,
            "contents": [
              {
                "title": "Instrucciones de la evaluación",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN — Módulo 2: SQL Básico y JOINs\n"
                    "Tiempo: 40 minutos | Puntaje: 100 puntos\n\n"
                    "Usa el siguiente esquema para todas las preguntas:\n"
                    "  empleados(id, nombre, departamento_id, salario, fecha_ingreso)\n"
                    "  departamentos(id, nombre, ciudad)\n"
                    "  proyectos(id, nombre, departamento_id, presupuesto, inicio, fin)\n\n"
                    "Escribe la consulta SQL para cada caso:\n\n"
                    "1. Lista los 5 empleados con mayor salario mostrando nombre y salario. (10 pts)\n\n"
                    "2. Cuenta cuántos empleados hay por departamento. Muestra el nombre del "
                    "   departamento y la cantidad, ordenados de mayor a menor. (15 pts)\n\n"
                    "3. Encuentra los departamentos cuyo salario promedio supera 4.500.000 COP. (15 pts)\n\n"
                    "4. Lista el nombre de cada empleado junto con el nombre de su departamento "
                    "   y la ciudad. Incluye empleados sin departamento asignado. (20 pts)\n\n"
                    "5. Para cada departamento, muestra el nombre del departamento, el número de "
                    "   proyectos activos (donde la fecha actual esté entre inicio y fin), y el "
                    "   presupuesto total de esos proyectos. (20 pts)\n\n"
                    "6. Encuentra los empleados que ingresaron antes del 2020 y trabajan en "
                    "   departamentos ubicados en Bogotá. (20 pts)"
                )
              }
            ]
          }
        ]
      },
      {
        "title": "SQL Avanzado y Administración",
        "description": "Subconsultas, CTEs, índices, transacciones y ACID.",
        "lessons": [
          {
            "title": "Subconsultas y Common Table Expressions (CTEs)",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Consultas avanzadas: subqueries y CTEs",
                "contentType": "TEXT",
                "description": (
                    "Las subconsultas y CTEs permiten construir consultas complejas de forma legible.\n\n"
                    "Subconsulta en WHERE:\n"
                    "-- Productos con precio mayor al promedio\n"
                    "SELECT nombre, precio\n"
                    "FROM   productos\n"
                    "WHERE  precio > (SELECT AVG(precio) FROM productos);\n\n"
                    "Subconsulta correlacionada (referencia la tabla exterior):\n"
                    "-- Clientes que han gastado más que el promedio general\n"
                    "SELECT c.nombre\n"
                    "FROM   clientes c\n"
                    "WHERE  (SELECT SUM(total) FROM pedidos WHERE cliente_id = c.id) > \n"
                    "       (SELECT AVG(total_cliente) FROM\n"
                    "           (SELECT SUM(total) AS total_cliente FROM pedidos GROUP BY cliente_id) t);\n\n"
                    "CTE (WITH) — más legible:\n"
                    "WITH ventas_por_cliente AS (\n"
                    "    SELECT cliente_id, SUM(total) AS total_gastado\n"
                    "    FROM   pedidos\n"
                    "    WHERE  estado = 'ENTREGADO'\n"
                    "    GROUP BY cliente_id\n"
                    "),\n"
                    "promedio AS (\n"
                    "    SELECT AVG(total_gastado) AS media FROM ventas_por_cliente\n"
                    ")\n"
                    "SELECT c.nombre, v.total_gastado\n"
                    "FROM   ventas_por_cliente v\n"
                    "JOIN   clientes c ON c.id = v.cliente_id\n"
                    "CROSS JOIN promedio p\n"
                    "WHERE  v.total_gastado > p.media\n"
                    "ORDER BY v.total_gastado DESC;\n\n"
                    "CTE Recursiva (para jerarquías):\n"
                    "WITH RECURSIVE jerarquia AS (\n"
                    "    SELECT id, nombre, manager_id, 0 AS nivel\n"
                    "    FROM empleados WHERE manager_id IS NULL\n"
                    "    UNION ALL\n"
                    "    SELECT e.id, e.nombre, e.manager_id, j.nivel + 1\n"
                    "    FROM empleados e JOIN jerarquia j ON e.manager_id = j.id\n"
                    ")\n"
                    "SELECT nombre, nivel FROM jerarquia ORDER BY nivel, nombre;"
                )
              }
            ]
          },
          {
            "title": "Índices y optimización de consultas",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Índices en PostgreSQL",
                "contentType": "TEXT",
                "description": (
                    "Un índice es una estructura de datos auxiliar que acelera las búsquedas.\n"
                    "Sin índice: PostgreSQL lee TODA la tabla (Sequential Scan).\n"
                    "Con índice: salta directamente a las filas relevantes (Index Scan).\n\n"
                    "Tipos de índice en PostgreSQL:\n"
                    "• B-Tree (por defecto): ideal para =, <, >, BETWEEN, ORDER BY.\n"
                    "• Hash: solo para igualdad (=). Raramente usado.\n"
                    "• GIN: para arrays, JSONB, búsqueda de texto completo.\n"
                    "• BRIN: para columnas con correlación física (ej: timestamps secuenciales).\n\n"
                    "Crear índices:\n"
                    "-- Índice simple\n"
                    "CREATE INDEX idx_pedidos_cliente ON pedidos (cliente_id);\n\n"
                    "-- Índice compuesto (para queries que filtran por ambas columnas)\n"
                    "CREATE INDEX idx_pedidos_estado_fecha ON pedidos (estado, creado_en DESC);\n\n"
                    "-- Índice parcial (solo indexa un subconjunto)\n"
                    "CREATE INDEX idx_pedidos_pendientes ON pedidos (creado_en)\n"
                    "WHERE estado = 'PENDIENTE';\n\n"
                    "Analizar el plan de ejecución:\n"
                    "EXPLAIN ANALYZE\n"
                    "SELECT * FROM pedidos WHERE cliente_id = 42 AND estado = 'ENTREGADO';\n\n"
                    "Cuándo NO crear índices:\n"
                    "• Tablas muy pequeñas (el scan es más rápido).\n"
                    "• Columnas con muy pocos valores distintos (baja cardinalidad).\n"
                    "• Columnas que se actualizan frecuentemente (costo de mantenimiento).\n"
                    "Los índices aceleran los SELECT pero ralentizan INSERT/UPDATE/DELETE."
                )
              }
            ]
          },
          {
            "title": "Transacciones y principios ACID",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Transacciones en PostgreSQL",
                "contentType": "TEXT",
                "description": (
                    "Una transacción agrupa varias operaciones SQL en una unidad atómica: "
                    "todas se completan o ninguna.\n\n"
                    "Ejemplo clásico — transferencia bancaria:\n"
                    "BEGIN;\n"
                    "  UPDATE cuentas SET saldo = saldo - 100000 WHERE id = 1;  -- débito\n"
                    "  UPDATE cuentas SET saldo = saldo + 100000 WHERE id = 2;  -- crédito\n"
                    "COMMIT;  -- confirma ambas operaciones\n\n"
                    "-- Si algo falla:\n"
                    "ROLLBACK;  -- revierte todo\n\n"
                    "Principios ACID:\n"
                    "• Atomicidad: la transacción es todo o nada. Si falla a mitad, se revierte.\n"
                    "• Consistencia: la BD pasa de un estado válido a otro estado válido.\n"
                    "  Las restricciones (FK, CHECK, NOT NULL) se verifican al hacer COMMIT.\n"
                    "• Aislamiento: las transacciones concurrentes no se interfieren.\n"
                    "  Niveles: READ COMMITTED (por defecto), REPEATABLE READ, SERIALIZABLE.\n"
                    "• Durabilidad: una vez confirmada, la transacción sobrevive a fallos de sistema\n"
                    "  (gracias al WAL - Write-Ahead Log).\n\n"
                    "Problemas de concurrencia:\n"
                    "• Dirty Read: leer datos no confirmados de otra transacción.\n"
                    "• Non-Repeatable Read: el mismo SELECT da resultados distintos en la misma TX.\n"
                    "• Phantom Read: aparecen filas nuevas entre dos lecturas de la misma TX.\n\n"
                    "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;\n"
                    "elimina todos estos problemas pero reduce el rendimiento."
                )
              }
            ]
          },
          {
            "title": "Evaluación Final: Bases de Datos Relacionales",
            "durationMinutes": 60,
            "contents": [
              {
                "title": "Instrucciones de la evaluación final",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN FINAL — Bases de Datos Relacionales\n"
                    "Tiempo: 60 minutos | Puntaje: 100 puntos\n\n"
                    "Proyecto: Base de datos para una plataforma de streaming de música.\n\n"
                    "Parte 1 — Diseño (30 pts)\n"
                    "Diseña el modelo relacional completo para las siguientes entidades:\n"
                    "• Usuario (id, email, nombre, plan_suscripcion, fecha_registro)\n"
                    "• Artista (id, nombre, pais, genero_principal)\n"
                    "• Album (id, titulo, artista_id, año_lanzamiento)\n"
                    "• Cancion (id, titulo, album_id, duracion_segundos, reproducciones)\n"
                    "• Playlist (id, nombre, usuario_id, publica)\n"
                    "• Relación M:N entre Playlist y Cancion con orden de reproducción.\n\n"
                    "Parte 2 — DDL (20 pts)\n"
                    "Escribe los CREATE TABLE con todas las restricciones apropiadas.\n\n"
                    "Parte 3 — Consultas (50 pts)\n"
                    "Escribe SQL para:\n"
                    "a) Las 10 canciones más reproducidas de los últimos 30 días. (10 pts)\n"
                    "b) Artistas con más de 5 álbumes, ordenados por número de canciones total. (10 pts)\n"
                    "c) Usuarios con plan premium que tienen playlists públicas con más de 20 canciones. (15 pts)\n"
                    "d) Para cada género, el artista con más reproducciones totales en sus canciones. "
                    "   Usa una CTE. (15 pts)"
                )
              }
            ]
          }
        ]
      }
    ]
  },

  {
    "title": "Arquitectura de Software",
    "description": (
        "Comprende los principios, estilos y patrones que guían el diseño de sistemas de software robustos. "
        "Estudiaremos atributos de calidad, arquitectura en capas, microservicios, arquitectura orientada "
        "a eventos, documentación con el modelo C4 y prácticas DevOps. Orientado a estudiantes con "
        "conocimientos básicos de programación."
    ),
    "maxStudents": 40,
    "modules": [
      {
        "title": "Fundamentos de Arquitectura de Software",
        "description": "Qué es la arquitectura, atributos de calidad y estilos clásicos.",
        "lessons": [
          {
            "title": "¿Qué es la arquitectura de software?",
            "durationMinutes": 15,
            "contents": [
              {
                "title": "Definición y rol del arquitecto de software",
                "contentType": "TEXT",
                "description": (
                    "La arquitectura de software es el conjunto de decisiones estructurales de alto nivel "
                    "que determinan cómo se organiza un sistema: sus componentes, las relaciones entre ellos "
                    "y los principios que gobiernan su diseño y evolución.\n\n"
                    "Definición de Bass, Clements y Kazman (SEI):\n"
                    "'La arquitectura de un sistema software es la estructura o estructuras del sistema, "
                    "que comprenden los elementos software, las propiedades externamente visibles de esos "
                    "elementos y las relaciones entre ellos.'\n\n"
                    "¿Por qué importa la arquitectura?\n"
                    "• Las decisiones arquitectónicas son las más difíciles de cambiar. Un error en la BD "
                    "  elegida o en cómo se comunican los servicios puede costar meses de refactoring.\n"
                    "• La arquitectura determina los atributos de calidad: rendimiento, seguridad, "
                    "  escalabilidad, mantenibilidad.\n"
                    "• Facilita la comunicación entre equipos mediante vocabulario y diagramas compartidos.\n\n"
                    "Rol del arquitecto de software:\n"
                    "• Toma decisiones de diseño con impacto sistémico.\n"
                    "• Evalúa trade-offs: no existe una arquitectura perfecta, solo la más adecuada para el contexto.\n"
                    "• Documenta decisiones con ADRs (Architecture Decision Records).\n"
                    "• Trabaja con desarrolladores, QA, seguridad y producto.\n\n"
                    "Diferencia entre arquitectura y diseño:\n"
                    "• Arquitectura: decisiones de alto nivel (¿qué tecnologías? ¿cómo se comunican los servicios?).\n"
                    "• Diseño: decisiones dentro de un componente (¿qué patrones de diseño usar en este servicio?)."
                )
              }
            ]
          },
          {
            "title": "Atributos de calidad (ISO 25010)",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Atributos de calidad del software",
                "contentType": "TEXT",
                "description": (
                    "Los atributos de calidad (también llamados requisitos no funcionales) determinan qué tan bueno "
                    "es un sistema más allá de si hace 'lo que debe hacer'. ISO 25010 los clasifica en:\n\n"
                    "1. Rendimiento / Performance\n"
                    "   • Tiempo de respuesta, throughput (peticiones/segundo), uso de recursos.\n"
                    "   • Tácticas: caché, índices, procesamiento asíncrono, escalado horizontal.\n\n"
                    "2. Disponibilidad / Availability\n"
                    "   • % del tiempo que el sistema está operativo. SLA: 99.9% = 8.7 h/año de downtime.\n"
                    "   • Tácticas: redundancia, health checks, circuit breakers, despliegue blue/green.\n\n"
                    "3. Seguridad / Security\n"
                    "   • Confidencialidad, integridad, no repudio, autenticación, autorización.\n"
                    "   • Tácticas: TLS, OAuth 2.0, validación de entrada, mínimo privilegio.\n\n"
                    "4. Mantenibilidad / Maintainability\n"
                    "   • Facilidad para modificar, extender y corregir el sistema.\n"
                    "   • Tácticas: modularidad, separación de responsabilidades (SRP), baja acoplamiento.\n\n"
                    "5. Escalabilidad / Scalability\n"
                    "   • Escala vertical: más RAM/CPU al mismo servidor.\n"
                    "   • Escala horizontal: más instancias del servicio.\n\n"
                    "6. Testeabilidad / Testability\n"
                    "   • Facilidad para verificar el comportamiento del sistema.\n\n"
                    "Trade-offs: mejorar un atributo suele afectar otro. Más seguridad → más latencia. "
                    "El arquitecto debe negociar estas tensiones con los stakeholders."
                )
              }
            ]
          },
          {
            "title": "Estilos y patrones arquitectónicos",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Los principales estilos arquitectónicos",
                "contentType": "TEXT",
                "description": (
                    "Un estilo arquitectónico es un vocabulario de componentes y conectores con restricciones "
                    "sobre cómo combinarlos.\n\n"
                    "1. Arquitectura en Capas (Layered)\n"
                    "   Organiza el sistema en capas horizontales: Presentación → Lógica de Negocio → Datos.\n"
                    "   + Simple de entender y desarrollar. + Buen aislamiento entre capas.\n"
                    "   - El número de capas puede escalar mal. - Toda petición atraviesa todas las capas.\n"
                    "   Ejemplos: MVC, Jakarta EE, Django.\n\n"
                    "2. Cliente-Servidor\n"
                    "   Clientes solicitan servicios a servidores centralizados.\n"
                    "   Ejemplo: navegador web ↔ servidor HTTP.\n\n"
                    "3. Microservicios\n"
                    "   El sistema se divide en servicios pequeños, independientes y desplegables por separado.\n"
                    "   + Escalabilidad independiente. + Tecnologías heterogéneas por servicio.\n"
                    "   - Complejidad operacional. - Consistencia eventual. - Latencia de red.\n\n"
                    "4. Orientado a Eventos (Event-Driven)\n"
                    "   Los componentes se comunican publicando y consumiendo eventos asíncronos.\n"
                    "   + Desacoplamiento extremo. + Alta escalabilidad.\n"
                    "   - Difícil de depurar. - Consistencia eventual.\n\n"
                    "5. Serverless / FaaS\n"
                    "   Funciones ejecutadas por la nube en respuesta a eventos.\n"
                    "   + Sin gestión de infraestructura. + Escala automático.\n"
                    "   - Cold start. - Vendor lock-in.\n\n"
                    "No existe un estilo universalmente superior. La elección depende del contexto, "
                    "el equipo y los atributos de calidad prioritarios."
                )
              },
              {
                "title": "Video: Estilos arquitectónicos comparados",
                "contentType": "VIDEO",
                "contentUrl": "https://www.youtube.com/watch?v=zvz3TV3_WoE",
                "description": "Comparación visual de los principales estilos arquitectónicos con casos de uso reales."
              }
            ]
          },
          {
            "title": "Evaluación: Módulo 1 — Fundamentos",
            "durationMinutes": 35,
            "contents": [
              {
                "title": "Instrucciones de la evaluación",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN — Módulo 1: Fundamentos de Arquitectura\n"
                    "Tiempo: 35 minutos | Puntaje: 100 puntos\n\n"
                    "Parte 1 — Preguntas abiertas (50 pts)\n"
                    "1. Explica en tus palabras qué es la arquitectura de software y por qué las "
                    "   decisiones arquitectónicas son las más difíciles de revertir. (20 pts)\n\n"
                    "2. Una startup que lanza su primer producto elige microservicios porque 'escala bien'. "
                    "   ¿Estás de acuerdo con esta decisión? Argumenta considerando los atributos de "
                    "   calidad relevantes para un equipo de 3 desarrolladores. (30 pts)\n\n"
                    "Parte 2 — Análisis de trade-offs (50 pts)\n"
                    "3. Para un sistema bancario de transferencias, rankea los 5 atributos de calidad "
                    "   más importantes (de mayor a menor), justifica tu orden y describe UNA táctica "
                    "   arquitectónica concreta para cada atributo. (50 pts)\n\n"
                    "   Atributos a considerar:\n"
                    "   Disponibilidad · Seguridad · Rendimiento · Mantenibilidad · Testeabilidad"
                )
              }
            ]
          }
        ]
      },
      {
        "title": "Arquitecturas Modernas",
        "description": "Microservicios, event-driven y patrones de integración.",
        "lessons": [
          {
            "title": "Arquitectura en Capas y patrón MVC",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Arquitectura en capas: implementación práctica",
                "contentType": "TEXT",
                "description": (
                    "La arquitectura en capas divide el sistema en niveles con responsabilidades bien definidas. "
                    "Cada capa solo se comunica con la capa adyacente.\n\n"
                    "Capas típicas de una aplicación web:\n\n"
                    "┌─────────────────────────────────────────┐\n"
                    "│  Capa de Presentación (UI / API REST)   │  ← Recibe peticiones, valida entrada\n"
                    "├─────────────────────────────────────────┤\n"
                    "│  Capa de Aplicación / Servicios         │  ← Orquesta casos de uso\n"
                    "├─────────────────────────────────────────┤\n"
                    "│  Capa de Dominio / Negocio              │  ← Reglas de negocio puras\n"
                    "├─────────────────────────────────────────┤\n"
                    "│  Capa de Infraestructura / Datos        │  ← BD, mensajería, APIs externas\n"
                    "└─────────────────────────────────────────┘\n\n"
                    "Patrón MVC (Model-View-Controller):\n"
                    "• Model: datos y lógica de negocio.\n"
                    "• View: presentación al usuario.\n"
                    "• Controller: recibe input, coordina Model y View.\n\n"
                    "Variante para APIs REST: Model-Repository-Service-Controller\n"
                    "• Repository: acceso a datos (JPA/Hibernate).\n"
                    "• Service: lógica de negocio.\n"
                    "• Controller: endpoint REST.\n\n"
                    "Ventajas:\n"
                    "• Fácil de entender para equipos nuevos.\n"
                    "• Pruebas unitarias por capa.\n"
                    "• Buen punto de partida para la mayoría de aplicaciones.\n\n"
                    "Cuándo supera sus límites:\n"
                    "• Cuando los servicios crecen demasiado ('God Service').\n"
                    "• Cuando necesitas escalar partes de forma independiente.\n"
                    "• En ese caso, considera microservicios o arquitectura hexagonal."
                )
              }
            ]
          },
          {
            "title": "Microservicios: principios y diseño",
            "durationMinutes": 30,
            "contents": [
              {
                "title": "Diseño de arquitecturas de microservicios",
                "contentType": "TEXT",
                "description": (
                    "Los microservicios descomponen una aplicación en servicios pequeños, cada uno:\n"
                    "• Con una responsabilidad única (Single Responsibility).\n"
                    "• Desplegable de forma independiente.\n"
                    "• Con su propia base de datos (Database per Service).\n"
                    "• Comunicándose vía API REST o mensajería asíncrona.\n\n"
                    "Principios de diseño:\n\n"
                    "1. Bounded Context (DDD)\n"
                    "   Cada servicio modela un subdominio del negocio. Ejemplo: Order Service,\n"
                    "   Inventory Service, Payment Service son contextos distintos.\n\n"
                    "2. API First\n"
                    "   Diseña el contrato de API (OpenAPI) antes de implementar.\n\n"
                    "3. Stateless\n"
                    "   Los servicios no guardan estado de sesión. El estado va en la BD o caché.\n\n"
                    "4. Resiliencia\n"
                    "   Circuit Breaker: si un servicio falla, no cascadea el error.\n"
                    "   Retry con backoff exponencial para fallos transitorios.\n\n"
                    "5. Observabilidad\n"
                    "   Logs estructurados + trazas distribuidas (Jaeger, Zipkin) + métricas (Prometheus).\n\n"
                    "Patrones de comunicación:\n"
                    "• Síncrono: REST / gRPC — el cliente espera la respuesta.\n"
                    "• Asíncrono: mensajería (RabbitMQ, Kafka) — el cliente no espera.\n"
                    "• API Gateway: punto de entrada único que enruta a los microservicios.\n\n"
                    "Desafíos:\n"
                    "• Consistencia distribuida: sin transacciones ACID entre servicios.\n"
                    "  Solución: patrón SAGA (coreografía o orquestación).\n"
                    "• Service Discovery: ¿cómo sabe el cliente dónde está cada servicio?\n"
                    "  Solución: Consul, Kubernetes Service, Eureka."
                )
              },
              {
                "title": "Video: Microservicios en la práctica",
                "contentType": "VIDEO",
                "contentUrl": "https://www.youtube.com/watch?v=rv4LlmLmVWk",
                "description": "Introducción práctica a microservicios: cuándo usarlos, cómo diseñarlos y sus patrones principales."
              }
            ]
          },
          {
            "title": "Arquitectura orientada a eventos (EDA)",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "Event-Driven Architecture",
                "contentType": "TEXT",
                "description": (
                    "En una arquitectura orientada a eventos (EDA), los componentes se comunican "
                    "publicando y consumiendo eventos en lugar de llamarse directamente.\n\n"
                    "Conceptos clave:\n"
                    "• Evento: algo que ocurrió en el sistema ('PedidoRealizado', 'PagoConfirmado').\n"
                    "  Tiene: tipo, timestamp, payload y una identificación.\n"
                    "• Productor: servicio que publica el evento.\n"
                    "• Consumidor: servicio que se suscribe y reacciona al evento.\n"
                    "• Broker: intermediario que enruta eventos (RabbitMQ, Apache Kafka).\n\n"
                    "Patrones:\n\n"
                    "Pub/Sub (RabbitMQ Fanout):\n"
                    "Un productor publica; múltiples consumidores independientes reciben el mismo evento.\n"
                    "Caso de uso: cuando se registra un usuario, se envía email de bienvenida Y se crea "
                    "su perfil de análisis Y se notifica al CRM — simultáneamente y de forma desacoplada.\n\n"
                    "Event Sourcing:\n"
                    "El estado del sistema se deriva de una secuencia de eventos inmutables.\n"
                    "Ventaja: auditoría completa, posibilidad de 'rebobinar' el sistema.\n\n"
                    "CQRS (Command Query Responsibility Segregation):\n"
                    "Separa las operaciones de escritura (Commands) de las de lectura (Queries).\n"
                    "Permite optimizar cada lado de forma independiente.\n\n"
                    "Ventajas de EDA:\n"
                    "• Desacoplamiento temporal y espacial extremo.\n"
                    "• Alta escalabilidad (los consumidores escalan independientemente).\n"
                    "• Fácil agregar nuevos consumidores sin modificar productores.\n\n"
                    "Desafíos:\n"
                    "• Consistencia eventual: los datos pueden estar temporalmente desincronizados.\n"
                    "• Depuración compleja: el flujo no es lineal.\n"
                    "• Exactly-once delivery es difícil de garantizar."
                )
              }
            ]
          },
          {
            "title": "Evaluación: Módulo 2 — Arquitecturas Modernas",
            "durationMinutes": 40,
            "contents": [
              {
                "title": "Instrucciones de la evaluación",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN — Módulo 2: Arquitecturas Modernas\n"
                    "Tiempo: 40 minutos | Puntaje: 100 puntos\n\n"
                    "Escenario: eres el arquitecto de una plataforma de e-commerce que actualmente "
                    "es un monolito y debe evolucionar para soportar 1 millón de usuarios activos.\n\n"
                    "Parte 1 — Análisis (40 pts)\n"
                    "1. El equipo propone migrar a microservicios. Identifica los 4 servicios principales "
                    "   que propondrías (con su responsabilidad y datos propios) y justifica la "
                    "   descomposición usando el concepto de Bounded Context. (20 pts)\n\n"
                    "2. El proceso de compra involucra: reservar inventario, procesar pago, generar orden "
                    "   y enviar confirmación por email. Diseña este flujo usando el patrón SAGA "
                    "   (describe los pasos y las compensaciones si algo falla). (20 pts)\n\n"
                    "Parte 2 — Decisiones arquitectónicas (60 pts)\n"
                    "3. ¿Usarías comunicación síncrona (REST) o asíncrona (mensajería) entre los "
                    "   servicios que identificaste? Justifica para al menos 3 pares de servicios. (30 pts)\n\n"
                    "4. El equipo de marketing quiere agregar un módulo de recomendaciones sin afectar "
                    "   el proceso de compra. ¿Cómo lo integrarías usando EDA? Describe los eventos, "
                    "   productores y consumidores. (30 pts)"
                )
              }
            ]
          }
        ]
      },
      {
        "title": "Diseño, Documentación y DevOps",
        "description": "C4 Model, ADRs, CI/CD y contenedores.",
        "lessons": [
          {
            "title": "Diagramas de arquitectura: el Modelo C4",
            "durationMinutes": 20,
            "contents": [
              {
                "title": "Documentar arquitectura con el Modelo C4",
                "contentType": "TEXT",
                "description": (
                    "El Modelo C4 (creado por Simon Brown) organiza los diagramas de arquitectura "
                    "en 4 niveles de detalle, como hacer zoom en un mapa.\n\n"
                    "Nivel 1 — Diagrama de Contexto del Sistema\n"
                    "Muestra el sistema como una caja negra y sus interacciones con usuarios y sistemas externos.\n"
                    "Audiencia: cualquier persona (técnica o no).\n"
                    "Incluye: usuarios (personas), el sistema principal y sistemas externos.\n\n"
                    "Nivel 2 — Diagrama de Contenedores\n"
                    "Muestra los 'contenedores' del sistema (aplicaciones, bases de datos, microservicios).\n"
                    "Audiencia: desarrolladores y arquitectos.\n"
                    "Incluye: Web App, API, Microservicios, Bases de Datos, Message Broker.\n\n"
                    "Nivel 3 — Diagrama de Componentes\n"
                    "Muestra los componentes internos de un contenedor específico.\n"
                    "Audiencia: desarrolladores del equipo.\n"
                    "Incluye: Controllers, Services, Repositories, Entities.\n\n"
                    "Nivel 4 — Diagrama de Código\n"
                    "Diagrama UML de clases para un componente específico.\n"
                    "Audiencia: desarrolladores trabajando en ese código.\n\n"
                    "Notación C4 simplificada:\n"
                    "• [Person]: representa un usuario o rol.\n"
                    "• [System]: sistema de software.\n"
                    "• [Container]: aplicación o servicio ejecutable.\n"
                    "• [Component]: módulo dentro de un contenedor.\n\n"
                    "Herramientas gratuitas:\n"
                    "• Structurizr Lite (gratuito): genera diagramas desde código DSL.\n"
                    "• Draw.io / diagrams.net: diagramación libre con plantillas C4.\n"
                    "• PlantUML con extensión C4."
                )
              }
            ]
          },
          {
            "title": "Architecture Decision Records (ADRs)",
            "durationMinutes": 15,
            "contents": [
              {
                "title": "Documentar decisiones con ADRs",
                "contentType": "TEXT",
                "description": (
                    "Un ADR (Architecture Decision Record) es un documento corto que registra una "
                    "decisión arquitectónica importante: el contexto, las alternativas consideradas "
                    "y la decisión tomada con sus consecuencias.\n\n"
                    "¿Por qué usar ADRs?\n"
                    "• Evitan que la próxima persona que llega al equipo 'deshaga' decisiones "
                    "  tomadas por buenas razones.\n"
                    "• Documentan los trade-offs: no solo el QUÉ, sino el POR QUÉ.\n"
                    "• Crean una bitácora de la evolución arquitectónica del sistema.\n\n"
                    "Formato estándar (Michael Nygard):\n\n"
                    "# ADR-0012: Usar PostgreSQL como base de datos principal\n\n"
                    "## Estado\n"
                    "Aceptado\n\n"
                    "## Contexto\n"
                    "Necesitamos una base de datos relacional para el servicio de cursos. "
                    "El equipo tiene experiencia con SQL. Los datos tienen relaciones complejas "
                    "entre módulos, lecciones y usuarios.\n\n"
                    "## Decisión\n"
                    "Usaremos PostgreSQL 15 como motor de base de datos principal.\n\n"
                    "## Alternativas consideradas\n"
                    "- MySQL: menos features avanzados (JSONB, window functions, full-text search).\n"
                    "- MongoDB: sin esquema, inadecuado para datos altamente relacionados.\n"
                    "- DynamoDB: vendor lock-in AWS, costo elevado, sin joins.\n\n"
                    "## Consecuencias\n"
                    "+ Soporte nativo para JSON, arrays, rangos y full-text search.\n"
                    "+ Transacciones ACID robustas.\n"
                    "+ Open source, sin costo de licencia.\n"
                    "- Requiere conocimiento del equipo en PostgreSQL específico.\n"
                    "- Escalado horizontal más complejo que NoSQL.\n\n"
                    "Los ADRs se guardan como archivos .md en el repositorio bajo /docs/adr/."
                )
              }
            ]
          },
          {
            "title": "DevOps, CI/CD y contenedores",
            "durationMinutes": 25,
            "contents": [
              {
                "title": "DevOps y CI/CD en arquitecturas modernas",
                "contentType": "TEXT",
                "description": (
                    "DevOps une el desarrollo (Dev) y las operaciones (Ops) para acortar el ciclo "
                    "de entrega de software manteniendo alta calidad.\n\n"
                    "CI — Integración Continua:\n"
                    "Cada push al repositorio dispara automáticamente:\n"
                    "1. Compilación del código.\n"
                    "2. Ejecución de pruebas unitarias e integración.\n"
                    "3. Análisis de código estático (SonarQube).\n"
                    "4. Construcción de la imagen Docker.\n\n"
                    "CD — Despliegue Continuo:\n"
                    "Después del CI, automáticamente:\n"
                    "1. Push de la imagen al registry (ghcr.io, Docker Hub).\n"
                    "2. Despliegue en ambiente de staging.\n"
                    "3. Pruebas de smoke/E2E en staging.\n"
                    "4. Despliegue a producción (con aprobación manual o automático).\n\n"
                    "Docker — contenedores:\n"
                    "• Empaqueta la aplicación con todas sus dependencias.\n"
                    "• 'Build once, run anywhere': el mismo contenedor en dev, CI y producción.\n"
                    "• Dockerfile define cómo construir la imagen.\n"
                    "• docker-compose.yml orquesta múltiples contenedores en desarrollo local.\n\n"
                    "Kubernetes — orquestación:\n"
                    "• Gestiona despliegue, escalado y resiliencia de contenedores en producción.\n"
                    "• Conceptos: Pod, Deployment, Service, Ingress, ConfigMap, Secret.\n"
                    "• Alternativa gratuita local: minikube.\n\n"
                    "Pipeline de CI/CD con GitHub Actions (gratuito):\n"
                    "on: push\n"
                    "jobs:\n"
                    "  build:\n"
                    "    runs-on: ubuntu-latest\n"
                    "    steps:\n"
                    "      - uses: actions/checkout@v4\n"
                    "      - run: mvn verify\n"
                    "      - run: docker build -t ghcr.io/usuario/app:${{ github.sha }} .\n"
                    "      - run: docker push ghcr.io/usuario/app:${{ github.sha }}"
                )
              }
            ]
          },
          {
            "title": "Evaluación Final: Arquitectura de Software",
            "durationMinutes": 60,
            "contents": [
              {
                "title": "Instrucciones de la evaluación final",
                "contentType": "TEXT",
                "description": (
                    "EVALUACIÓN FINAL — Arquitectura de Software\n"
                    "Tiempo: 60 minutos | Puntaje: 100 puntos\n\n"
                    "Proyecto: Diseña la arquitectura de una plataforma de aprendizaje en línea.\n\n"
                    "Requisitos funcionales:\n"
                    "• Usuarios pueden registrarse, comprar cursos y ver videos/PDFs.\n"
                    "• Instructores crean cursos con módulos y lecciones.\n"
                    "• El sistema recomienda cursos basándose en el historial.\n"
                    "• Los estudiantes reciben certificados al completar un curso.\n"
                    "• Debe soportar transmisión de video en vivo (webinars).\n\n"
                    "Requisitos no funcionales:\n"
                    "• 100.000 usuarios activos simultáneos.\n"
                    "• Disponibilidad 99.9%.\n"
                    "• Videos deben cargar en menos de 2 segundos.\n\n"
                    "Entregable 1 — Diagrama C4 Nivel 1 y 2 (30 pts)\n"
                    "Dibuja el diagrama de contexto (nivel 1) y el de contenedores (nivel 2).\n"
                    "Identifica al menos 5 contenedores y describe su responsabilidad.\n\n"
                    "Entregable 2 — Decisiones arquitectónicas (30 pts)\n"
                    "Escribe un ADR para cada una de estas decisiones:\n"
                    "a) Elección de estilo arquitectónico general.\n"
                    "b) Estrategia de almacenamiento de video.\n\n"
                    "Entregable 3 — Trade-offs (40 pts)\n"
                    "El cliente propone usar un único servicio de 'contenido' para gestionar "
                    "cursos, videos, PDFs y certificados. Tú propones separarlo en 3 servicios.\n"
                    "Argumenta ambas posiciones y recomienda la mejor opción para este contexto específico."
                )
              }
            ]
          }
        ]
      }
    ]
  }
]

# ── seed logic ────────────────────────────────────────────────────────────────

def seed():
    print("\n=== Obteniendo tokens ===")
    instr_token = login(INSTRUCTOR_EMAIL, INSTRUCTOR_PASS)
    if not instr_token:
        print("ERROR: No se pudo obtener el token del instructor. Verifica las credenciales.")
        sys.exit(1)
    ok(f"Token instructor OK")

    student_tokens = []
    for email, pwd in STUDENT_CREDS:
        t = login(email, pwd)
        if t:
            student_tokens.append(t)
            ok(f"Token {email}")
        else:
            print(f"  [WARN] No se pudo obtener token para {email}")

    course_ids = []

    for course_data in COURSES:
        info(f"Creando curso: {course_data['title']}")

        # Create course
        cr = post(f"{COURSE_SVC}/courses", {
            "title":       course_data["title"],
            "description": course_data["description"],
            "maxStudents": course_data["maxStudents"]
        }, instr_token)
        if not cr:
            print(f"  [ERROR] No se pudo crear el curso")
            continue
        course_id = cr["id"]
        ok(f"Curso creado: {course_id}")
        course_ids.append(course_id)

        for module_data in course_data["modules"]:
            # Create module
            mr = post(f"{COURSE_SVC}/courses/{course_id}/modules", {
                "title":       module_data["title"],
                "description": module_data.get("description", "")
            }, instr_token)
            if not mr:
                print(f"  [ERROR] No se pudo crear el módulo: {module_data['title']}")
                continue
            module_id = mr["id"]
            ok(f"  Módulo: {module_data['title']}")

            for lesson_data in module_data["lessons"]:
                # Create lesson
                lr = post(f"{COURSE_SVC}/modules/{module_id}/lessons", {
                    "title":           lesson_data["title"],
                    "durationMinutes": lesson_data["durationMinutes"]
                }, instr_token)
                if not lr:
                    print(f"  [ERROR] No se pudo crear la lección: {lesson_data['title']}")
                    continue
                lesson_id = lr["id"]
                ok(f"    Lección: {lesson_data['title']}")

                for content_data in lesson_data.get("contents", []):
                    body = {
                        "title":       content_data["title"],
                        "description": content_data.get("description", ""),
                        "contentType": content_data["contentType"],
                    }
                    if "contentUrl" in content_data:
                        body["contentUrl"] = content_data["contentUrl"]
                    cr2 = post(f"{COURSE_SVC}/lessons/{lesson_id}/contents", body, instr_token)
                    if cr2:
                        ok(f"      Contenido [{content_data['contentType']}]: {content_data['title']}")

        # Publish course
        pr = post(f"{COURSE_SVC}/courses/{course_id}/publish", None, instr_token)
        if pr:
            ok(f"Curso publicado: {course_data['title']}")

    # Enroll all students in all courses
    print("\n=== Inscribiendo estudiantes ===")
    for i, st_token in enumerate(student_tokens):
        email = STUDENT_CREDS[i][0]
        for course_id in course_ids:
            er = post(f"{COURSE_SVC}/enrollments/courses/{course_id}", None, st_token)
            if er:
                ok(f"  {email} inscrito en {course_id}")

    print("\n=== SEED COMPLETADO ===")
    print(f"  Cursos creados: {len(course_ids)}")
    print(f"  Estudiantes inscritos: {len(student_tokens)} en {len(course_ids)} cursos")

if __name__ == "__main__":
    seed()
