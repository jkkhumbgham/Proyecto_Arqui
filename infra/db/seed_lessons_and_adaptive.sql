-- =============================================================
-- Seed: Lessons + Adaptive Rules
-- Apply AFTER the application has started at least once (so
-- Hibernate auto-DDL has created all tables).
--
-- Prerequisites: a course with at least one module must exist.
-- The UUIDs below must be replaced with real IDs from your DB.
-- Run this script to see existing courses + modules:
--
--   SELECT c.id AS course_id, c.title, m.id AS module_id, m.title AS module_title
--   FROM courses.courses c JOIN courses.modules m ON m.course_id = c.id
--   WHERE c.deleted_at IS NULL AND m.deleted_at IS NULL
--   ORDER BY c.created_at, m.order_index;
--
-- Then replace COURSE_ID_HERE and MODULE_ID_HERE below.
-- =============================================================

-- ---- Variables (edit these) ----
-- Set these to real UUIDs from your database:
--   :course_id   -> an existing course ID
--   :module_id   -> an existing module ID belonging to that course
--   :instructor_id -> the instructor UUID who owns the course

-- Example: run interactively via psql -v course_id="'...'" -v module_id="'...'" ...
-- OR just replace the placeholders and run directly.

\set course_id     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
\set module_id     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
\set instructor_id 'cccccccc-cccc-cccc-cccc-cccccccccccc'

-- ---- Lessons ----

-- Lesson 1: Main lesson (will have an assessment)
INSERT INTO courses.lessons (id, module_id, title, content, order_index, duration_minutes, created_at)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeee01',
    :'module_id',
    'Introducción a la programación orientada a objetos',
    'La programación orientada a objetos (POO) es un paradigma de programación que organiza el código
en torno a objetos en lugar de funciones y lógica.

Conceptos clave:
- Clase: plantilla para crear objetos.
- Objeto: instancia de una clase.
- Encapsulamiento: ocultar detalles de implementación.
- Herencia: una clase puede heredar atributos y métodos de otra.
- Polimorfismo: múltiples formas de hacer lo mismo.',
    1,
    20,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Lesson 2: Supplementary lesson (shown when student fails the assessment)
INSERT INTO courses.lessons (id, module_id, title, content, order_index, duration_minutes, created_at)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeee02',
    :'module_id',
    'Repaso: Fundamentos de POO (material de refuerzo)',
    'Este material te ayudará a reforzar los conceptos básicos de POO.

Recuerda los 4 pilares:
1. Abstracción: modelar entidades del mundo real como clases.
2. Encapsulamiento: los datos internos se protegen del acceso directo.
3. Herencia: reutilizar código mediante jerarquías de clases.
4. Polimorfismo: objetos de diferentes clases responden al mismo mensaje.

Ejemplo en Java:
  class Animal {
    String nombre;
    void hablar() { System.out.println("..."); }
  }
  class Perro extends Animal {
    @Override void hablar() { System.out.println("¡Guau!"); }
  }',
    2,
    15,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- ---- Assessment tied to Lesson 1 ----

INSERT INTO assessments.assessments (id, title, course_id, lesson_id, instructor_id,
    description, max_attempts, passing_score_pct, randomize_questions, created_at, updated_at)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'Evaluación: POO Básica',
    :'course_id',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeee01',
    :'instructor_id',
    'Evalúa tu comprensión de los conceptos básicos de programación orientada a objetos.',
    3,
    60.0,
    false,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Question 1 (SINGLE_CHOICE)
INSERT INTO assessments.questions (id, assessment_id, text, type, points, order_index)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddd01',
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    '¿Qué es una clase en programación orientada a objetos?',
    'SINGLE_CHOICE',
    1.0,
    1
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddd11', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
     'Una instancia de un objeto', false, 1),
    ('dddddddd-dddd-dddd-dddd-dddddddddd12', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
     'Una plantilla para crear objetos', true, 2),
    ('dddddddd-dddd-dddd-dddd-dddddddddd13', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
     'Un tipo de dato primitivo', false, 3),
    ('dddddddd-dddd-dddd-dddd-dddddddddd14', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
     'Un método especial', false, 4)
ON CONFLICT (id) DO NOTHING;

-- Question 2 (TRUE_FALSE)
INSERT INTO assessments.questions (id, assessment_id, text, type, points, order_index)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddd02',
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'El encapsulamiento permite ocultar los detalles internos de una clase.',
    'TRUE_FALSE',
    1.0,
    2
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO assessments.answer_options (id, question_id, text, is_correct, order_index)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddd21', 'dddddddd-dddd-dddd-dddd-dddddddddd02',
     'Verdadero', true, 1),
    ('dddddddd-dddd-dddd-dddd-dddddddddd22', 'dddddddd-dddd-dddd-dddd-dddddddddd02',
     'Falso', false, 2)
ON CONFLICT (id) DO NOTHING;

-- ---- Adaptive Rule ----
-- If student scores below 60% on the assessment, redirect to supplementary lesson

INSERT INTO assessments.adaptive_rules
    (id, assessment_id, course_id, lesson_id, instructor_id,
     score_threshold_pct, supplementary_lesson_id, message, active, created_at, updated_at)
VALUES (
    'aaaabbbb-cccc-dddd-eeee-ffffaaaabbbb',
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    :'course_id',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeee01',
    :'instructor_id',
    60.0,
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeee02',
    'Tu puntuación está por debajo del 60%. Te recomendamos repasar el material de refuerzo antes de intentarlo de nuevo.',
    true,
    NOW(),
    NOW()
)
ON CONFLICT ON CONSTRAINT uq_adaptive_rule DO NOTHING;
