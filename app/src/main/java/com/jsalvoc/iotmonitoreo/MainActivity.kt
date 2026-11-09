package com.jsalvoc.iotmonitoreo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var btSocket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null
    private var isConnected = false

    private lateinit var tvEstado: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvConsola: TextView
    private lateinit var graficoTemp: LineChart

    private val entradasTermicas = ArrayList<Entry>()
    private var tiempoX = 0f

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEstado = findViewById(R.id.tvEstado)
        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvConsola = findViewById(R.id.tvConsola)
        graficoTemp = findViewById(R.id.graficoTemperatura)

        configurarGrafico()

        findViewById<Button>(R.id.btnConectar).setOnClickListener { iniciarConexionBluetooth() }
        findViewById<Button>(R.id.btnEmergencia).setOnClickListener { enviarComando("A\n") }
        findViewById<Button>(R.id.btnLockdown).setOnClickListener { enviarComando("B\n") }
        findViewById<Button>(R.id.btnDesbloquear).setOnClickListener { enviarComando("D\n") }
        
        findViewById<Button>(R.id.btnSetUmbral).setOnClickListener {
            val umbral = findViewById<EditText>(R.id.etNuevoUmbral).text.toString()
            if (umbral.isNotEmpty()) enviarComando("U$umbral\n")
        }
        
        findViewById<Button>(R.id.btnSetClave).setOnClickListener {
            val clave = findViewById<EditText>(R.id.etNuevaClave).text.toString()
            if (clave.isNotEmpty()) enviarComando("C$clave\n")
        }
    }

    private fun configurarGrafico() {
        val descripcion = Description()
        descripcion.text = "Historial Térmico (°C)"
        graficoTemp.description = descripcion
        graficoTemp.setTouchEnabled(true)
        graficoTemp.isDragEnabled = true
        graficoTemp.setScaleEnabled(true)
        graficoTemp.setDrawGridBackground(false)
    }

    @SuppressLint("MissingPermission")
    private fun iniciarConexionBluetooth() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val btAdapter = btManager.adapter

        if (btAdapter == null || !btAdapter.isEnabled) {
            Toast.makeText(this, "Por favor, enciende el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = btAdapter.bondedDevices
        val hc05 = pairedDevices?.firstOrNull()

        if (hc05 != null) {
            tvEstado.text = "Estado: Conectando a ${hc05.name}..."
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    btSocket = hc05.createRfcommSocketToServiceRecord(BT_UUID)
                    btSocket?.connect()
                    outStream = btSocket?.outputStream
                    inStream = btSocket?.inputStream
                    isConnected = true

                    withContext(Dispatchers.Main) {
                        tvEstado.text = "Estado: CONECTADO"
                        tvEstado.setTextColor(android.graphics.Color.GREEN)
                        agregarConsola("Sistema Enlazado Correctamente.")
                    }

                    escucharDatos()

                } catch (e: Exception) {
                    isConnected = false
                    withContext(Dispatchers.Main) {
                        tvEstado.text = "Estado: Error de Conexión"
                        tvEstado.setTextColor(android.graphics.Color.RED)
                    }
                }
            }
        } else {
            Toast.makeText(this, "No hay dispositivos vinculados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun escucharDatos() {
        val buffer = ByteArray(1024)
        var bytes: Int
        var lecturaAcumulada = ""

        while (isConnected) {
            try {
                bytes = inStream?.read(buffer) ?: 0
                if (bytes > 0) {
                    val incoming = String(buffer, 0, bytes)
                    lecturaAcumulada += incoming

                    if (lecturaAcumulada.contains("\n")) {
                        val tramas = lecturaAcumulada.split("\n")
                        for (i in 0 until tramas.size - 1) {
                            val tramaLimpia = tramas[i].trim()
                            if (tramaLimpia.isNotEmpty()) {
                                procesarTramaUI(tramaLimpia)
                            }
                        }
                        lecturaAcumulada = tramas.last()
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                break
            }
        }
    }

    private fun procesarTramaUI(trama: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (trama.startsWith("DATA,")) {
                val partes = trama.split(",")
                if (partes.size >= 3) {
                    val temperatura = partes[1]
                    val humedad = partes[2]
                    
                    tvTemp.text = "$temperatura °C\nTemp"
                    tvHum.text = "$humedad %\nHumedad"

                    val tempFloat = temperatura.toFloatOrNull()
                    if (tempFloat != null) {
                        entradasTermicas.add(Entry(tiempoX, tempFloat))
                        tiempoX += 1f
                        
                        val dataSet = LineDataSet(entradasTermicas, "Temperatura Cuarto")
                        dataSet.color = android.graphics.Color.RED
                        dataSet.valueTextColor = android.graphics.Color.BLACK
                        
                        graficoTemp.data = LineData(dataSet)
                        graficoTemp.notifyDataSetChanged()
                        graficoTemp.invalidate()
                    }
                }
            } else {
                agregarConsola(trama)
            }
        }
    }

    private fun enviarComando(comando: String) {
        if (isConnected && outStream != null) {
            try {
                outStream?.write(comando.toByteArray())
                agregarConsola(">> Enviado: ${comando.trim()}")
            } catch (e: Exception) {
                agregarConsola("Error al enviar comando.")
            }
        } else {
            Toast.makeText(this, "Dispositivo no conectado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun agregarConsola(texto: String) {
        val previo = tvConsola.text.toString()
        val historial = if (previo.length > 1000) previo.substring(0, 1000) else previo
        tvConsola.text = "- $texto\n$historial"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            btSocket?.close()
        } catch (e: Exception) { }
    }
}
