# 🚀 GUÍA RÁPIDA DE COMANDOS DEL BACKEND - VOICEPAY SYSTEM

Esta guía práctica contiene todos los comandos esenciales para administrar, compilar, arrancar y probar el backend de microservicios de **VoicePay**. Puedes guardar este archivo en tu repositorio para tenerlo siempre a mano y usarlo desde tu terminal preferida (PowerShell, CMD o Git Bash).

---

## 📋 1. Mapa de Servicios y Puertos

El backend consta de **5 microservicios Spring Boot** (Java 21) comunicados entre sí, y una pila de observabilidad/monitoreo opcional basada en Docker.

| Servicio / Componente | Puerto | Descripción | URL Base / Endpoint Principal |
| :--- | :---: | :--- | :--- |
| **Gateway Service** | `9000` | Puerta de entrada única (API Gateway) para el Frontend. | `http://localhost:9000/` |
| **User Service** | `8080` | Gestión de usuarios, autenticación y seguridad JWT. | `http://localhost:8080/auth/` o `.../users/` |
| **Payment Service** | `8081` | Procesamiento de pagos, facturas y suscripciones. | `http://localhost:8081/payments/` |
| **IVR Service** | `8082` | Flujo telefónico interactivo (Voicebot) y lógica de voz. | `http://localhost:8082/ivr/` |
| **Notification Service**| `8083` | Generación y envío de notificaciones y alertas. | `http://localhost:8083/notifications/` |
| **Elasticsearch** | `9200` | Motor de búsqueda e indexación de logs de servicios. | `http://localhost:9200/` |
| **Kibana** | `5601` | Dashboard visual para explorar y analizar logs (ELK). | `http://localhost:5601/` |
| **Prometheus** | `9090` | Recolección de métricas de rendimiento en tiempo real. | `http://localhost:9090/` |
| **Grafana** | `3000` | Visualización premium de métricas e infraestructura. | `http://localhost:3000/` |

---

## ⚡ 2. Arrancar y Detener los Servicios

### A. Arrancar Todos los Servicios a la Vez (Recomendado)
Usa el script preconfigurado en PowerShell para arrancar todos los microservicios automáticamente en ventanas separadas:
```powershell
.\start-services.ps1
```
*💡 **¿Qué hace?** Establece `JAVA_HOME` para la versión correcta (JDK 21) y abre una consola independiente para cada servicio utilizando el Maven Wrapper local (`./mvnw`).*

---

### B. Arrancar Microservicios Individualmente
Si solo quieres levantar un servicio en particular para depurarlo o hacerle cambios:

* **User Service (Puerto 8080):**
  ```bash
  ./mvnw spring-boot:run -pl user-service
  ```
* **Payment Service (Puerto 8081):**
  ```bash
  ./mvnw spring-boot:run -pl payment-service
  ```
* **IVR Service (Puerto 8082):**
  ```bash
  ./mvnw spring-boot:run -pl ivr-service
  ```
* **Notification Service (Puerto 8083):**
  ```bash
  ./mvnw spring-boot:run -pl notification-service
  ```
* **Gateway Service (Puerto 9000):**
  ```bash
  ./mvnw spring-boot:run -pl gateway-service
  ```

> [!NOTE]
> * `-pl <nombre>` le dice a Maven que ejecute solo el **módulo** (proyecto hijo) especificado.
> * En **Windows (PowerShell)**, usa `.\mvnw` en lugar de `./mvnw`.

---

## 🛠️ 3. Comandos de Construcción y Maven (Compilation & Build)

Maven es el motor de construcción de este proyecto multi-módulo. Aquí tienes los comandos más útiles para limpiar el proyecto y compilarlo desde la raíz:

### Limpiar Carpetas de Compilación (`target/`)
Elimina todas las clases y ejecutables compilados previamente. Es ideal cuando notas comportamientos extraños o quieres asegurar una compilación limpia.
```bash
./mvnw clean
```

### Compilar y Empaquetar Todo el Sistema (Muy Rápido)
Compila y genera los archivos `.jar` de todos los microservicios saltándose la ejecución de pruebas unitarias para ganar velocidad:
```bash
./mvnw clean package -DskipTests
```

### Compilar y Empaquetar un Solo Microservicio
Si hiciste cambios en un solo módulo (por ejemplo, `user-service`) y quieres compilar solo ese componente:
```bash
./mvnw clean package -pl user-service -am -DskipTests
```
*💡 **¿Qué hace `-am`?** Significa "Also Make". Le indica a Maven que si el microservicio depende de librerías comunes locales, también compile esas librerías primero.*

### Ejecutar Pruebas (Tests)
Ejecuta todas las pruebas unitarias y de integración del sistema en todos los módulos:
```bash
./mvnw test
```

### Árbol de Dependencias
Muestra de forma jerárquica todas las dependencias y librerías que utiliza tu proyecto. Es sumamente útil para detectar dependencias duplicadas o conflictos de versiones:
```bash
./mvnw dependency:tree
```

---

## 🐳 4. Infraestructura Auxiliar (Docker)

El proyecto cuenta con un archivo `docker-compose.yml` que levanta la pila de observabilidad **ELK (Elasticsearch + Logstash + Kibana)** y **Prometheus + Grafana** para medir métricas de uso y rendimiento.

* **Levantar la infraestructura (en segundo plano):**
  ```bash
  docker-compose up -d
  ```
* **Ver logs de los contenedores Docker:**
  ```bash
  docker-compose logs -f
  ```
* **Apagar toda la infraestructura Docker:**
  ```bash
  docker-compose down
  ```

---

## 🔌 5. Probar y Simular Rutas / Endpoints desde la Terminal

Para facilitarte la vida, aquí tienes comandos preparados tanto para **cURL (Git Bash / Linux / CMD)** como para **PowerShell** para interactuar con las rutas HTTP del backend.

### Paso 1: Autenticarse y Obtener un Token JWT Seguro
Dado que las rutas críticas del backend están protegidas, primero debes iniciar sesión para obtener un token JWT.

* **Opción A: Desde PowerShell (Guarda el token en una variable automáticamente 🌟):**
  ```powershell
  $login = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"admin@voicepay.com","password":"password123"}'
  $token = $login.token
  Write-Host "¡Token JWT Obtenido e Inicializado!" -ForegroundColor Green
  ```
* **Opción B: Desde Git Bash / CMD (cURL estándar):**
  ```bash
  curl -X POST http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email": "admin@voicepay.com", "password": "password123"}'
  ```

---

### Paso 2: Usar el Token para Consultar Rutas Protegidas

Una vez que tienes tu token, puedes realizar consultas autenticadas a los diferentes servicios:

#### A. Obtener Todos los Usuarios (A través de la API Gateway - Puerto 9000)
* **Desde PowerShell:**
  ```powershell
  Invoke-RestMethod -Uri "http://localhost:9000/users" -Method Get -Headers @{ Authorization = "Bearer $token" }
  ```
* **Desde cURL:**
  ```bash
  curl -X GET http://localhost:9000/users -H "Authorization: Bearer <COPIA_EL_TOKEN_AQUI>"
  ```

#### B. Buscar un Usuario por Teléfono (Servicio de Usuarios - Puerto 8080)
* **Desde PowerShell:**
  ```powershell
  Invoke-RestMethod -Uri "http://localhost:8080/users/phone/+34655443322" -Method Get -Headers @{ Authorization = "Bearer $token" }
  ```
* **Desde cURL:**
  ```bash
  curl -X GET http://localhost:8080/users/phone/+34655443322 -H "Authorization: Bearer <COPIA_EL_TOKEN_AQUI>"
  ```

#### C. Registrar un Nuevo Usuario (Público / Registro)
* **Desde PowerShell:**
  ```powershell
  $nuevoUsuario = @{
      name = "Carlos Ramirez"
      email = "carlos@voicepay.com"
      phoneNumber = "+34611223344"
      password = "mipassword123"
      role = "ROLE_USER"
  } | ConvertTo-Json -Depth 5 -Compress
  
  Invoke-RestMethod -Uri "http://localhost:8080/auth/register" -Method Post -ContentType "application/json" -Body $nuevoUsuario
  ```
* **Desde cURL:**
  ```bash
  curl -X POST http://localhost:8080/auth/register \
    -H "Content-Type: application/json" \
    -d '{"name":"Carlos Ramirez","email":"carlos@voicepay.com","phoneNumber":"+34611223344","password":"mipassword123","role":"ROLE_USER"}'
  ```

---

### Paso 3: Simular una Llamada Entrante Interactiva (IVR Bot)
El backend incluye un script en Python que simula de principio a fin una llamada telefónica interactiva (Pedro llamando para consultar e iniciar el flujo de pago). 

Para ejecutarlo, simplemente abre una terminal en la raíz del proyecto backend y escribe:
```bash
python simulate_ivr_call.py
```
*💡 **¿Por qué es genial?** Este script interactúa secuencialmente con el backend para simular la llamada entrante, espera 8 segundos para que puedas ver las animaciones de flujo iluminarse en verde y azul en el frontend (`http://localhost:5173/ivr-flow`), y luego simula al usuario pulsando `1` para procesar el pago de forma encriptada.*

---

## 🗄️ 6. Comandos de Base de Datos (PostgreSQL)

Los microservicios usan bases de datos PostgreSQL independientes. Si tienes el cliente de consola `psql` configurado en tu PATH o usas una terminal interactiva:

* **Limpiar tablas para empezar de cero con datos nuevos encriptados:**
  Puedes ejecutar el script SQL que limpia las tablas de usuarios y pagos utilizando:
  ```bash
  psql -U postgres -d voicepay_user -f cleanup_db.sql
  ```

---

> [!TIP]
> **Consejo para Windows:** Si estás usando VS Code, puedes abrir una terminal integrada de **PowerShell** y pegar directamente los comandos del bloque de PowerShell. La variable `$token` persistirá mientras no cierres esa sesión de terminal, haciendo que probar rutas seguidas sea extremadamente ágil y cómodo.
