package com.learn.mobileaipoc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learn.mobileaipoc.databinding.FragmentRagBinding
import com.learn.mobileaipoc.ai.LeapManager
import com.pdftron.pdf.PDFDoc
import com.pdftron.pdf.TextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

class RagFragment : Fragment() {
    private var _binding: FragmentRagBinding? = null
    private val binding get() = _binding!!
    private var pdfText: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonPickPdf.setOnClickListener { pickPdf() }
        binding.buttonLoadModel.setOnClickListener { loadModel() }
        binding.buttonAsk.setOnClickListener { askQuestion() }
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, REQUEST_PICK_PDF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_PDF && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            extractPdfText(uri)
        }
    }

    private fun extractPdfText(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri).use { input ->
                    if (input == null) return@withContext ""
                    val doc = PDFDoc(input)
                    doc.initSecurityHandler()
                    val extractor = TextExtractor()
                    val sb = StringBuilder()
                    var pageNum = 1
                    val pageCount = doc.pageCount
                    while (pageNum <= pageCount && sb.length < 800000) {
                        val page = doc.getPage(pageNum)
                        extractor.begin(page)
                        sb.append(extractor.getAsText())
                        pageNum++
                    }
                    doc.close()
                    sb.toString()
                }
            }
            pdfText = text
            binding.textPdfStatus.text = getFileName(uri) + ": " + getString(R.string.model_loaded)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "document.pdf"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) name = cursor.getString(nameIndex)
        }
        return name
    }

    private fun loadModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = LeapManager.ensureModelLoadedFromFilesDir(
                requireContext(),
                preferredName = "LFM2-1.2B-8da4w_output_8da8w-seq_4096.bundle"
            )
            binding.textPdfStatus.text = if (ok) getString(R.string.model_loaded) else getString(R.string.model_load_failed)
        }
    }

    private fun askQuestion() {
        val question = binding.editQuestion.text?.toString()?.trim().orEmpty()
        if (question.isEmpty() || pdfText.isEmpty()) {
            binding.textAnswer.text = getString(R.string.load_model_first)
            return
        }
        val conversation = LeapManager.createConversationOrNull() ?: run {
            binding.textAnswer.text = getString(R.string.load_model_first)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val method = conversation.javaClass.getMethod("generateResponse", String::class.java)
                val prompt = "You are a helpful assistant. Use the provided PDF context to answer the question.\n\nContext:\n${pdfText.take(6000)}\n\nQuestion: $question\n\nAnswer:"
                val flow = method.invoke(conversation, prompt)
                val flowClass = Class.forName("kotlinx.coroutines.flow.Flow")
                if (flowClass.isInstance(flow)) {
                    @Suppress("UNCHECKED_CAST")
                    val kflow = flow as kotlinx.coroutines.flow.Flow<Any>
                    binding.textAnswer.text = ""
                    kflow.onEach { resp ->
                        val text = extractTextFromMessageResponse(resp)
                        if (text != null) binding.textAnswer.append(text)
                    }.catch { e ->
                        binding.textPdfStatus.text = getString(R.string.generation_error, e.message ?: "error")
                    }.collect()
                }
            } catch (e: Exception) {
                binding.textPdfStatus.text = getString(R.string.generation_error, e.message ?: "error")
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
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_PICK_PDF = 4001
    }
}


