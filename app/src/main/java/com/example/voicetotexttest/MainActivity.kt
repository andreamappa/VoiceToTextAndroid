package com.example.voicetotexttest

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding
import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.SpeechGrpc
import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import io.grpc.CallCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.cloud.speech.v1.LongRunningRecognizeRequest
import com.google.cloud.speech.v1.LongRunningRecognizeResponse
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import android.util.Log
import com.google.longrunning.Operation // Importa la classe Operation
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import io.grpc.Metadata

private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 112
private const val API_KEY = "" // Sostituisci con la tua chiave API
private const val LOG_FILE_NAME = "voice_to_text_log.txt"
private const val LOG_SUBDIRECTORY = "VoiceToTextLogs" // Sottodirectory per i log

class MainActivity : AppCompatActivity() {

    private lateinit var transcriptionTextView: TextView
    private lateinit var recordButton: Button
    private var audioUri: Uri? = null
    private var permissionToWriteAccepted = false
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var writePermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transcriptionTextView = findViewById(R.id.transcriptionTextView)
        recordButton = findViewById(R.id.recordButton)
        transcriptionTextView.textSize = 14f
        transcriptionTextView.text = "Applicazione avviata."

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        ActivityCompat.requestPermissions(this, writePermission, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION)

        recordButton.setOnClickListener {
            logToFile("Button record premuto (funzionalità locale disabilitata).")
            transcriptionTextView.append("\nButton record premuto (funzionalità locale disabilitata).")
        }

        handleSharedIntent()
        logToFile("onCreate completato.")
        transcriptionTextView.append("\nonCreate completato.")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                permissionToRecordAccepted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                logToFile("onRequestPermissionsResult (Audio): $requestCode, ${grantResults.contentToString()}. Permesso accettato: $permissionToRecordAccepted.")
                transcriptionTextView.append("\nonRequestPermissionsResult (Audio): $requestCode, ${grantResults.contentToString()}. Permesso accettato: $permissionToRecordAccepted.")
                if (!permissionToRecordAccepted) finish()
            }
            REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION -> {
                permissionToWriteAccepted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                logToFile("onRequestPermissionsResult (Storage): $requestCode, ${grantResults.contentToString()}. Permesso accettato: $permissionToWriteAccepted.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logToFile("onDestroy chiamato.")
        transcriptionTextView.append("\nonDestroy chiamato.")
    }

    private fun handleSharedIntent() {
        val intent = getIntent()
        val action = intent.action
        val type = intent.type
        logToFile("handleSharedIntent: action=$action, type=$type, intent=$intent.")
        transcriptionTextView.append("\nhandleSharedIntent: action=$action, type=$type, intent=$intent.")

        if (Intent.ACTION_SEND == action && type?.startsWith("audio/") == true) {
            audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            logToFile("File audio condiviso rilevato. URI: $audioUri.")
            transcriptionTextView.append("\nFile audio condiviso rilevato. URI: $audioUri.")
            if (audioUri != null) {
                logToFile("Avvio trascrizione lunga con Google Cloud per: $audioUri.")
                transcriptionTextView.append("\nAvvio trascrizione lunga con Google Cloud per: $audioUri.")
                transcribeAudioFile(audioUri!!)
            } else {
                logToFile("Errore: nessun file audio allegato nell'intent condiviso.")
                transcriptionTextView.append("\nErrore: nessun file audio allegato nell'intent condiviso.")
            }
        } else {
            logToFile("Nessun file audio condiviso rilevato.")
            transcriptionTextView.append("\nNessun file audio condiviso rilevato.")
        }
        logToFile("handleSharedIntent completato.")
        transcriptionTextView.append("\nhandleSharedIntent completato.")
    }

    private fun transcribeAudioFile(uri: Uri) {
        logToFile("transcribeAudioFile chiamato con URI: $uri.")
        transcriptionTextView.append("\ntranscribeAudioFile chiamato con URI: $uri.")

        lifecycleScope.launch(Dispatchers.Main) {
            logToFile("lifecycleScope.launch (Dispatchers.Main) iniziato.")
            transcriptionTextView.append("\nlifecycleScope.launch (Dispatchers.Main) iniziato.")
            try {
                logToFile("Tentativo di aprire InputStream per URI: $uri.")
                transcriptionTextView.append("\nTentativo di aprire InputStream per URI: $uri.")
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    launch(Dispatchers.Main) {
                        logToFile("Errore: Impossibile aprire InputStream per URI: $uri.")
                        transcriptionTextView.append("\nErrore: Impossibile aprire InputStream per URI: $uri.")
                        Log.e("LongRunningRecognize", "Errore: InputStream nullo per l'URI: $uri")
                    }
                    return@launch
                }

                inputStream.use {
                    val audioData = it.readBytes()
                    Log.i("AudioInfo", "Dimensione dei byte audio letti: ${audioData.size}")
                    logToFile("Dimensione dei byte audio letti: ${audioData.size}")
                    transcriptionTextView.append("\nLog: Dimensione dei byte audio letti: ${audioData.size}")

                    val mimeType = contentResolver.getType(uri)
                    Log.i("AudioInfo", "MIME Type del file condiviso: $mimeType")
                    logToFile("MIME Type del file condiviso: $mimeType")
                    transcriptionTextView.append("\nLog: MIME Type del file condiviso: $mimeType")
                    val fileExtension = uri.path?.substringAfterLast(".")?.toLowerCase(Locale.ROOT)
                    Log.i("AudioInfo", "Estensione del file (dedotta dall'URI): $fileExtension")
                    logToFile("Estensione del file (dedotta dall'URI): $fileExtension")
                    transcriptionTextView.append("\nLog: Estensione del file (dedotta dall'URI): $fileExtension")

                    val encoding = getEncodingFromMimeType(mimeType, fileExtension)
                    val sampleRateHertz = getSampleRateFromMimeType(mimeType, fileExtension)
                    Log.i("AudioInfo", "Encoding dedotto: $encoding")
                    logToFile("Encoding dedotto: $encoding")
                    transcriptionTextView.append("\nLog: Encoding dedotto: $encoding")
                    Log.i("AudioInfo", "Sample Rate dedotto: $sampleRateHertz")
                    logToFile("Sample Rate dedotto: $sampleRateHertz")
                    transcriptionTextView.append("\nLog: Sample Rate dedotto: $sampleRateHertz")

                    val channel = ManagedChannelBuilder.forAddress("speech.googleapis.com", 443)
                        .useTransportSecurity()
                        .build()
                    logToFile("Canale gRPC creato.")
                    transcriptionTextView.append("\nLog: Canale gRPC creato.")

                    val stub = SpeechGrpc.newBlockingStub(channel)
                        .withCallCredentials(object : CallCredentials() {
                            override fun applyRequestMetadata(
                                requestInfo: CallCredentials.RequestInfo,
                                appExecutor: java.util.concurrent.Executor,
                                applier: CallCredentials.MetadataApplier
                            ) {
                                val metadata = io.grpc.Metadata()
                                val apiKeyMetadataKey = io.grpc.Metadata.Key.of(
                                    "x-goog-api-key",
                                    io.grpc.Metadata.ASCII_STRING_MARSHALLER
                                )
                                metadata.put(apiKeyMetadataKey, API_KEY)
                                applier.apply(metadata)
                                Log.i("GrpcCredentials", "Credenziali API applicate alla metadata.")
                                logToFile("Credenziali API applicate alla metadata.")
                                transcriptionTextView.append("\nLog: Credenziali API applicate alla metadata.")
                            }

                            override fun thisUsesUnstableApi() {}
                        })
                    logToFile("Stub gRPC creato.")
                    transcriptionTextView.append("\nLog: Stub gRPC creato.")

                    val config = RecognitionConfig.newBuilder()
                        .setEncoding(encoding)
                        .setSampleRateHertz(sampleRateHertz)
                        .setLanguageCode("it-IT")
                        .setModel("default")
                        .setEnableAutomaticPunctuation(true)
                        .setAudioChannelCount(1)
                        .setUseEnhanced(true)
                        .build()
                    Log.i("SpeechConfig", "RecognitionConfig creata: $config")
                    logToFile("RecognitionConfig creata: $config")
                    transcriptionTextView.append("\nLog: RecognitionConfig creata: $config")

                    val audio = RecognitionAudio.newBuilder()
                        .setContent(ByteString.copyFrom(audioData))
                        .build()
                    logToFile("RecognitionAudio creata.")
                    transcriptionTextView.append("\nLog: RecognitionAudio creata.")

                    val request = LongRunningRecognizeRequest.newBuilder()
                        .setConfig(config)
                        .setAudio(audio)
                        .build()
                    Log.i("SpeechRequest", "LongRunningRecognizeRequest creata: $request")
                    logToFile("LongRunningRecognizeRequest creata: $request")
                    transcriptionTextView.append("\nLog: LongRunningRecognizeRequest creata: $request")

                    val operationFuture: Deferred<Operation> = async(Dispatchers.IO) {
                        Log.i("SpeechApiCall", "Chiamata a stub.longRunningRecognize(request)")
                        logToFile("Chiamata a stub.longRunningRecognize(request)...")
                        transcriptionTextView.append("\nLog: Chiamata a stub.longRunningRecognize(request)...")
                        stub.longRunningRecognize(request)
                    }

                    try {
                        Log.i("SpeechOperation", "In attesa del risultato dell'operazione...")
                        logToFile("In attesa del risultato dell'operazione...")
                        transcriptionTextView.append("\nLog: In attesa del risultato dell'operazione...")
                        val operation = operationFuture.await()
                        Log.i("SpeechOperation", "Risultato dell'operazione ottenuto: $operation")
                        logToFile("Risultato dell'operazione ottenuto: $operation")
                        transcriptionTextView.append("\nLog: Risultato dell'operazione ottenuto: $operation")

                        if (operation.hasResponse()) {
                            Log.i("SpeechResponse", "L'operazione ha una risposta.")
                            logToFile("L'operazione ha una risposta.")
                            transcriptionTextView.append("\nLog: L'operazione ha una risposta.")
                            try {
                                val response = operation.response.unpack(LongRunningRecognizeResponse::class.java)
                                Log.i("SpeechResponse", "Risposta unboxed: $response")
                                logToFile("Risposta unboxed: $response")
                                transcriptionTextView.append("\nLog: Risposta unboxed: $response")
                                if (response.resultsList.isNotEmpty()) {
                                    val transcript = response.resultsList[0].alternativesList[0].transcript
                                    logToFile("Trascrizione (Google Cloud - Lunga Durata): $transcript")
                                    transcriptionTextView.append("\nTrascrizione (Google Cloud - Lunga Durata): $transcript")
                                } else {
                                    logToFile("Nessuna trascrizione trovata nella Risposta.")
                                    transcriptionTextView.append("\nLog: Nessuna trascrizione trovata nella Risposta.")
                                }
                            } catch (unpackingException: Exception) {
                                Log.e("SpeechResponse", "Errore durante l'unpack della Risposta: ${unpackingException.localizedMessage}", unpackingException)
                                logToFile("Errore durante l'unpack della Risposta: ${unpackingException.localizedMessage}")
                                transcriptionTextView.append("\nErrore durante l'unpack della Risposta: <span class="math-inline">\{unpackingException\.localizedMessage\}"\)
                                \}
                            \} else if \(operation\.hasError\(\)\) \{
                            val error \= operation\.error
                            Log\.e\("SpeechOperation", "L'operazione ha un errore\: \(</span>{error.code}) <span class="math-inline">\{error\.message\}"\)
                            logToFile\("Errore durante la trascrizione\: \(</span>{error.code}) <span class="math-inline">\{error\.message\}"\)
                            transcriptionTextView\.append\("\\nErrore durante la trascrizione\: \(</span>{error.code}) ${error.message}")
                        } else {
                            Log.w("SpeechOperation", "Operazione a lunga durata completata senza risposta o errore.")
                            logToFile("Operazione a lunga durata completata senza risposta o errore.")
                            transcriptionTextView.append("\nLog: Operazione a lunga durata completata senza risposta o errore.")
                        }
                    } catch (e: Exception) {
                        Log.e("SpeechOperation", "Errore durante l'attesa della trascrizione: ${e.localizedMessage}", e)
                        logToFile("Errore durante l'attesa della trascrizione: ${e.localizedMessage}")
                        transcriptionTextView.append("\nErrore durante l'attesa della trascrizione: ${e.localizedMessage}")
                    } finally {
                        channel?.shutdownNow()?.awaitTermination(5, TimeUnit.SECONDS)
                        Log.i("GrpcChannel", "Canale gRPC chiuso.")
                        logToFile("Canale gRPC chiuso.")
                        transcriptionTextView.append("\nLog: Canale gRPC chiuso.")
                    }
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    logToFile("Errore durante la trascrizione lunga con Google Cloud: ${e.localizedMessage}")
                    transcriptionTextView.append("\nErrore durante la trascrizione lunga con Google Cloud: ${e.localizedMessage}")
                    Log.e("LongRunningRecognize", "Errore durante la trascrizione lunga (try esterno): ${e.localizedMessage}", e)
                }
            }
            logToFile("lifecycleScope.launch (Dispatchers.Main) completato.")
            transcriptionTextView.append("\nlifecycleScope.launch (Dispatchers.Main) completato.")
        }
        logToFile("transcribeAudioFile completato.")
        transcriptionTextView.append("\ntranscribeAudioFile completato.")
    }

    private fun getEncodingFromMimeType(mimeType: String?, fileExtension: String?): AudioEncoding {
        Log.i("AudioEncoding", "Determinazione encoding per MIME Type: $mimeType, Estensione: $fileExtension")
        return when {
            fileExtension == "ogg" || fileExtension == "opus" || mimeType?.contains
                    fileExtension == "wav" || mimeType == "audio/wav" -> 16000 // Valore tipico per WAV
            else -> {
                Log.w("AudioSampleRate", "Sample rate non riconosciuto ($mimeType, $fileExtension), usando 16000 Hz come fallback.")
                16000
            }
        }
    }

    private fun logToFile(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "$timestamp - $message\n"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ utilizza MediaStore
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + File.separator + LOG_SUBDIRECTORY)
            }

            var uri: Uri? = null
            var outputStream: OutputStream? = null
            try {
                uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                if (uri != null) {
                    outputStream = contentResolver.openOutputStream(uri)
                    outputStream?.write(logMessage.toByteArray())
                } else {
                    fallbackLogToFile("Errore durante l'inserimento del file in MediaStore.")
                }
            } catch (e: IOException) {
                fallbackLogToFile("Errore IOException durante la scrittura su MediaStore: ${e.localizedMessage}")
            } finally {
                outputStream?.close()
            }
        } else {
            // Versioni precedenti utilizzano il metodo tradizionale su file
            val directory = File(Environment.getExternalStorageDirectory(), LOG_SUBDIRECTORY)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val logFile = File(directory, LOG_FILE_NAME)

            try {
                FileOutputStream(logFile, true).use { it.write(logMessage.toByteArray()) }
            } catch (e: IOException) {
                Log.e("FileLogging", "Errore durante la scrittura nel file di log: ${e.localizedMessage}")
                transcriptionTextView.append("\nErrore durante la scrittura nel log (fallback): ${e.localizedMessage}")
            }
        }
        Log.d("FileLogging", logMessage.trim()) // Continua a loggare anche su Logcat per debug
    }

    private fun fallbackLogToFile(errorMessage: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "$timestamp - ERRORE: $errorMessage\n"
        Log.e("FileLoggingFallback", logMessage.trim())
        transcriptionTextView.append("\nLog (Fallback): $errorMessage")

        val directory = File(Environment.getExternalStorageDirectory(), LOG_SUBDIRECTORY)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val logFile = File(directory, LOG_FILE_NAME)

        try {
            FileOutputStream(logFile, true).use { it.write(logMessage.toByteArray()) }
        } catch (e: IOException) {
            Log.e("FileLoggingFallback", "Errore FATALE durante la scrittura nel file di log (fallback): ${e.localizedMessage}")
            transcriptionTextView.append("\nERRORE FATALE durante la scrittura nel log (fallback): ${e.localizedMessage}")
        }
    }
}
