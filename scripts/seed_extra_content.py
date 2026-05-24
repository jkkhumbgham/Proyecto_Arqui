#!/usr/bin/env python3
"""Adds PDF and extra video content items to existing lessons."""

import json, sys, urllib.request, urllib.error

USER_SVC   = "http://localhost:8081/api/v1"
COURSE_SVC = "http://localhost:8082/api/v1"

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
        print(f"  [ERROR] login: {body}"); return None
    return body["accessToken"]

def post(url, body, token):
    status, resp = req("POST", url, body, token)
    if status not in (200, 201):
        print(f"  [WARN] {status} {url}: {str(resp)[:100]}"); return None
    return resp

def ok(msg):   print(f"  [OK] {msg}")
def info(msg): print(f"\n=== {msg} ===")

# Lesson IDs from the seed script output
EXTRA_CONTENTS = [

    # ── JAVA ─────────────────────────────────────────────────────────────────

    # "¿Qué es Java y por qué aprenderlo?" — add PDF cheat sheet
    ("4b96aeaa-03e3-4ee0-b6fb-d893dd62d612", {
        "title": "Referencia rápida: Sintaxis Java",
        "contentType": "PDF",
        "contentUrl": "https://introcs.cs.princeton.edu/java/11cheatsheet/cheatsheet.pdf",
        "description": "Cheat sheet con la sintaxis esencial de Java: tipos de datos, operadores, estructuras de control y métodos de String."
    }),

    # "Instalación del entorno de desarrollo" — add video
    ("7f28bc45-5a8c-4e6a-ab7c-e979b2f0e72e", {
        "title": "Video: Instalar JDK e IntelliJ IDEA paso a paso",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=J3QBaGbom_A",
        "description": "Tutorial en video para instalar el JDK y configurar IntelliJ IDEA Community Edition en Windows, macOS y Linux."
    }),

    # "Variables, tipos de datos y operadores" — add PDF
    ("33505795-9f82-4bd5-80fa-895754e06161", {
        "title": "Guía de tipos de datos en Java (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://cs.lmu.edu/~ray/notes/javatypes/javatypes.pdf",
        "description": "Guía de referencia rápida con todos los tipos de datos primitivos de Java, sus rangos, valores por defecto y conversiones."
    }),

    # "Condicionales: if, else if y switch" — add video
    ("aaed6f67-8247-4f7f-8c59-3e09a39d56a4", {
        "title": "Video: Condicionales en Java",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=mA23x39DjbI",
        "description": "Explicación práctica de if, else if, else y switch en Java con ejemplos de la vida real."
    }),

    # "Manejo de excepciones" — add PDF
    ("b3f0af24-90ae-4896-8cbe-3091df527cea", {
        "title": "Guía de excepciones en Java (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://www.oracle.com/technetwork/es/java/exception-140887-zhs.pdf",
        "description": "Documentación oficial sobre el manejo de excepciones en Java: jerarquía, tipos y buenas prácticas."
    }),

    # "Clases, objetos y encapsulamiento" — add video
    ("cf33f5f2-f8de-4ca8-ba08-87a4e28f969b", {
        "title": "Video: Clases y objetos en Java",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=IIAbOobe31Y",
        "description": "Introducción visual a la creación de clases, instanciación de objetos y el concepto de encapsulamiento en Java."
    }),

    # "Herencia y polimorfismo" — add PDF
    ("04a218cc-fafb-4e13-8b8a-7697a0b648e6", {
        "title": "Diagrama de herencia y polimorfismo (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://users.cs.duke.edu/~ola/ap/recurrence.pdf",
        "description": "Material de referencia sobre herencia, polimorfismo y el concepto de casting en Java orientado a objetos."
    }),

    # "Interfaces y clases abstractas" — add video
    ("1d1285ff-9948-4b17-a8bc-305a39bd87e0", {
        "title": "Video: Interfaces vs Clases Abstractas",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=HvPlEJ3LHgE",
        "description": "Comparación visual entre interfaces y clases abstractas en Java: cuándo usar cada una y ejemplos prácticos."
    }),

    # ── BASES DE DATOS ────────────────────────────────────────────────────────

    # "Introducción a las bases de datos relacionales" — add video
    ("27e11e27-6c53-43a5-9fe2-4d05f94296fa", {
        "title": "Video: Bases de datos relacionales en 10 minutos",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=OqjJjpjDRLc",
        "description": "Introducción rápida al modelo relacional, tablas, claves y relaciones entre entidades."
    }),

    # "Creación de tablas con DDL" — add PDF
    ("0479a5c5-eb3f-4cb7-9e19-66f22382a4af", {
        "title": "Referencia rápida: SQL DDL y tipos de datos PostgreSQL",
        "contentType": "PDF",
        "contentUrl": "https://www.postgresqltutorial.com/wp-content/uploads/2018/03/PostgreSQL-Cheat-Sheet.pdf",
        "description": "Cheat sheet con los comandos DDL más usados en PostgreSQL: CREATE, ALTER, DROP y tipos de datos."
    }),

    # "SELECT, FROM, WHERE y ORDER BY" — add video
    ("5dbdca8e-9685-4a99-bab2-4d7844c0d2d9", {
        "title": "Video: Consultas SQL básicas",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=HXV3zeQKqGY",
        "description": "Tutorial completo de SQL para principiantes: SELECT, WHERE, ORDER BY, GROUP BY y funciones de agregación."
    }),
    # Also add PDF to this lesson
    ("5dbdca8e-9685-4a99-bab2-4d7844c0d2d9", {
        "title": "Cheat sheet: SQL SELECT y filtros",
        "contentType": "PDF",
        "contentUrl": "https://learnsql.com/blog/sql-basics-cheat-sheet/sql-basics-cheat-sheet-a4.pdf",
        "description": "Referencia rápida de comandos SELECT en SQL: operadores de comparación, LIKE, IN, BETWEEN y más."
    }),

    # "GROUP BY, HAVING y funciones de agregación" — add PDF
    ("78766c99-cc80-447b-aaaf-13ada2d95180", {
        "title": "Guía de funciones de agregación SQL",
        "contentType": "PDF",
        "contentUrl": "https://learnsql.com/blog/sql-group-by-cheat-sheet/sql-group-by-cheat-sheet-a4.pdf",
        "description": "Referencia visual sobre GROUP BY, HAVING y funciones COUNT, SUM, AVG, MAX, MIN con ejemplos."
    }),

    # "Subconsultas y CTEs" — add video
    ("3558afb0-1920-4086-9199-fbe2fc04ed19", {
        "title": "Video: CTEs y subconsultas avanzadas en SQL",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=K1WeoKxLZ5o",
        "description": "Explicación práctica de subconsultas, CTEs y CTEs recursivas en PostgreSQL con casos de uso reales."
    }),

    # "Índices y optimización" — add PDF
    ("2d5341a3-5be2-4c33-bbe0-e2607ae9ce68", {
        "title": "Guía de índices en PostgreSQL (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://www.postgresql.org/files/documentation/pdf/15/postgresql-15-A4.pdf",
        "description": "Documentación oficial de PostgreSQL sobre índices, tipos disponibles y cómo usar EXPLAIN ANALYZE para optimizar consultas."
    }),

    # "Transacciones y ACID" — add video
    ("226f2cc5-6f00-49eb-8f32-124b231c5aaa", {
        "title": "Video: Transacciones y ACID explicados",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=pomxJOFVcQs",
        "description": "Explicación visual de los principios ACID, transacciones en bases de datos y los niveles de aislamiento."
    }),

    # ── ARQUITECTURA ──────────────────────────────────────────────────────────

    # "¿Qué es la arquitectura de software?" — add video
    ("f4ff9b6a-34d3-449f-bb41-6804c32eda46", {
        "title": "Video: ¿Qué es la arquitectura de software?",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=BrT3AO8bVQY",
        "description": "Introducción al rol del arquitecto de software, las decisiones arquitectónicas y por qué importan."
    }),

    # "Atributos de calidad" — add PDF
    ("f775af2a-3657-4ac1-9425-15f25884d9f9", {
        "title": "ISO 25010 — Atributos de calidad del software (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://iso25000.com/index.php/normas-iso-25000/iso-25010/21-iso-25010",
        "description": "Resumen del estándar ISO 25010 con los atributos de calidad del software y sus subcaracterísticas."
    }),
    # Also add a video on quality attributes
    ("f775af2a-3657-4ac1-9425-15f25884d9f9", {
        "title": "Video: Atributos de calidad y trade-offs",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=H8iHiGGl6Ck",
        "description": "Cómo identificar y priorizar atributos de calidad en arquitecturas de software reales, con ejemplos de trade-offs."
    }),

    # "Arquitectura en Capas y MVC" — add PDF
    ("2ad7283e-a0fb-4d00-bb1f-9fbd0fbaaee2", {
        "title": "Guía: Arquitectura en capas y patrón MVC (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://www.tutorialspoint.com/design_pattern/pdf/design_pattern_mvc_pattern.pdf",
        "description": "Descripción detallada del patrón MVC, la arquitectura en capas y ejemplos de implementación."
    }),

    # "Microservicios" — add PDF
    ("e16c4278-228d-431d-8a04-44920a3d5a9a", {
        "title": "Artículo: Microservices (Martin Fowler) — PDF",
        "contentType": "PDF",
        "contentUrl": "https://martinfowler.com/articles/microservices.pdf",
        "description": "El artículo seminal de Martin Fowler y James Lewis que define los microservicios y sus características."
    }),

    # "Diagramas de arquitectura: el Modelo C4" — add video
    ("9a123495-4054-4d2f-b4af-d96badfbb590", {
        "title": "Video: Modelo C4 explicado por Simon Brown",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=x2-rSnhpw0g",
        "description": "Simon Brown (creador del C4 Model) explica los 4 niveles de diagramas y cómo aplicarlos en proyectos reales."
    }),
    # Also add PDF
    ("9a123495-4054-4d2f-b4af-d96badfbb590", {
        "title": "Referencia rápida: C4 Model (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://c4model.com/review/c4.pdf",
        "description": "Guía de referencia visual del Modelo C4 con los 4 niveles de diagrama y la notación recomendada."
    }),

    # "ADRs" — add video
    ("5f7100a7-aedd-4c19-bc26-223196d531f4", {
        "title": "Video: Architecture Decision Records en la práctica",
        "contentType": "VIDEO",
        "contentUrl": "https://www.youtube.com/watch?v=rwfXkSjFhzc",
        "description": "Cómo escribir ADRs efectivos, qué incluir y cómo integrarlos en el repositorio del proyecto."
    }),

    # "DevOps, CI/CD y contenedores" — add PDF
    ("0138b1ed-e4c4-473a-a720-26ad28a43e16", {
        "title": "Guía: Docker y CI/CD con GitHub Actions (PDF)",
        "contentType": "PDF",
        "contentUrl": "https://resources.github.com/whitepapers/GitHub-Actions-CI-CD.pdf",
        "description": "Guía oficial de GitHub sobre implementación de CI/CD con GitHub Actions, Docker y mejores prácticas DevOps."
    }),
]

def seed():
    info("Obteniendo token")
    token = login(INSTRUCTOR_EMAIL, INSTRUCTOR_PASS)
    if not token:
        print("ERROR: no se pudo obtener token"); sys.exit(1)
    ok("Token OK")

    info("Agregando contenidos (PDF y video) a lecciones existentes")
    for lesson_id, content in EXTRA_CONTENTS:
        result = post(f"{COURSE_SVC}/lessons/{lesson_id}/contents", content, token)
        if result:
            ok(f"[{content['contentType']}] {content['title'][:60]}")

    print("\n=== CONTENIDOS EXTRA AGREGADOS ===")

if __name__ == "__main__":
    seed()
