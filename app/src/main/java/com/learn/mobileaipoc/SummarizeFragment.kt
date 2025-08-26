package com.learn.mobileaipoc

import ai.liquid.leap.message.MessageResponse
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learn.mobileaipoc.databinding.FragmentSummarizeBinding
import com.learn.mobileaipoc.ai.LeapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

const val TAG = "SummarizeFragment"
class SummarizeFragment : Fragment() {

    private var _binding: FragmentSummarizeBinding? = null
    private val binding get() = _binding!!
    private var generationJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSummarizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLoadModel.setOnClickListener { loadModel() }
        binding.buttonSummarize.setOnClickListener {
            binding.progressBar.visibility= View.VISIBLE
            summarize() }
        binding.buttonCancel.setOnClickListener { generationJob?.cancel() }
        loadModel()
    }

    private fun loadModel() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = LeapManager.ensureModelLoadedFromFilesDir(
                requireContext(),
                preferredName = "model.bundle"
            )
            Log.d(TAG,"" + if (ok) getString(R.string.model_loaded) else getString(R.string.model_load_failed))
            withContext(Dispatchers.Main) {
                binding.textStatus.text = if (ok) getString(R.string.model_loaded) else getString(R.string.model_load_failed)
            }
        }
    }

    private fun summarize() {
        val input = binding.editInput.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) return
        val generateTextBuffer = StringBuilder()

        val conversation = LeapManager.createConversationOrNull()
        // Use reflection to call Conversation.generateResponse(input): Flow<MessageResponse>
        generationJob?.cancel()
        generationJob = viewLifecycleOwner.lifecycleScope.launch {
            try {

                    conversation?.generateResponse(input)?.onEach {
                        when (it) {
                            is MessageResponse.Chunk -> {
                                generateTextBuffer.append(it.text)
                                binding.textOutput.text =generateTextBuffer.toString()
                                Log.d(TAG, "text chunk: ${it.text}")
                            }
                            is MessageResponse.ReasoningChunk -> {
//                                generatedReasoningBuffer.append(it.reasoning)

//                                Log.d(TAG, "reasoning chunk: ${it.text}")
                            }
                            else -> {
                                // ignore other response
                            }
                        }
                    }
                        ?.onCompletion {
                            binding.progressBar.visibility= View.GONE

                            Log.d(TAG, "Generation done!")
                            binding.textOutput.text =generateTextBuffer.toString()
                        }
                        ?.catch { exception ->
                            Log.e(TAG, "Error in generation: $exception")
                        }
                        ?.collect()

            } catch (e: Exception) {
                binding.textStatus.text = getString(R.string.generation_error, e.message ?: "error")
            }
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
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        generationJob?.cancel()
        _binding = null
    }
}


