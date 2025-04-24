package com.example.voicetotexttest

import kotlinx.coroutines.delay
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import java.io.IOException
import java.util.Arrays
import com.google.longrunning.GetOperationRequest
import kotlinx.coroutines.withContext
import com.google.longrunning.OperationsGrpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive


private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 112
private const val API_KEY = "" // Sostituisci con la tua chiave API

class MainActivity : AppCompatActivity() {

    private lateinit var transcriptionTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var mainChannel: io.grpc.ManagedChannel
    private var audioUri: Uri? = null
    private var currentOperationName: String? = null
    private var permissionToWriteAccepted = false
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var writePermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    //private var mainChannel: io.grpc.ManagedChannel? = null

    override fun onStart() {
        super.onStart()
        Log.i("Permissions", "onStart chiamato.")
        transcriptionTextView.append("\nonStart chiamato.")
        // In questo approccio, la richiesta di archiviazione è in onCreate
        // Potremmo comunque fare qui controlli o ulteriori azioni legate al permesso
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transcriptionTextView = findViewById(R.id.transcriptionTextView)
        recordButton = findViewById(R.id.recordButton)
        transcriptionTextView.textSize = 14f
        transcriptionTextView.text = "Applicazione avviata."

        recordButton.setOnClickListener {
            Log.i("MainActivity", "Button record premuto (funzionalità locale disabilitata).")
            transcriptionTextView.append("\nButton record premuto (funzionalità locale disabilitata).")
        }

        handleSharedIntent()
        Log.i("MainActivity", "onCreate completato.")
        transcriptionTextView.append("\nonCreate completato.")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionToWriteAccepted = true
                    Log.i("Permissions", "onRequestPermissionsResult (Storage): Permesso di archiviazione concesso.")
                    transcriptionTextView.append("\nonRequestPermissionsResult (Storage): Permesso di archiviazione concesso.")
                    // Se il permesso è concesso ORA, e avevamo un URI condiviso, riprendi la trascrizione
                    intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                        Log.i("Permissions", "Rilevato URI in onRequestPermissionsResult, riprendo la trascrizione per: $uri")
                        transcriptionTextView.append("\nLog: Rilevato URI, riprendo la trascrizione.")
                        transcribeAudioFile(uri)
                    }
                } else {
                    permissionToWriteAccepted = false
                    Log.w("Permissions", "onRequestPermissionsResult (Storage): Permesso di archiviazione negato.");
                    transcriptionTextView.append("\nonRequestPermissionsResult (Storage): Permesso di archiviazione negato.");
                    // Potresti voler informare l'utente che la condivisione audio potrebbe non funzionare
                }
            }
            // Abbiamo rimosso la gestione del permesso audio per questo test
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Se hai mantenuto un riferimento al canale principale, chiudilo qui.
        // Esempio: mainChannel?.shutdownNow()?.awaitTermination(5, TimeUnit.SECONDS)
        Log.i("MainActivity", "onDestroy chiamato.")
        transcriptionTextView.append("\nonDestroy chiamato.")
    }

    private fun handleSharedIntent() {
        val intent = intent
        val action = intent?.action
        val type = intent?.type

        Log.i("IntentHandling", "handleSharedIntent() chiamato. Action: $action, Type: $type")

        if (action == Intent.ACTION_SEND) {
            if (type?.startsWith("audio/") == true) {
                val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (audioUri != null) {
                    Log.i("IntentHandling", "File audio condiviso rilevato. URI: $audioUri.")
                    Log.i("Transcription", "Avvio trascrizione lunga con Google Cloud per URI condiviso: $audioUri.")
                    // Inizializza mainChannel qui se non è già stato fatto
                    if (!::mainChannel.isInitialized) {
                        mainChannel = ManagedChannelBuilder.forAddress("speech.googleapis.com", 443)
                            .useTransportSecurity()
                            .build()
                        Log.i("GrpcChannel", "mainChannel inizializzato in handleSharedIntent.")
                    }
                    transcribeAudioFile(audioUri)
                }
            } else {
                Log.i("IntentHandling", "Tipo di intent non supportato: $type")
                transcriptionTextView.append("\nLog: Tipo di intent non supportato: $type")
            }
        } else if (action == Intent.ACTION_MAIN && type == null) {
            Log.i("IntentHandling", "Azione dell'intent non gestita: $action")
            transcriptionTextView.append("\nLog: Azione dell'intent non gestita: $action")
        }
    }
    private fun transcribeAudioFile(uri: Uri) {
        Log.i("Transcription", "transcribeAudioFile chiamato con URI (condiviso): $uri.")
        transcriptionTextView.append("\ntranscribeAudioFile chiamato con URI (condiviso): $uri.")
        lifecycleScope.launch(Dispatchers.IO) { // Esegui l'operazione di I/O in un dispatcher appropriato
            try {
                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.use {
                    val audioData = it.readBytes()
                    Log.i("AudioInfo", "Dimensione dei byte audio letti dal flusso condiviso: ${audioData.size}")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Dimensione dei byte audio letti dal flusso condiviso: ${audioData.size}")
                    }

                    val mimeType = contentResolver.getType(uri)
                    Log.i("AudioInfo", "MIME Type del file condiviso: $mimeType")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: MIME Type del file condiviso: $mimeType")
                    }
                    val fileExtension = uri.path?.substringAfterLast(".")?.toLowerCase(Locale.ROOT)
                    Log.i("AudioInfo", "Estensione del file (dedotta dall'URI): $fileExtension")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Estensione del file (dedotta dall'URI): $fileExtension")
                    }

                    val encoding = getEncodingFromMimeType(mimeType, fileExtension)
                    val sampleRateHertz = getSampleRateFromMimeType(mimeType, fileExtension)
                    Log.i("AudioInfo", "Encoding dedotto: $encoding")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Encoding dedotto: $encoding")
                    }
                    Log.i("AudioInfo", "Sample Rate dedotto: $sampleRateHertz")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Sample Rate dedotto: $sampleRateHertz")
                    }

                    Log.i("GrpcChannel", "Stato di mainChannel prima della creazione dello stub: $mainChannel")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Stato di mainChannel prima della creazione dello stub: $mainChannel")
                    }
                    if (!::mainChannel.isInitialized) {
                        mainChannel = ManagedChannelBuilder.forAddress("speech.googleapis.com", 443)
                            .useTransportSecurity()
                            .build()
                        Log.w("GrpcChannel", "Warning: mainChannel non era inizializzato, inizializzato ora in transcribeAudioFile.")
                        launch(Dispatchers.Main) {
                            transcriptionTextView.append("\nLog: Warning: mainChannel non era inizializzato, inizializzato ora in transcribeAudioFile.")
                        }
                    }

                    val stub = SpeechGrpc.newBlockingStub(mainChannel)
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
                                launch(Dispatchers.Main) {
                                    transcriptionTextView.append("\nLog: Credenziali API applicate alla metadata.")
                                }
                            }

                            override fun thisUsesUnstableApi() {}
                        })
                    Log.i("GrpcStub", "transcribeAudioFile: Stub gRPC creato.")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Stub gRPC creato.")
                    }

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
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: RecognitionConfig creata: $config")
                    }

                    val audio = RecognitionAudio.newBuilder()
                        .setContent(ByteString.copyFrom(audioData))
                        .build()
                    Log.i("AudioRequest", "transcribeAudioFile: RecognitionAudio creata.")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: RecognitionAudio creata.")
                    }

                    val request = LongRunningRecognizeRequest.newBuilder()
                        .setConfig(config)
                        .setAudio(audio)
                        .build()
                    Log.i("SpeechRequest", "LongRunningRecognizeRequest creata: $request")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: LongRunningRecognizeRequest creata: $request")
                    }

                    Log.i("SpeechApiCall", "Chiamata a stub.longRunningRecognize(request)")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Chiamata a stub.longRunningRecognize(request)...")
                    }
                    val operation = stub.longRunningRecognize(request)

                    Log.i("SpeechOperation", "Oggetto Operazione ricevuto: ${operation.name}")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Oggetto Operazione ricevuto: ${operation.name}")
                    }

                    if (!operation.name.isNullOrEmpty()) {
                        currentOperationName = operation.name
                        startPollingOperationStatus(operation.name, mainChannel)
                    } else {
                        Log.e("SpeechOperation", "Errore: Nome dell'operazione non valido.")
                        launch(Dispatchers.Main) {
                            transcriptionTextView.append("\nErrore: Nome dell'operazione non valido.")
                        }
                    }
                } ?: run {
                    Log.e("LongRunningRecognize", "Errore: Impossibile aprire InputStream per URI condiviso: $uri.")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nErrore: Impossibile aprire InputStream per URI condiviso: $uri.")
                    }
                }
            } catch (e: IOException) {
                Log.e("LongRunningRecognize", "Errore durante la lettura del file audio condiviso: ${e.localizedMessage}", e)
                launch(Dispatchers.Main) {
                    transcriptionTextView.append("\nErrore durante la lettura del file audio condiviso: ${e.localizedMessage}")
                }
            } finally {
                Log.i("Transcription", "transcribeAudioFile completato.")
                launch(Dispatchers.Main) {
                    transcriptionTextView.append("\ntranscribeAudioFile completato.")
                }
            }
        }
        Log.i("Transcription", "transcribeAudioFile completato (fuori coroutine).")
        transcriptionTextView.append("\ntranscribeAudioFile completato (fuori coroutine).")
    }

    private fun getEncodingFromMimeType(mimeType: String?, fileExtension: String?): AudioEncoding {
        Log.i("AudioEncoding", "Determinazione encoding per MIME Type: $mimeType, Estensione (iniziale): $fileExtension")
        val extension = fileExtension?.lowercase(Locale.ROOT) ?: audioUri?.path?.substringAfterLast(".")?.lowercase(Locale.ROOT)
        Log.i("AudioEncoding", "Estensione da usare: $extension")
        return when {
            extension == "ogg" || extension == "opus" || mimeType?.contains("ogg") == true -> AudioEncoding.OGG_OPUS
            extension == "m4a" || mimeType?.contains("amr-wb") == true || mimeType?.contains("x-m4a") == true -> AudioEncoding.AMR_WB
            extension == "wav" || mimeType == "audio/wav" -> AudioEncoding.LINEAR16
            else -> {
                Log.w("AudioEncoding", "Encoding non riconosciuto ($mimeType, $extension), usando WEBM_OPUS come fallback.")
                AudioEncoding.WEBM_OPUS
            }
        }.also { encoding ->
            Log.i("AudioEncoding", "Encoding dedotto = $encoding")
        }
    }

    private fun getSampleRateFromMimeType(mimeType: String?, fileExtension: String?): Int {
        Log.i("AudioSampleRate", "Determinazione sample rate per MIME Type: $mimeType, Estensione (iniziale): $fileExtension")
        val extension = fileExtension?.lowercase(Locale.ROOT) ?: audioUri?.path?.substringAfterLast(".")?.lowercase(Locale.ROOT)
        Log.i("AudioSampleRate", "Estensione da usare: $extension")
        return when {
            extension == "wav" || mimeType == "audio/wav" -> 16000
            else -> {
                Log.w("AudioSampleRate", "Sample rate non riconosciuto ($mimeType, $extension), usando 16000 Hz come fallback.")
                16000
            }
        }.also { sampleRate ->
            Log.i("AudioSampleRate", "Sample rate dedotto = $sampleRate Hz")
        }
    }
    private var isOperationFinished = false

    private suspend fun getOperationStatus(operationName: String, scope: CoroutineScope, sharedChannel: io.grpc.ManagedChannel?) {
        Log.i("SpeechOperation", "getOperationStatus chiamato per: $operationName")
        withContext(Dispatchers.Main) {
            transcriptionTextView.append("\nLog: getOperationStatus chiamato per: $operationName...")
        }
        var isOperationFinished = false
        var retryCount = 0
        val maxRetries = 5

        val operationsStub = OperationsGrpc.newBlockingStub(sharedChannel ?: mainChannel!!) // Usa il canale passato (che ora è il principale)
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
                    Log.i("GrpcCredentials-Op", "Credenziali API applicate (Operation).")
                    // Esegui l'aggiornamento dell'UI all'interno di una coroutine
                    scope.launch(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Credenziali API applicate (Operation).")
                    }
                }

                override fun thisUsesUnstableApi() {}
            })

        while (!isOperationFinished && retryCount < maxRetries && scope.isActive) {
            try {
                val request = GetOperationRequest.newBuilder()
                    .setName(operationName)
                    .build()
                val operationResponse = operationsStub.getOperation(request)

                if (operationResponse.done) {
                    isOperationFinished = true
                    if (operationResponse.hasError()) {
                        val error = operationResponse.error
                        Log.e("SpeechOperation", "Errore durante l'operazione ($operationName): ${error.message}")
                        withContext(Dispatchers.Main) {
                            transcriptionTextView.append("\nErrore durante la trascrizione: ${error.message}")
                        }
                    } else {
                        Log.i("OperationResponse", "Tipo di Any nella risposta: ${operationResponse.response.typeUrl}") // Aggiunta del log
                        try {
                            val transcribeResponse = operationResponse.response.unpack(LongRunningRecognizeResponse::class.java)
                            transcribeResponse.resultsList.forEach { result ->
                                result.alternativesList.forEach { alternative ->
                                    val risultatoDellaTrascrizione = alternative.transcript
                                    Log.i("TranscriptionResult", "Trascrizione finale ($operationName): $risultatoDellaTrascrizione")
                                    withContext(Dispatchers.Main) {
                                        transcriptionTextView.append("\nTrascrizione: $risultatoDellaTrascrizione")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SpeechOperation", "Errore durante l'unpacking della risposta: ${e.localizedMessage}", e)
                            withContext(Dispatchers.Main) {
                                transcriptionTextView.append("\nErrore durante l'unpacking della risposta: ${e.localizedMessage}")
                            }
                        }
                    }
                } else {
                    Log.i("SpeechOperation", "Operazione ($operationName) non ancora completata. In attesa di 2 secondi (tentativo ${retryCount + 1}).")
                    withContext(Dispatchers.Main) {
                        transcriptionTextView.append("\nLog: Operazione non completata, attendo...")
                    }
                    delay(2000)
                    retryCount++
                }
            } catch (e: Exception) {
                Log.e("SpeechOperation", "Errore durante il polling dell'operazione ($operationName): ${e.localizedMessage}. Tentativo ${retryCount + 1}", e)
                withContext(Dispatchers.Main) {
                    transcriptionTextView.append("\nErrore durante il polling: ${e.localizedMessage} (Tentativo ${retryCount + 1})")
                }
                delay(2000)
                retryCount++
            }
        }

        if (!isOperationFinished) {
            Log.w("SpeechOperation", "Timeout o numero massimo di tentativi raggiunto per l'operazione: $operationName")
            withContext(Dispatchers.Main) {
                transcriptionTextView.append("\nErrore: Timeout o troppi tentativi per la trascrizione.")
            }
        }
    }

    private fun startPollingOperationStatus(operationName: String, sharedChannel: io.grpc.ManagedChannel?) {
        lifecycleScope.launch(Dispatchers.IO) {
            getOperationStatus(operationName, this, sharedChannel) // Passa il canale condiviso
        }
    }
}