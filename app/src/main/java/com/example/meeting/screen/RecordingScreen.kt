package com.example.meeting.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.meeting.Supabaseclient
import com.example.meeting.databinding.ActivityRecordingScreenBinding
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RecordingScreen : AppCompatActivity() {
    private lateinit var binding: ActivityRecordingScreenBinding
    private var isRecording = false
    private lateinit var wavFile: File
    private var audioRecord: AudioRecord? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    private val sampleRate = 16000 // Good for Vosk
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
       // databaseRef= FirebaseDatabase.getInstance("https://tmumm-34a4f-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
        // Generate unique WAV filename
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        wavFile = File(getExternalFilesDir(Environment.DIRECTORY_RECORDINGS), "recording_$timestamp.wav")

        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            initVoskModel()
        }

        // Start recording
        binding.btnStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startRecording()
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }

        // Stop recording and transcribe
        binding.btnStop.setOnClickListener {
            stopRecording()
            transcribeAudio()
            Log.d("save","${wavFile.absolutePath}")
        }
//        binding.btn8.setOnClickListener {
//            val intent = Intent(this, Explorefiles::class.java)
//            startActivity(intent)
//        }
    }

    private fun initVoskModel() {
        // Show progress indicator
        runOnUiThread {
         //   binding.txt4.text = "Loading model..."
        }
        // Unpack Vosk model from assets
        StorageService.unpack(
            this,
            "model",
            "model",
            { loadedModel ->
                model = loadedModel
                recognizer = Recognizer(model, sampleRate.toFloat())
                runOnUiThread {
                  //  binding.txt4.text = "Model loaded, ready to record"
                }
            },
            { exception ->
                runOnUiThread {
                  //  binding.txt4.text = "Failed to load model: ${exception.message}"
                    Toast.makeText(this, "Model loading failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    Log.d("error","${exception.message}")
                }
            }
        )
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording()
    {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        val audioData = ByteArray(bufferSize)
        isRecording = true
        audioRecord?.startRecording()

        // Write placeholder WAV header
        try {
            FileOutputStream(wavFile).use { fos ->
                val header = ByteArray(44)
                fos.write(header)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Failed to create WAV file", Toast.LENGTH_SHORT).show()
            }
            return
        }
        // Start recording thread
        Thread {
            FileOutputStream(wavFile, true).use { fos ->
                var totalAudioLen = 0
                while (isRecording) {
                    val read = audioRecord?.read(audioData, 0, audioData.size) ?: 0
                    if (read > 0) {
                        try {
                            fos.write(audioData, 0, read)
                            totalAudioLen += read
                        } catch (e: IOException) {
                            runOnUiThread {
                                Toast.makeText(this, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            break
                        }
                    }
                }
                // Update WAV header
                writeWavHeader(totalAudioLen)
            }
        }.start()

        runOnUiThread {
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        runOnUiThread {
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
            Log.d("RecordingScreen", "WAV file saved at: ${wavFile.absolutePath}")
        }
    }

    private fun writeWavHeader(totalAudioLen: Int) {
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8
        val totalDataLen = totalAudioLen + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Sub-chunk size
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 16 / 8).toShort()) // Block align
            putShort(16) // Bits per sample
            put("data".toByteArray())
            putInt(totalAudioLen)
        }.array()

        try {
            RandomAccessFile(wavFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Failed to write WAV header", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun transcribeAudio() {
        if (model == null || recognizer == null) {
            Toast.makeText(this, "Vosk model not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        runOnUiThread {
        //    binding.txt4.text = "Transcribing..."
        }

        try {
            FileInputStream(wavFile).use { fis ->
                // Skip WAV header (44 bytes)
                fis.skip(44)

                val buffer = ByteArray(4096)
                recognizer?.let { rec ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        // Convert byte array to short array for Vosk
                        val shortArray = ShortArray(bytesRead / 2)
                        ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().get(shortArray)
                        rec.acceptWaveForm(shortArray, shortArray.size)
                    }
                    // Get final transcription
                    val resultJson = rec.result
                    val resultText = JSONObject(resultJson).getString("text")
                    runOnUiThread {
                       // binding.txt4.text = if (resultText.isEmpty()) "No speech detected" else resultText
                        val filename = generateFileName()
                        saveTextToFile(this,filename,resultText)
                        Toast.makeText(this, "Transcription: $resultText", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
              //  binding.txt4.text = "Transcription failed"
                Toast.makeText(this, "Transcription error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initVoskModel()
        } else {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecord?.release()
        recognizer?.close()
    }

    private fun generateFileName(): String {
        // Create a SimpleDateFormat for IST timezone
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST
        val currentTime = dateFormat.format(Date())
        return "text_$currentTime.txt" // e.g., text_2025-09-08_18-19-00.txt
    }



    private fun saveTextToFile(context: Context, fileName: String, text: String) {

        try {
            // Save locally
         //   val file = File(context.filesDir, fileName)
            val file = File(getExternalFilesDir(null), fileName)
            val fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(text.toByteArray())
            fileOutputStream.close()

            Log.d("savefile", "File saved: ${file.absolutePath}")
            Toast.makeText(this,"${file.absolutePath}",Toast.LENGTH_SHORT).show()
            // ✅ Upload to Supabase
            uploadToSupabase(file,fileName)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("savefile", "Error saving file: ${e.message}")
        }
    }

//    private fun uploadFileToFirebase(file: File, fileName: String) {
//        if(!file.exists()){
//            Log.d("firebase","file not exists")
//            return
//        }
//        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
//        val storageRef = storage.reference.child("transcripts/$fileName")
//        val bytes = file.readBytes()
//
//        val uploadTask = storageRef.putBytes(bytes)
//        uploadTask.addOnSuccessListener {
//            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
//                Log.d("Firebase", "Uploaded: $downloadUri")
//                saveFileMetadata(fileName, downloadUri.toString())
//            }
//        }.addOnFailureListener {
//            Log.e("Firebase", "Upload failed: ${it.message}")
//        }
//    }

    private fun saveFileMetadata(fileName: String, downloadUrl: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Extract only date part from filename (e.g., "2025-09-10")
        val dateKey = fileName.substring(5, 15) // text_2025-09-10...

        val transcriptData = hashMapOf(
            "fileName" to fileName,
            "date" to dateKey,
            "url" to downloadUrl
        )

        db.collection("transcripts")
            .add(transcriptData)
            .addOnSuccessListener {
                Log.d("Firestore", "Metadata saved for $fileName")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving metadata: ${e.message}")
            }
    }

//    private fun uploadTextFileToFirebase(fileUri: Uri) {
//        Log.d(">>>>","1")
//        try {
//            val inputStream = contentResolver.openInputStream(fileUri)
//            val reader = BufferedReader(InputStreamReader(inputStream))
//            val textContent = reader.use { it.readText() }
//
//            // Generate Date String: yyyy-MM-dd format
//            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//            val timeFormat= SimpleDateFormat("HH-mm-ss",Locale.getDefault())
//            val currentDate = dateFormat.format(Date())
//            val currentTime = timeFormat.format(Date())
//
//            // Reference to Firebase Database
//            //val databaseRef = FirebaseDatabase.getInstance().reference
//
//            // Store under "files/{date}/file_content"
//           // val fileNode = databaseRef.child("files").child(currentDate)
//            val fileNode=databaseRef.child("files").child(currentDate).child(currentTime)
//            Log.d(">>>>","2")
//            val fileData = mapOf(
//                "uploaded_at" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
//                "content" to textContent
//            )
//
//            fileNode.setValue(fileData)
//                .addOnSuccessListener {
//                    Log.d(">>>>","File content uploaded successfully!")
//                }
//                .addOnFailureListener { e ->
//                    Log.d(">>>>>","Upload failed: ${e.message}")
//                }
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }


    private fun uploadToSupabase(file: File, fileName: String)
    {
        val userid = Supabaseclient.getCurrentUserId()
        if(userid==null){
            Toast.makeText(this,"User is not authenticated",Toast.LENGTH_SHORT).show()
            return
        }
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            
            val storagePath = "users/$userid/transcripts/$currentDate/$fileName"

            val bucket = Supabaseclient.supabase.storage.from("notes_bucket")


            // Upload file
            bucket.upload(storagePath, file.readBytes()) {
                contentType = ContentType.Text.Plain
            }

            // Get public URL
            val publicUrl = bucket.publicUrl(storagePath)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@RecordingScreen,
                    "Uploaded to Supabase successfully!",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("Supabase", "File uploaded: $publicUrl")
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@RecordingScreen,
                    "Upload failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("Supabase", "Upload error: ${e.message}")
            }
        }
    }
}


}