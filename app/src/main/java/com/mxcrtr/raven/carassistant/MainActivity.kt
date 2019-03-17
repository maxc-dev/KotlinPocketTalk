package com.mxcrtr.raven.carassistant

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

class MainActivity : AppCompatActivity(), edu.cmu.pocketsphinx.RecognitionListener {

    /** Reference to the speech recogniser.  */
    private var recogniser: SpeechRecognizer? = null

    /** Reference to the dictionary storing the words for the grammar language model.  */
    private val INSTANT_WORDS = "instant"
    private val REQUESTS_WORDS = "requests"
    private val TRIGGER_PHRASE = "hey raven"

    /** Used to handle the audio permission request.  */
    private val PERMISSION_REQUEST_RECORD_AUDIO = 1
    private val SPEECH_TIMEOUT = 3000

    var listening = true

    private var speechOutput: TextToSpeech? = null

    var commandHandler = CommandHandler(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speechOutput = TextToSpeech(this, TextToSpeech.OnInitListener { i ->
            if (i != TextToSpeech.ERROR) {
                speechOutput?.language = Locale.UK
                speechOutput?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(s: String) {}

                    override fun onDone(s: String) {
                        restartRecognizer()
                    }

                    override fun onError(s: String) {}

                })
            }
        })

        // Checks to see if the user has permitted the app to record audio.
        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
            return
        }

        // Initiates the recogniser in an AsyncTask.
        SetupTask(this).execute()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Initiates the recognizer in an AsyncTask.
                SetupTask(this).execute()
            } else {
                finish()
            }
        }
    }

    fun SpeakText(text: String) {
        speechOutput?.speak(text, TextToSpeech.QUEUE_ADD, null, text)
    }

    private class SetupTask internal constructor(activity: MainActivity) : AsyncTask<Void, Void, Exception>() {
        /** Reference to the main activity class.  */
        private val activityReference: WeakReference<MainActivity> = WeakReference(activity)

        override fun doInBackground(vararg params: Void): Exception? {
            try {
                // Obtaining the file directory to the resource file.
                activityReference.get()!!.initRecognizer(
                    Assets(
                        activityReference.get()
                    ).syncAssets()
                )
            } catch (ex: IOException) {
                return ex
            }

            return null
        }

        override fun onPostExecute(result: Exception?) {
            if (result != null) {
                // If the task fails...

            } else {
                // If the task is a success... It starts the recogniser.
                activityReference.get()!!.restartRecognizer()
            }
        }
    }

    /** Restarts the recogniser to listen for a command.  */
    private fun restartRecognizer() {
        recogniser?.stop()
        recogniser?.startListening(if (listening) REQUESTS_WORDS else INSTANT_WORDS , SPEECH_TIMEOUT)
    }

    /**
     * Initiates the recogniser and adds the grammar language model.
     *
     * @param assetsDir     Directory to the assets.
     * @throws IOException  Exception is thrown if there is an incorrect directory.
     */
    @Throws(IOException::class)
    private fun initRecognizer(assetsDir: File) {

        recogniser = defaultSetup()
            .setAcousticModel(File(assetsDir, "en-us-ptm"))
            .setDictionary(File(assetsDir, "cmudict-en-us.dict"))
            .recognizer

        // Adds current activity as a listener for the recogniser.
        recogniser?.addListener(this)

        // Adds the grammar model which detects words being used.
        recogniser?.addGrammarSearch(INSTANT_WORDS, File(assetsDir, "instant.gram"))

        recogniser?.addGrammarSearch(REQUESTS_WORDS, File(assetsDir, "requests.gram"))

        recogniser?.addKeyphraseSearch(TRIGGER_PHRASE, TRIGGER_PHRASE)
    }

    //overrides...

    public override fun onDestroy() {
        super.onDestroy()

        if (speechOutput != null) {
            speechOutput?.stop()
            speechOutput?.shutdown()
        }

        if (recogniser != null) {
            recogniser?.cancel()
            recogniser?.shutdown()
        }
    }

    override fun onError(p0: java.lang.Exception?) {}

    override fun onResult(hypothesis: Hypothesis?) {
        if (!speechOutput!!.isSpeaking && hypothesis != null) {
            println(hypothesis.hypstr)
            commandHandler.inputCommand(hypothesis.hypstr)
        }
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        if (speechOutput!!.isSpeaking && hypothesis != null) {
            hypothesis.delete()
        }
    }

    override fun onTimeout() {
        restartRecognizer()
    }

    override fun onBeginningOfSpeech() {}

    override fun onEndOfSpeech() {
        if (!speechOutput!!.isSpeaking) restartRecognizer()
    }

}
