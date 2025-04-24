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
import android.widget.ProgressBar
import android.view.View


//private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 112
private const val REQUEST_AUDIO_PICK = 101 // Puoi usare 100 se non è già definito
private const val API_KEY = BuildConfig.SPEECH_TO_TEXT_API_KEY

class MainActivity : AppCompatActivity() {

    private lateinit var transcriptionTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var mainChannel: io.grpc.ManagedChannel
    private var audioUri: Uri? = null
    private var currentOperationName: String? = null
    private var permissionToWriteAccepted = false
//    private var permissionToRecordAccepted = false
//    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var writePermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    //private var mainChannel: io.grpc.ManagedChannel? = null

    override fun onStart() {
        super.onStart()
        Log.i("Permissions", "onStart chiamato.")
        // In questo approccio, la richiesta di archiviazione è in onCreate
        // Potremmo comunque fare qui controlli o ulteriori azioni legate al permesso
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transcriptionTextView = findViewById(R.id.transcriptionTextView)
        transcriptionTextView.text = "Condividi un messaggio audio da WhatsApp per vederlo trascritto."

        recordButton = findViewById(R.id.selectAudioButton) // Cambia l'ID per corrispondere al nuovo layout
        recordButton.text = "Seleziona File Audio"
        recordButton.setOnClickListener {
            openAudioChooser()
        }

        progressBar = findViewById(R.id.progressBar) // Ottieni il riferimento alla ProgressBar

        handleSharedIntent()
        Log.i("MainActivity", "onCreate completato.")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUDIO_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                Log.i("AudioSelection", "File audio selezionato: $uri")
                transcribeAudioFile(uri)
            }
        }
    }

    private fun openAudioChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_AUDIO_PICK)
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
                // Abbiamo deciso di non mostrare questo log nell'UI
                // transcriptionTextView.append("\nLog: Tipo di intent non supportato: $type")
            }
        } else if (action == Intent.ACTION_MAIN && type == null) {
            Log.i("IntentHandling", "Azione dell'intent non gestita: $action")
            // Abbiamo deciso di non mostrare questo log nell'UI
            // transcriptionTextView.append("\nLog: Azione dell'intent non gestita: $action")
        }
    }

    private fun transcribeAudioFile(uri: Uri) {
        Log.i("Transcription", "transcribeAudioFile chiamato con URI: $uri")
        transcriptionTextView.text = "Elaborazione in corso..."
        progressBar.visibility = View.VISIBLE // Mostra la ProgressBar

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val audioData = inputStream.readBytes()
                    Log.i("AudioInfo", "Dimensione dei byte audio letti: ${audioData.size}")

                    val mimeType = contentResolver.getType(uri)
                    Log.i("AudioInfo", "MIME Type: $mimeType")
                    val fileExtension = uri.path?.substringAfterLast(".")?.toLowerCase(Locale.ROOT)
                    Log.i("AudioInfo", "Estensione del file: $fileExtension")

                    val encoding = getEncodingFromMimeType(mimeType, fileExtension)
                    val sampleRateHertz = getSampleRateFromMimeType(mimeType, fileExtension)
                    Log.i("AudioInfo", "Encoding dedotto: $encoding")
                    Log.i("AudioInfo", "Sample Rate dedotto: $sampleRateHertz")

                    // Inizializza mainChannel se non è già stata inizializzata
                    if (!::mainChannel.isInitialized) {
                        mainChannel = ManagedChannelBuilder.forAddress("speech.googleapis.com", 443)
                            .useTransportSecurity()
                            .build()
                        Log.w("GrpcChannel", "Warning: mainChannel non inizializzato, creato in transcribeAudioFile.")
                    }

                    val stub = SpeechGrpc.newBlockingStub(mainChannel)
                        .withCallCredentials(object : CallCredentials() {
                            override fun applyRequestMetadata(
                                requestInfo: CallCredentials.RequestInfo,
                                appExecutor: java.util.concurrent.Executor,
                                applier: CallCredentials.MetadataApplier
                            ) {
                                val metadata = io.grpc.Metadata()
                                metadata.put(
                                    io.grpc.Metadata.Key.of("x-goog-api-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER),
                                    API_KEY
                                )
                                applier.apply(metadata)
                                Log.i("GrpcCredentials", "Credenziali API applicate.")
                            }

                            override fun thisUsesUnstableApi() {}
                        })
                    Log.i("GrpcStub", "Stub gRPC creato.")

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

                    val audio = RecognitionAudio.newBuilder()
                        .setContent(ByteString.copyFrom(audioData))
                        .build()
                    Log.i("AudioRequest", "RecognitionAudio creata.")

                    val request = LongRunningRecognizeRequest.newBuilder()
                        .setConfig(config)
                        .setAudio(audio)
                        .build()
                    Log.i("SpeechRequest", "LongRunningRecognizeRequest creata.")

                    Log.i("SpeechApiCall", "Chiamata a stub.longRunningRecognize(request)")
                    val operation = stub.longRunningRecognize(request)

                    Log.i("SpeechOperation", "Oggetto Operazione ricevuto: ${operation.name}")

                    if (!operation.name.isNullOrEmpty()) {
                        currentOperationName = operation.name
                        startPollingOperationStatus(operation.name, mainChannel)
                    } else {
                        Log.e("SpeechOperation", "Errore: Nome dell'operazione non valido.")
                        launch(Dispatchers.Main) {
                            transcriptionTextView.text = "Errore durante l'avvio della trascrizione."
                            progressBar.visibility = View.GONE // Nascondi la ProgressBar in caso di errore
                        }
                    }
                } ?: run {
                    Log.e("LongRunningRecognize", "Errore: Impossibile aprire InputStream per URI: $uri")
                    launch(Dispatchers.Main) {
                        transcriptionTextView.text = "Errore nella lettura del file audio."
                        progressBar.visibility = View.GONE // Nascondi la ProgressBar in caso di errore
                    }
                }
            } catch (e: IOException) {
                Log.e("LongRunningRecognize", "Errore durante la lettura del file audio: ${e.localizedMessage}", e)
                launch(Dispatchers.Main) {
                    transcriptionTextView.text = "Errore durante la lettura del file audio: ${e.localizedMessage}"
                    progressBar.visibility = View.GONE // Nascondi la ProgressBar in caso di errore
                }
            } finally {
                Log.i("Transcription", "transcribeAudioFile completato (fuori coroutine).")
            }
        }
        Log.i("Transcription", "transcribeAudioFile completato (fuori coroutine).")
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
        Log.i("SpeechOperation", "Avviato polling per l'operazione: $operationName")
        var isOperationFinished = false
        var retryCount = 0
        val maxRetries = 30

        val operationsStub = OperationsGrpc.newBlockingStub(sharedChannel ?: mainChannel!!)
            .withCallCredentials(object : CallCredentials() {
                override fun applyRequestMetadata(
                    requestInfo: CallCredentials.RequestInfo,
                    appExecutor: java.util.concurrent.Executor,
                    applier: CallCredentials.MetadataApplier
                ) {
                    val metadata = io.grpc.Metadata()
                    metadata.put(
                        io.grpc.Metadata.Key.of("x-goog-api-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER),
                        API_KEY
                    )
                    applier.apply(metadata)
                    Log.i("GrpcCredentials-Op", "Credenziali API applicate (Operazione).")
                }

                override fun thisUsesUnstableApi() {}
            })

        withContext(Dispatchers.Main) {
            Log.d("ProgressBarVisibility", "Mostro ProgressBar all'inizio del polling.")
            progressBar.visibility = View.VISIBLE
        }

        while (!isOperationFinished && retryCount < maxRetries && scope.isActive) {
            try {
                val request = GetOperationRequest.newBuilder()
                    .setName(operationName)
                    .build()
                val operationResponse = operationsStub.getOperation(request)

                Log.d("OperationStatus", "Operation Name: $operationName, Done: ${operationResponse.done}")

                if (operationResponse.done) {
                    isOperationFinished = true
                    withContext(Dispatchers.Main) {
                        Log.d("ProgressBarVisibility", "Imposto ProgressBar su GONE al completamento (done = true).")
                        progressBar.visibility = View.GONE
                        if (operationResponse.hasError()) {
                            val error = operationResponse.error
                            Log.e("SpeechOperation", "Errore nella trascrizione ($operationName): ${error.message}")
                            transcriptionTextView.text = "Errore nella trascrizione: ${error.message}"
                        } else {
                            Log.i("OperationResponse", "Tipo di risposta (Any): ${operationResponse.response.typeUrl}")
                            try {
                                val transcribeResponse =
                                    operationResponse.response.unpack(LongRunningRecognizeResponse::class.java)
                                val finalTranscription = StringBuilder()
                                transcribeResponse.resultsList.forEach { result ->
                                    result.alternativesList.forEach { alternative ->
                                        finalTranscription.append(alternative.transcript).append(" ")
                                        Log.i(
                                            "TranscriptionResult",
                                            "Trascrizione parziale ($operationName): ${alternative.transcript}"
                                        )
                                    }
                                }
                                val trimmedTranscription = finalTranscription.trim()
                                if (trimmedTranscription.isNotEmpty()) {
                                    transcriptionTextView.text = trimmedTranscription
                                } else {
                                    transcriptionTextView.text = "Nessuna trascrizione rilevata."
                                }
                            } catch (e: Exception) {
                                Log.e(
                                    "SpeechOperation",
                                    "Errore durante l'estrazione della risposta ($operationName): ${e.localizedMessage}",
                                    e
                                )
                                transcriptionTextView.text = "Errore nell'elaborazione della trascrizione."
                            }
                        }
                    }
                } else {
                    Log.i(
                        "SpeechOperation",
                        "Operazione ($operationName) in corso. Attendo 2 secondi (tentativo ${retryCount + 1})."
                    )
                    delay(2000)
                    retryCount++
                }
            } catch (e: Exception) {
                Log.e(
                    "SpeechOperation",
                    "Errore durante il polling ($operationName), tentativo ${retryCount + 1}: ${e.localizedMessage}",
                    e
                )
                withContext(Dispatchers.Main) {
                    Log.d("ProgressBarVisibility", "Imposto ProgressBar su GONE in caso di errore di polling.")
                    progressBar.visibility = View.GONE
                    transcriptionTextView.text = "Errore di comunicazione con il servizio di trascrizione."
                }
                delay(2000)
                retryCount++
            }
        }

        if (!isOperationFinished) {
            Log.w("SpeechOperation", "Timeout raggiunto per l'operazione: $operationName")
            withContext(Dispatchers.Main) {
                Log.d("ProgressBarVisibility", "Imposto ProgressBar su GONE in caso di timeout.")
                progressBar.visibility = View.GONE
                transcriptionTextView.text = "Timeout durante la trascrizione."
            }
        }
    }

    private fun startPollingOperationStatus(operationName: String, sharedChannel: io.grpc.ManagedChannel?) {
        lifecycleScope.launch(Dispatchers.IO) {
            getOperationStatus(operationName, this, sharedChannel) // Passa il canale condiviso
        }
    }
}