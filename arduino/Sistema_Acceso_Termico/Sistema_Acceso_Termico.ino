#include <Keypad.h>
#include <Servo.h>
#include <SoftwareSerial.h>
#include <DHT.h>

// ==========================================
// PINES Y COMPONENTES
// ==========================================
const int pinServo  = 13;
const int pinDHT    = 2;   
const int pinAlerta = A0;  

#define DHTTYPE DHT22     
DHT dht(pinDHT, DHTTYPE);
SoftwareSerial BT(10, 9); 
Servo cerradura;

// ==========================================
// TECLADO MATRICIAL
// ==========================================
const byte FILAS = 4;
const byte COLUMNAS = 4;
char teclas[FILAS][COLUMNAS] = {
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
};
byte pinesFilas[FILAS] = {3, 4, 5, 6};
byte pinesColumnas[COLUMNAS] = {7, 8, 11, 12};
Keypad teclado = Keypad(makeKeymap(teclas), pinesFilas, pinesColumnas, FILAS, COLUMNAS);

// ==========================================
// VARIABLES DE CONTROL DINÁMICO
// ==========================================
String claveCorrecta = "1234";
float umbralTemp = 50.0;       
bool sistemaBloqueado = false; 
String claveIngresada = "";
unsigned long temporizador = 0;

void setup() {
  Serial.begin(9600); 
  BT.begin(9600);     
  
  dht.begin();
  pinMode(pinAlerta, OUTPUT);
  digitalWrite(pinAlerta, LOW); 
  
  cerradura.attach(pinServo);
  cerradura.write(0); 

  Serial.println("--- Sistema Fisico Iniciado ---");
  Serial.println("Modo Avanzado: Control Bidireccional Activo\n");
}

void loop() {
  // ------------------------------------------------
  // 1. ESCUCHAR COMANDOS DESDE LA APP MÓVIL
  // ------------------------------------------------
  if (BT.available()) {
    String comando = BT.readStringUntil('\n'); 
    comando.trim(); 
    
    if(comando.length() > 0) {
      char tipoComando = comando.charAt(0); 
      
      if (tipoComando == 'A') {
        Serial.println("\n[ APP ] Apertura remota activada!");
        BT.println("CONFIRMADO: PUERTA ABIERTA"); 
        cerradura.write(90); 
        delay(4000);         
        cerradura.write(0);  
        BT.println("CONFIRMADO: PUERTA CERRADA");
        Serial.println("Puerta cerrada automaticamente.\n");
        
      } else if (tipoComando == 'B') {
        sistemaBloqueado = true;
        Serial.println("\n[ APP ] SISTEMA EN LOCKDOWN!");
        BT.println("CONFIRMADO: MODO LOCKDOWN ACTIVO"); 
        
      } else if (tipoComando == 'D') {
        sistemaBloqueado = false;
        Serial.println("\n[ APP ] Lockdown desactivado.");
        BT.println("CONFIRMADO: SISTEMA DESBLOQUEADO"); 
        
      } else if (tipoComando == 'U') {
        String valorStr = comando.substring(1);
        umbralTemp = valorStr.toFloat();
        Serial.print("\n[ APP ] Nuevo umbral de alerta: "); Serial.println(umbralTemp);
        BT.print("CONFIRMADO: UMBRAL A "); BT.print(umbralTemp); BT.println(" C");
        
      } else if (tipoComando == 'C') {
        claveCorrecta = comando.substring(1);
        Serial.print("\n[ APP ] Clave cambiada a: "); Serial.println(claveCorrecta);
        BT.println("CONFIRMADO: PIN ACTUALIZADO");
      }
    }
  }

  // ------------------------------------------------
  // 2. MONITOREO AMBIENTAL FÍSICO
  // ------------------------------------------------
  if(millis() - temporizador > 10000) {
    if(claveIngresada == "") {
      float temperatura = dht.readTemperature();
      float humedad = dht.readHumidity();

      if (!isnan(temperatura) && !isnan(humedad)) {
        BT.print("DATA,");
        BT.print(temperatura);
        BT.print(",");
        BT.println(humedad);

        if (temperatura > umbralTemp) {
          Serial.println(">>> ALERTA: SOBRECALENTAMIENTO <<<");
          BT.println("!!! ALERTA CRITICA: UMBRAL SUPERADO !!!");
          digitalWrite(pinAlerta, HIGH); 
        } else {
          digitalWrite(pinAlerta, LOW);  
        }
      }
    }
    temporizador = millis(); 
  }

  // ------------------------------------------------
  // 3. CONTROL DE ACCESO LOCAL
  // ------------------------------------------------
  char tecla = teclado.getKey();
  
  if (tecla) {
    if (sistemaBloqueado) {
      Serial.println("\nTeclado inhabilitado por Lockdown.");
      BT.println("ALERTA: Intento de uso de teclado durante Lockdown");
      return; 
    }

    if (tecla == '#') { 
      if (claveIngresada == claveCorrecta) {
        Serial.println("\n[ LOCAL ] Abriendo puerta...");
        BT.println("CONFIRMADO: PUERTA ABIERTA"); 
        cerradura.write(90); 
        delay(4000);         
        cerradura.write(0);  
        BT.println("CONFIRMADO: PUERTA CERRADA");
      } else {
        Serial.println("\n[ ERROR ] Clave Incorrecta.\n");
        BT.println("ALERTA: INTENTO FALLIDO"); 
      }
      claveIngresada = ""; 
      
    } else if (tecla == '*') {
      claveIngresada = ""; 
      Serial.println("\nIngreso borrado.\n");
    } else {
      claveIngresada += tecla;
      Serial.print("*"); 
    }
  }
}
