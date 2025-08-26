package com.learn.mobileaipoc

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learn.mobileaipoc.databinding.FragmentDictationBinding
import com.learn.mobileaipoc.ai.LeapManager
import kotlinx.coroutines.launch

class DictationFragment : Fragment() {
    private var _binding: FragmentDictationBinding? = null
    private val binding get() = _binding!!
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDictationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLoadModel.setOnClickListener { loadModel() }
        binding.buttonStart.setOnClickListener { startDictation() }
        binding.buttonStop.setOnClickListener { stopDictation() }
        ensureAudioPermission()
    }

    private fun loadModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            LeapManager.ensureModelLoadedFromFilesDir(
                requireContext(),
                preferredName = "LFM2-1.2B-8da4w_output_8da8w-seq_4096.bundle"
            )
        }
    }

    private fun ensureAudioPermission() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 2002)
        }
    }

    private fun startDictation() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) return
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { binding.textStatus.text = getString(R.string.status_idle) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { binding.textStatus.text = getString(R.string.generation_error, error.toString()) }
                override fun onResults(results: Bundle) {
                    val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = texts?.firstOrNull().orEmpty()
                    binding.editFormRaw.setText(spoken)
                    fillFormWithAi(spoken)
                }
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopDictation() {
        speechRecognizer?.stopListening()
    }

    private fun fillFormWithAi(spoken: String) {
        val conversation = LeapManager.createConversationOrNull() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val method = conversation.javaClass.getMethod("generateResponse", String::class.java)
                val prompt = "Extract name, email, and address from the following dictation and output as lines: name: ..., email: ..., address: ...\n\n$spoken"
                val flow = method.invoke(conversation, prompt)
                val flowClass = Class.forName("kotlinx.coroutines.flow.Flow")
                if (flowClass.isInstance(flow)) {
                    @Suppress("UNCHECKED_CAST")
                    val kflow = flow as kotlinx.coroutines.flow.Flow<Any>
                    val sb = StringBuilder()
                    kflow.collect { resp ->
                        val t = extractTextFromMessageResponse(resp)
                        if (t != null) sb.append(t)
                    }
                    val result = sb.toString()
                    parseAndPopulate(result)
                }
            } catch (_: Exception) {}
        }
    }

    private fun extractTextFromMessageResponse(resp: Any): String? {
        return try {
            val k = resp.javaClass
            val name = k.name
            when {
                name.contains("ReasoningChunk") -> k.getDeclaredField("text").get(resp)?.toString()
                name.contains("Chunk") -> k.getDeclaredField("text").get(resp)?.toString()
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun parseAndPopulate(result: String) {
        fun findAfter(label: String): String? {
            val regex = Regex("(?i)${label}\\s*:\\s*(.*)")
            return regex.find(result)?.groupValues?.getOrNull(1)?.trim()
        }
        binding.editName.setText(findAfter("name") ?: "")
        binding.editEmail.setText(findAfter("email") ?: "")
        binding.editAddress.setText(findAfter("address") ?: "")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _binding = null
    }
}


