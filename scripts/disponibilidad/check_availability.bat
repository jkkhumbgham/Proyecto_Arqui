@echo off
REM =============================================================================
REM check_availability.bat — Pruebas Técnicas de Disponibilidad (TD-001..010)
REM Plan Integral de Pruebas · PUJ Arquitectura de Software 2026
REM
REM Uso: check_availability.bat [BASE_URL_USERS] [BASE_URL_COURSES] ...
REM Prerrequisito: docker compose up -d
REM =============================================================================

setlocal EnableDelayedExpansion

set USERS=%1
if "%USERS%"=="" set USERS=http://localhost:8081

set COURSES=%2
if "%COURSES%"=="" set COURSES=http://localhost:8082

set ASSESSMENTS=%3
if "%ASSESSMENTS%"=="" set ASSESSMENTS=http://localhost:8083

set COLLAB=%4
if "%COLLAB%"=="" set COLLAB=http://localhost:8084

set ANALYTICS=%5
if "%ANALYTICS%"=="" set ANALYTICS=http://localhost:5000

set PASS=0
set FAIL=0
set TOKEN=

echo.
echo ============================================================
echo  PRUEBAS DE DISPONIBILIDAD -- Plan Pruebas PUJ 2026
echo ============================================================
echo.

REM ── Helper: check HTTP status ─────────────────────────────────────────────
REM No hay funciones en .bat, se usa GOTO + variables globales

REM ── TD-001: Login exitoso ─────────────────────────────────────────────────
echo [TEST] TD-001 . Login con credenciales validas
curl -s -o NUL -w "%%{http_code}" --max-time 5 -X POST "%USERS%/api/v1/auth/login" ^
     -H "Content-Type: application/json" ^
     -d "{\"email\":\"estudiante@puj.edu.co\",\"password\":\"Password1!\"}" > %TEMP%\td001.txt 2>NUL
set /p HTTP_TD001=<%TEMP%\td001.txt
if "!HTTP_TD001!"=="200" (
    echo   [PASS] HTTP 200
    set /a PASS+=1
) else (
    echo   [FAIL] HTTP !HTTP_TD001! esperado 200
    set /a FAIL+=1
)

REM ── TD-002: Bloqueo tras 5 intentos fallidos ──────────────────────────────
echo [TEST] TD-002 . 5 intentos fallidos - cuenta bloqueada
for /L %%i in (1,1,5) do (
    curl -s -o NUL --max-time 5 -X POST "%USERS%/api/v1/auth/login" ^
         -H "Content-Type: application/json" ^
         -d "{\"email\":\"bloqueo@puj.edu.co\",\"password\":\"Incorrecta!\"}" >NUL 2>&1
)
curl -s -o NUL -w "%%{http_code}" --max-time 5 -X POST "%USERS%/api/v1/auth/login" ^
     -H "Content-Type: application/json" ^
     -d "{\"email\":\"bloqueo@puj.edu.co\",\"password\":\"Incorrecta!\"}" > %TEMP%\td002.txt 2>NUL
set /p HTTP_TD002=<%TEMP%\td002.txt
if "!HTTP_TD002!"=="401" (
    echo   [PASS] HTTP 401 - cuenta bloqueada
    set /a PASS+=1
) else (
    echo   [FAIL] HTTP !HTTP_TD002! esperado 401
    set /a FAIL+=1
)

REM ── TD-003: GET /auth/me ──────────────────────────────────────────────────
echo [TEST] TD-003 . GET /auth/me con token
curl -s -o NUL -w "%%{http_code}" --max-time 5 ^
     "%USERS%/api/v1/auth/me" ^
     -H "Authorization: Bearer !TOKEN!" > %TEMP%\td003.txt 2>NUL
set /p HTTP_TD003=<%TEMP%\td003.txt
if "!HTTP_TD003!"=="200" (echo   [PASS] HTTP 200 & set /a PASS+=1) else (echo   [FAIL] HTTP !HTTP_TD003! & set /a FAIL+=1)

REM ── TD-004: Catálogo cursos ───────────────────────────────────────────────
echo [TEST] TD-004 . GET /courses publicados
curl -s -o NUL -w "%%{http_code}" --max-time 5 "%COURSES%/api/v1/courses?page=0&size=10" > %TEMP%\td004.txt 2>NUL
set /p HTTP_TD004=<%TEMP%\td004.txt
if "!HTTP_TD004!"=="200" (echo   [PASS] HTTP 200 & set /a PASS+=1) else (echo   [FAIL] HTTP !HTTP_TD004! & set /a FAIL+=1)

REM ── TD-005..009: Health checks ────────────────────────────────────────────
for %%S in (user-service:%USERS% course-service:%COURSES% assessment-service:%ASSESSMENTS% collaboration-service:%COLLAB% analytics-service:%ANALYTICS%) do (
    for /F "tokens=1,2 delims=:" %%A in ("%%S") do (
        set SVC=%%A
        set URL=%%B
    )
    echo [TEST] TD-00x . Health check !SVC!
    curl -s -o NUL -w "%%{http_code}" --max-time 5 "!URL!/api/v1/health" > %TEMP%\tdh.txt 2>NUL
    set /p HTTP_TDH=<%TEMP%\tdh.txt
    if "!HTTP_TDH!"=="200" (echo   [PASS] HTTP 200 & set /a PASS+=1) else (echo   [FAIL] HTTP !HTTP_TDH! & set /a FAIL+=1)
)

REM ── TD-010: Dashboard analytics ──────────────────────────────────────────
echo [TEST] TD-010 . GET /analytics/dashboard/summary ADMIN
curl -s -o NUL -w "%%{http_code}" --max-time 5 ^
     "%ANALYTICS%/api/v1/analytics/dashboard/summary" ^
     -H "Authorization: Bearer !TOKEN!" > %TEMP%\td010.txt 2>NUL
set /p HTTP_TD010=<%TEMP%\td010.txt
if "!HTTP_TD010!"=="200" (echo   [PASS] HTTP 200 & set /a PASS+=1) else (echo   [FAIL] HTTP !HTTP_TD010! & set /a FAIL+=1)

echo.
echo ============================================================
echo  RESULTADOS: %PASS% PASS . %FAIL% FAIL
echo ============================================================
echo.

if %FAIL% GTR 0 (exit /b 1)
exit /b 0
