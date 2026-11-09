# Sistema IoT de Acceso y Monitoreo Térmico (Control OT)

Repositorio oficial de la Evaluación Unidad 2 para la asignatura **Aplicaciones Móviles para IoT (TI3042)** - INACAP.

## Autores
* **John Salvo**
* **Diego Muñoz**

## Descripción del Proyecto
Aplicación móvil nativa desarrollada en **Android Studio (Kotlin)** que opera como un Centro de Control de Tecnología de Operaciones (OT) bidireccional para cuartos de servidores, enlazada mediante Bluetooth Clásico (SPP) a un prototipo basado en Arduino UNO.

### Características Principales
* **Telemetría en Tiempo Real:** Visualización de Temperatura (°C) y Humedad (%) mediante sensor DHT22.
* **Analítica Gráfica:** Historial térmico graficado en tiempo real utilizando `MPAndroidChart`.
* **Actuador Remoto de Emergencia:** Apertura inalámbrica del servomotor SG90 en caso de emergencia (`Comando 'A'`).
* **Modo Lockdown:** Bloqueo lógico remoto del teclado matricial físico ante intentos de fuerza bruta (`Comando 'B'` / `'D'`).
* **Administración Dinámica:** Modificación en caliente del umbral de alerta térmica (`Uxx`) y PIN de acceso (`Cxxxx`).
* **Cumplimiento ISO 27400:** Gestión estricta de permisos de hardware inalámbrico en `AndroidManifest.xml`.

## Estructura del Repositorio
* `/app`: Código fuente de la aplicación Android (Kotlin, XML, Gradle).
* `/arduino`: Sketch en C++ (`Sistema_Acceso_Termico.ino`) para el microcontrolador Arduino UNO.
