#!/usr/bin/env python3
"""Creates real auto-graded assessments linked to evaluation lessons."""

import json, sys, urllib.request, urllib.error

USER_SVC       = "http://localhost:8081/api/v1"
ASSESSMENT_SVC = "http://localhost:8083/api/v1"

INSTRUCTOR_EMAIL = "prof.garcia@puj.edu.co"
INSTRUCTOR_PASS  = "Profesor1234!"

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
        print(f"  [ERROR] login {email}: {body}"); return None
    return body["accessToken"]

def post(url, body, token):
    status, resp = req("POST", url, body, token)
    if status not in (200, 201):
        print(f"  [WARN] POST {url}: {status} → {resp}"); return None
    return resp

def ok(msg):   print(f"  [OK] {msg}")
def info(msg): print(f"\n=== {msg} ===")

# evaluation lesson IDs from the seed (title contains "Evaluación")
# courseId → list of (lessonId, short_title)
EVALUATIONS = {
    # Fundamentos de Programación en Java
    "ab6c87b6-c651-41e8-a3c2-8bc87f715c43": [
        ("3601b8e2-aa91-42f5-8ea3-d9f87247e1ed", "Módulo 1 — Introducción a Java"),
        ("3f1f2ccc-d859-45fb-8042-2f37c06f0672", "Módulo 2 — Estructuras de Control"),
        ("0767487c-17f8-4d6b-8e05-5826a9a2a079", "Final — Programación en Java"),
    ],
    # Bases de Datos Relacionales
    "3b1b2f45-79bb-41c8-a5f8-17436726694b": [
        ("1c7ee676-f492-4cb4-bbf9-a65a31e8a888", "Módulo 1 — Modelo Relacional"),
        ("fbfe0b43-5f21-4dfb-8a67-83d013bed45d", "Módulo 2 — SQL Básico"),
        ("bb19fcd6-94fe-4a05-be98-9a126a0af209", "Final — Bases de Datos"),
    ],
    # Arquitectura de Software
    "c6450656-01a0-4e08-9eaf-b88ec6bb5fff": [
        ("97eed9eb-b002-4e2a-9345-859d49312a81", "Módulo 1 — Fundamentos"),
        ("8c1527ef-751c-413e-9cf7-0fae012cb6f3", "Módulo 2 — Arquitecturas Modernas"),
        ("adfc7320-f133-481a-a40e-254494a5b2d8", "Final — Arquitectura de Software"),
    ],
}

import uuid as _uuid

ASSESSMENT_DATA = {
    # ─── JAVA ─────────────────────────────────────────────────────────────────
    ("ab6c87b6-c651-41e8-a3c2-8bc87f715c43", "3601b8e2-aa91-42f5-8ea3-d9f87247e1ed"): {
        "title": "Evaluación: Introducción a Java",
        "description": "Conceptos básicos de Java: JVM, tipos de datos y sintaxis fundamental.",
        "passingScorePct": 60.0,
        "maxAttempts": 2,
        "questions": [
            {
                "text": "¿Qué significa la sigla JVM?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Java Virtual Machine", "correct": True},
                    {"text": "Java Variable Model", "correct": False},
                    {"text": "Java Version Manager", "correct": False},
                    {"text": "Java Visual Module", "correct": False},
                ]
            },
            {
                "text": "¿Cuál de los siguientes es un tipo primitivo en Java?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "String", "correct": False},
                    {"text": "Integer", "correct": False},
                    {"text": "int", "correct": True},
                    {"text": "Array", "correct": False},
                ]
            },
            {
                "text": "¿Qué imprime el siguiente código?\n\nint a = 10, b = 3;\nSystem.out.println(a % b);",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "3", "correct": False},
                    {"text": "1", "correct": True},
                    {"text": "0.33", "correct": False},
                    {"text": "Error de compilación", "correct": False},
                ]
            },
            {
                "text": "Java es un lenguaje con tipado estático, lo que significa que el tipo de las variables se verifica en tiempo de compilación.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de los siguientes son tipos primitivos válidos en Java? (Selecciona todos los que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "boolean", "correct": True},
                    {"text": "String", "correct": False},
                    {"text": "double", "correct": True},
                    {"text": "char", "correct": True},
                    {"text": "Float", "correct": False},
                ]
            },
            {
                "text": "¿Cuál es la salida de: System.out.println(5 / 2); en Java?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "2.5", "correct": False},
                    {"text": "2", "correct": True},
                    {"text": "3", "correct": False},
                    {"text": "2.0", "correct": False},
                ]
            },
            {
                "text": "La JVM permite que el mismo bytecode de Java se ejecute en cualquier sistema operativo que tenga una JVM instalada.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuál es el método principal (punto de entrada) de cualquier aplicación Java?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "public void start()", "correct": False},
                    {"text": "public static void main(String[] args)", "correct": True},
                    {"text": "public static int main()", "correct": False},
                    {"text": "void run(String args)", "correct": False},
                ]
            },
        ]
    },
    ("ab6c87b6-c651-41e8-a3c2-8bc87f715c43", "3f1f2ccc-d859-45fb-8042-2f37c06f0672"): {
        "title": "Evaluación: Estructuras de Control",
        "description": "Condicionales, bucles y manejo de excepciones en Java.",
        "passingScorePct": 60.0,
        "maxAttempts": 2,
        "questions": [
            {
                "text": "¿Qué imprime el siguiente código?\n\nfor (int i = 0; i < 5; i++) {\n    if (i % 2 == 0) continue;\n    System.out.print(i + \" \");\n}",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "0 2 4", "correct": False},
                    {"text": "1 3", "correct": True},
                    {"text": "0 1 2 3 4", "correct": False},
                    {"text": "1 2 3 4", "correct": False},
                ]
            },
            {
                "text": "La instrucción 'break' dentro de un bucle hace que el programa salte a la siguiente iteración.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Cuál bucle garantiza ejecutarse al menos una vez, incluso si la condición es falsa desde el inicio?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "for", "correct": False},
                    {"text": "while", "correct": False},
                    {"text": "do-while", "correct": True},
                    {"text": "for-each", "correct": False},
                ]
            },
            {
                "text": "¿Qué excepción se lanza cuando se intenta dividir un entero entre cero en Java?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "NullPointerException", "correct": False},
                    {"text": "ArithmeticException", "correct": True},
                    {"text": "NumberFormatException", "correct": False},
                    {"text": "IllegalArgumentException", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes afirmaciones sobre try-catch son correctas? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "El bloque 'finally' siempre se ejecuta, sin importar si hubo excepción", "correct": True},
                    {"text": "Se puede tener múltiples bloques catch para distintas excepciones", "correct": True},
                    {"text": "catch(Exception e) atrapa cualquier excepción", "correct": True},
                    {"text": "El bloque finally es obligatorio", "correct": False},
                ]
            },
            {
                "text": "¿Cuántas veces imprime 'Hola' el siguiente código?\n\nint i = 10;\nwhile (i > 0) {\n    System.out.println(\"Hola\");\n    i -= 3;\n    if (i == 1) break;\n}",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "2", "correct": False},
                    {"text": "3", "correct": True},
                    {"text": "4", "correct": False},
                    {"text": "Bucle infinito", "correct": False},
                ]
            },
            {
                "text": "El switch de Java (versión moderna, Java 14+) puede usarse como expresión y retornar un valor.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuál es la forma correcta de atrapar una excepción de tipo NumberFormatException?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "catch (Error e) { }", "correct": False},
                    {"text": "catch (NumberFormatException e) { }", "correct": True},
                    {"text": "throws NumberFormatException { }", "correct": False},
                    {"text": "handle NumberFormatException { }", "correct": False},
                ]
            },
        ]
    },
    ("ab6c87b6-c651-41e8-a3c2-8bc87f715c43", "0767487c-17f8-4d6b-8e05-5826a9a2a079"): {
        "title": "Evaluación Final: Programación Orientada a Objetos en Java",
        "description": "Clases, herencia, interfaces y polimorfismo. Evaluación final del curso.",
        "passingScorePct": 70.0,
        "maxAttempts": 1,
        "questions": [
            {
                "text": "¿Cuál de los siguientes NO es uno de los 4 pilares de la Programación Orientada a Objetos?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Encapsulamiento", "correct": False},
                    {"text": "Herencia", "correct": False},
                    {"text": "Compilación", "correct": True},
                    {"text": "Polimorfismo", "correct": False},
                ]
            },
            {
                "text": "Una clase abstracta en Java puede ser instanciada directamente con 'new'.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Cuál es la diferencia entre 'extends' e 'implements' en Java?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "No hay diferencia, son intercambiables", "correct": False},
                    {"text": "'extends' es para herencia de clases; 'implements' es para interfaces", "correct": True},
                    {"text": "'implements' es para herencia de clases; 'extends' es para interfaces", "correct": False},
                    {"text": "'extends' solo se usa con clases abstractas", "correct": False},
                ]
            },
            {
                "text": "En Java, una clase puede implementar múltiples interfaces simultáneamente.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Qué anotación se usa para indicar que un método sobreescribe el de la clase padre?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "@Inherit", "correct": False},
                    {"text": "@Super", "correct": False},
                    {"text": "@Override", "correct": True},
                    {"text": "@Extends", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes son características de las interfaces en Java moderno (Java 8+)? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "Pueden tener métodos default con implementación", "correct": True},
                    {"text": "Pueden tener métodos static", "correct": True},
                    {"text": "Pueden tener atributos de instancia", "correct": False},
                    {"text": "Pueden tener métodos abstractos sin cuerpo", "correct": True},
                ]
            },
            {
                "text": "La palabra clave 'super()' en un constructor de clase hija llama al constructor de la clase padre.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Qué es el encapsulamiento?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "La capacidad de una clase de heredar atributos de otra", "correct": False},
                    {"text": "Ocultar los detalles internos de un objeto y exponer solo lo necesario mediante métodos públicos", "correct": True},
                    {"text": "La capacidad de un método de comportarse distinto según el tipo del objeto", "correct": False},
                    {"text": "Crear objetos a partir de una clase", "correct": False},
                ]
            },
            {
                "text": "¿Cuál es la salida del siguiente código?\n\nAnimal a = new Perro();\nSystem.out.println(a.hacerSonido());\n\n(Donde Perro extiende Animal y sobreescribe hacerSonido() para retornar '¡Guau!')",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "Error de compilación", "correct": False},
                    {"text": "El sonido genérico de Animal", "correct": False},
                    {"text": "¡Guau!", "correct": True},
                    {"text": "null", "correct": False},
                ]
            },
            {
                "text": "¿Cuál modificador de acceso hace que un miembro sea visible solo dentro de la misma clase?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "public", "correct": False},
                    {"text": "protected", "correct": False},
                    {"text": "private", "correct": True},
                    {"text": "package-private (sin modificador)", "correct": False},
                ]
            },
        ]
    },

    # ─── BASES DE DATOS ───────────────────────────────────────────────────────
    ("3b1b2f45-79bb-41c8-a5f8-17436726694b", "1c7ee676-f492-4cb4-bbf9-a65a31e8a888"): {
        "title": "Evaluación: Modelo Relacional y DDL",
        "description": "Fundamentos del modelo relacional, normalización y creación de tablas en SQL.",
        "passingScorePct": 60.0,
        "maxAttempts": 2,
        "questions": [
            {
                "text": "¿Qué garantiza una clave primaria (PRIMARY KEY) en una tabla?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Que el valor puede ser NULL", "correct": False},
                    {"text": "Que cada fila tiene un identificador único y no nulo", "correct": True},
                    {"text": "Que la columna es un número entero", "correct": False},
                    {"text": "Que la columna referencia otra tabla", "correct": False},
                ]
            },
            {
                "text": "Una clave foránea (FOREIGN KEY) puede hacer referencia a cualquier columna de otra tabla, no necesariamente a su clave primaria.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué problema resuelve la normalización de bases de datos?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Aumentar la velocidad de las consultas", "correct": False},
                    {"text": "Eliminar redundancia de datos e inconsistencias", "correct": True},
                    {"text": "Reducir el número de tablas necesarias", "correct": False},
                    {"text": "Encriptar los datos sensibles", "correct": False},
                ]
            },
            {
                "text": "¿Cuál de los siguientes tipos de datos es el más apropiado para almacenar precios monetarios en PostgreSQL?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "FLOAT", "correct": False},
                    {"text": "INTEGER", "correct": False},
                    {"text": "DECIMAL(10,2)", "correct": True},
                    {"text": "VARCHAR(20)", "correct": False},
                ]
            },
            {
                "text": "Una tabla que cumple con la 3FN (Tercera Forma Normal) también cumple con la 1FN y la 2FN.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes restricciones SQL son válidas? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "NOT NULL", "correct": True},
                    {"text": "UNIQUE", "correct": True},
                    {"text": "CHECK", "correct": True},
                    {"text": "REQUIRED", "correct": False},
                ]
            },
            {
                "text": "¿Qué relación de cardinalidad describe: 'un estudiante puede inscribirse en muchos cursos, y un curso puede tener muchos estudiantes'?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "1:1", "correct": False},
                    {"text": "1:N", "correct": False},
                    {"text": "N:M", "correct": True},
                    {"text": "0:1", "correct": False},
                ]
            },
            {
                "text": "¿Qué comando SQL se usa para eliminar una tabla y todos sus datos permanentemente?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "DELETE TABLE clientes;", "correct": False},
                    {"text": "REMOVE TABLE clientes;", "correct": False},
                    {"text": "DROP TABLE clientes;", "correct": True},
                    {"text": "TRUNCATE clientes;", "correct": False},
                ]
            },
        ]
    },
    ("3b1b2f45-79bb-41c8-a5f8-17436726694b", "fbfe0b43-5f21-4dfb-8a67-83d013bed45d"): {
        "title": "Evaluación: SQL Básico y JOINs",
        "description": "Consultas SELECT, filtros, agrupaciones y combinación de tablas con JOINs.",
        "passingScorePct": 60.0,
        "maxAttempts": 2,
        "questions": [
            {
                "text": "¿Qué cláusula SQL se usa para filtrar filas ANTES de agrupar?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "HAVING", "correct": False},
                    {"text": "WHERE", "correct": True},
                    {"text": "FILTER", "correct": False},
                    {"text": "GROUP BY", "correct": False},
                ]
            },
            {
                "text": "HAVING se usa para filtrar grupos resultantes de un GROUP BY.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Qué tipo de JOIN retorna TODAS las filas de la tabla izquierda, y NULL donde no hay coincidencia en la derecha?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "INNER JOIN", "correct": False},
                    {"text": "LEFT JOIN", "correct": True},
                    {"text": "RIGHT JOIN", "correct": False},
                    {"text": "CROSS JOIN", "correct": False},
                ]
            },
            {
                "text": "¿Cuál función SQL cuenta el número de filas en un grupo?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "SUM()", "correct": False},
                    {"text": "TOTAL()", "correct": False},
                    {"text": "COUNT()", "correct": True},
                    {"text": "NUMBER()", "correct": False},
                ]
            },
            {
                "text": "Un INNER JOIN entre dos tablas puede devolver más filas que cualquiera de las tablas originales si hay duplicados en la condición de JOIN.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes son funciones de agregación válidas en SQL? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "AVG()", "correct": True},
                    {"text": "MAX()", "correct": True},
                    {"text": "FIRST()", "correct": False},
                    {"text": "MIN()", "correct": True},
                ]
            },
            {
                "text": "¿Qué cláusula SQL ordena los resultados de una consulta?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "SORT BY", "correct": False},
                    {"text": "ORDER BY", "correct": True},
                    {"text": "ARRANGE BY", "correct": False},
                    {"text": "GROUP BY", "correct": False},
                ]
            },
            {
                "text": "El operador ILIKE en PostgreSQL es equivalente a LIKE pero no distingue mayúsculas de minúsculas.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
        ]
    },
    ("3b1b2f45-79bb-41c8-a5f8-17436726694b", "bb19fcd6-94fe-4a05-be98-9a126a0af209"): {
        "title": "Evaluación Final: Bases de Datos Relacionales",
        "description": "SQL avanzado, índices, transacciones y principios ACID. Evaluación final del curso.",
        "passingScorePct": 70.0,
        "maxAttempts": 1,
        "questions": [
            {
                "text": "¿Qué significa ACID en el contexto de transacciones de base de datos?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "Atomicity, Consistency, Isolation, Durability", "correct": True},
                    {"text": "Automatic, Concurrent, Indexed, Durable", "correct": False},
                    {"text": "Atomicity, Consistency, Indexed, Distributed", "correct": False},
                    {"text": "Async, Commit, Isolation, Database", "correct": False},
                ]
            },
            {
                "text": "Un índice en una base de datos siempre mejora tanto las consultas SELECT como las operaciones INSERT/UPDATE/DELETE.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué comando PostgreSQL permite ver el plan de ejecución de una consulta?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "ANALYZE", "correct": False},
                    {"text": "EXPLAIN", "correct": True},
                    {"text": "DESCRIBE", "correct": False},
                    {"text": "SHOW PLAN", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes afirmaciones sobre las CTEs (WITH) son correctas? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "Mejoran la legibilidad de consultas complejas", "correct": True},
                    {"text": "Pueden ser recursivas", "correct": True},
                    {"text": "Solo se pueden usar en DELETE", "correct": False},
                    {"text": "Se definen antes del SELECT principal con la palabra WITH", "correct": True},
                ]
            },
            {
                "text": "Si una transacción hace ROLLBACK, todos los cambios realizados dentro de ella son revertidos.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuál nivel de aislamiento de transacciones protege contra Dirty Reads, Non-Repeatable Reads y Phantom Reads?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "READ UNCOMMITTED", "correct": False},
                    {"text": "READ COMMITTED", "correct": False},
                    {"text": "REPEATABLE READ", "correct": False},
                    {"text": "SERIALIZABLE", "correct": True},
                ]
            },
            {
                "text": "¿Qué tipo de índice en PostgreSQL es más adecuado para búsquedas de texto completo en columnas JSONB?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "B-Tree", "correct": False},
                    {"text": "Hash", "correct": False},
                    {"text": "GIN", "correct": True},
                    {"text": "BRIN", "correct": False},
                ]
            },
            {
                "text": "Una subconsulta correlacionada hace referencia a columnas de la consulta exterior.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
        ]
    },

    # ─── ARQUITECTURA ─────────────────────────────────────────────────────────
    ("c6450656-01a0-4e08-9eaf-b88ec6bb5fff", "97eed9eb-b002-4e2a-9345-859d49312a81"): {
        "title": "Evaluación: Fundamentos de Arquitectura de Software",
        "description": "Definición, atributos de calidad y estilos arquitectónicos.",
        "passingScorePct": 60.0,
        "maxAttempts": 2,
        "questions": [
            {
                "text": "¿Cuál de los siguientes atributos de calidad hace referencia al porcentaje de tiempo que un sistema está operativo?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Rendimiento", "correct": False},
                    {"text": "Disponibilidad", "correct": True},
                    {"text": "Seguridad", "correct": False},
                    {"text": "Mantenibilidad", "correct": False},
                ]
            },
            {
                "text": "Mejorar un atributo de calidad (como la seguridad) nunca afecta negativamente a otros atributos (como el rendimiento).",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Cuál estilo arquitectónico organiza el software en niveles horizontales como Presentación → Negocio → Datos?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Microservicios", "correct": False},
                    {"text": "Arquitectura en Capas", "correct": True},
                    {"text": "Event-Driven", "correct": False},
                    {"text": "Serverless", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de los siguientes son atributos de calidad según ISO 25010? (Selecciona todos los que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "Mantenibilidad", "correct": True},
                    {"text": "Disponibilidad", "correct": True},
                    {"text": "Velocidad de escritura del equipo", "correct": False},
                    {"text": "Testeabilidad", "correct": True},
                ]
            },
            {
                "text": "La arquitectura de software y el diseño de software son exactamente lo mismo.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué significa 'escalar horizontalmente' un servicio?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Agregar más RAM y CPU al mismo servidor", "correct": False},
                    {"text": "Agregar más instancias del servicio en paralelo", "correct": True},
                    {"text": "Optimizar el código para usar menos memoria", "correct": False},
                    {"text": "Reducir el número de dependencias del servicio", "correct": False},
                ]
            },
            {
                "text": "Una startup con 3 desarrolladores que lanza su primer producto debería preferir microservicios sobre un monolito modular.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué táctica arquitectónica ayuda a prevenir que el fallo de un servicio se propague a otros servicios?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Load Balancer", "correct": False},
                    {"text": "Circuit Breaker", "correct": True},
                    {"text": "API Gateway", "correct": False},
                    {"text": "Service Mesh", "correct": False},
                ]
            },
        ]
    },
    ("c6450656-01a0-4e08-9eaf-b88ec6bb5fff", "8c1527ef-751c-413e-9cf7-0fae012cb6f3"): {
        "title": "Evaluación: Arquitecturas Modernas",
        "description": "Microservicios, Event-Driven Architecture y patrones de integración.",
        "passingScorePct": 60.0,
        "maxAttempts": 2,
        "questions": [
            {
                "text": "¿Cuál es el principio de 'Database per Service' en microservicios?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Todos los microservicios comparten la misma base de datos", "correct": False},
                    {"text": "Cada microservicio tiene su propia base de datos, independiente de los demás", "correct": True},
                    {"text": "Los microservicios no pueden usar bases de datos", "correct": False},
                    {"text": "La base de datos se replica en cada instancia del servicio", "correct": False},
                ]
            },
            {
                "text": "En una arquitectura orientada a eventos, el productor necesita conocer la identidad de cada consumidor de sus eventos.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué patrón permite mantener consistencia entre microservicios sin usar transacciones distribuidas ACID?",
                "type": "SINGLE_CHOICE", "points": 3.0,
                "options": [
                    {"text": "API Gateway", "correct": False},
                    {"text": "SAGA", "correct": True},
                    {"text": "CQRS", "correct": False},
                    {"text": "Circuit Breaker", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes son ventajas de la arquitectura orientada a eventos? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "Desacoplamiento temporal entre productores y consumidores", "correct": True},
                    {"text": "Facilidad para agregar nuevos consumidores sin modificar productores", "correct": True},
                    {"text": "Flujo de datos fácil de depurar", "correct": False},
                    {"text": "Alta escalabilidad independiente por consumidor", "correct": True},
                ]
            },
            {
                "text": "CQRS (Command Query Responsibility Segregation) separa las operaciones de lectura de las de escritura.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Qué componente actúa como punto de entrada único que enruta peticiones a los microservicios correctos?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Message Broker", "correct": False},
                    {"text": "Service Registry", "correct": False},
                    {"text": "API Gateway", "correct": True},
                    {"text": "Load Balancer", "correct": False},
                ]
            },
            {
                "text": "La consistencia eventual en microservicios significa que los datos siempre están inmediatamente sincronizados entre servicios.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué herramienta de código abierto se usa comúnmente como message broker para implementar EDA?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Nginx", "correct": False},
                    {"text": "Redis", "correct": False},
                    {"text": "RabbitMQ", "correct": True},
                    {"text": "PostgreSQL", "correct": False},
                ]
            },
        ]
    },
    ("c6450656-01a0-4e08-9eaf-b88ec6bb5fff", "adfc7320-f133-481a-a40e-254494a5b2d8"): {
        "title": "Evaluación Final: Arquitectura de Software",
        "description": "C4 Model, ADRs, DevOps y CI/CD. Evaluación final del curso.",
        "passingScorePct": 70.0,
        "maxAttempts": 1,
        "questions": [
            {
                "text": "¿Cuántos niveles tiene el Modelo C4 para diagramas de arquitectura?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "2", "correct": False},
                    {"text": "3", "correct": False},
                    {"text": "4", "correct": True},
                    {"text": "5", "correct": False},
                ]
            },
            {
                "text": "El nivel 1 del Modelo C4 (Diagrama de Contexto) muestra los componentes internos del sistema en detalle.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": False},
                    {"text": "Falso", "correct": True},
                ]
            },
            {
                "text": "¿Qué es un ADR (Architecture Decision Record)?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Un tipo de diagrama de clases UML", "correct": False},
                    {"text": "Un documento que registra una decisión arquitectónica con su contexto, alternativas y consecuencias", "correct": True},
                    {"text": "Un reporte de errores en producción", "correct": False},
                    {"text": "Una herramienta de monitoreo de servicios", "correct": False},
                ]
            },
            {
                "text": "¿Cuáles de las siguientes son partes típicas de un ADR? (Selecciona todas las que apliquen)",
                "type": "MULTIPLE_CHOICE", "points": 4.0,
                "options": [
                    {"text": "Contexto", "correct": True},
                    {"text": "Decisión tomada", "correct": True},
                    {"text": "Código fuente completo de la implementación", "correct": False},
                    {"text": "Alternativas consideradas", "correct": True},
                ]
            },
            {
                "text": "CI (Integración Continua) incluye la ejecución automática de pruebas con cada push al repositorio.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿Cuál es la principal ventaja de usar contenedores Docker?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Reemplaza la necesidad de una base de datos", "correct": False},
                    {"text": "Permite empaquetar la aplicación con sus dependencias para ejecutarla de forma consistente en cualquier entorno", "correct": True},
                    {"text": "Hace el código más rápido automáticamente", "correct": False},
                    {"text": "Elimina la necesidad de escribir pruebas unitarias", "correct": False},
                ]
            },
            {
                "text": "Kubernetes es una herramienta de orquestación de contenedores que gestiona el despliegue y escalado automático.",
                "type": "TRUE_FALSE", "points": 2.0,
                "options": [
                    {"text": "Verdadero", "correct": True},
                    {"text": "Falso", "correct": False},
                ]
            },
            {
                "text": "¿En qué nivel del Modelo C4 se muestran los microservicios, bases de datos y aplicaciones web de un sistema?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "Nivel 1 — Contexto del Sistema", "correct": False},
                    {"text": "Nivel 2 — Contenedores", "correct": True},
                    {"text": "Nivel 3 — Componentes", "correct": False},
                    {"text": "Nivel 4 — Código", "correct": False},
                ]
            },
            {
                "text": "¿Cuál de los siguientes servicios gratuitos puede usarse para implementar un pipeline de CI/CD sin costo?",
                "type": "SINGLE_CHOICE", "points": 2.0,
                "options": [
                    {"text": "GitHub Actions", "correct": True},
                    {"text": "AWS CodePipeline", "correct": False},
                    {"text": "Azure DevOps Enterprise", "correct": False},
                    {"text": "Jenkins Enterprise", "correct": False},
                ]
            },
        ]
    },
}

def seed():
    info("Obteniendo token del instructor")
    token = login(INSTRUCTOR_EMAIL, INSTRUCTOR_PASS)
    if not token:
        print("ERROR: no se pudo obtener token"); sys.exit(1)
    ok("Token OK")

    for (course_id, lesson_id), data in ASSESSMENT_DATA.items():
        info(f"Creando: {data['title']}")
        body = {
            "title":          data["title"],
            "courseId":       course_id,
            "lessonId":       lesson_id,
            "description":    data["description"],
            "passingScorePct": data["passingScorePct"],
            "maxAttempts":    data["maxAttempts"],
            "questions":      data["questions"],
        }
        result = post(f"{ASSESSMENT_SVC}/assessments", body, token)
        if result:
            q_count = len(result.get("questions", []))
            ok(f"  Creada con {q_count} preguntas | total: {result.get('totalPoints', '?')} pts")

    print("\n=== EVALUACIONES CREADAS ===")

if __name__ == "__main__":
    seed()
