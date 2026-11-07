package com.example.htmlrunner

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

data class WordItem(
    var term: String = "",
    var definition: String = "",
    var examples: MutableList<String> = mutableListOf()
)

class MainActivity : AppCompatActivity() {

    private val wordList = mutableListOf<WordItem>()
    private val gson = Gson()
    private var exportIncompleteOnly = false

    private lateinit var etSearch: EditText
    private lateinit var tvCountBadge: TextView
    private lateinit var wordsContainer: LinearLayout
    private lateinit var btnAddWord: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnImport: MaterialButton

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { saveExportFile(it) }
    }

    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { readImportFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAF7F0"))
            setPadding(32, 48, 32, 32)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        val titleView = TextView(this).apply {
            text = "LexiKeep"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#14120E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvCountBadge = TextView(this).apply {
            text = "0 items"
            textSize = 14f
            setTextColor(Color.parseColor("#655E52"))
            setPadding(16, 8, 16, 8)
        }
        header.addView(titleView)
        header.addView(tvCountBadge)
        root.addView(header)

        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        etSearch = EditText(this).apply {
            hint = "Search terms, definitions, examples..."
            textSize = 14f
            setBackgroundColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        searchBar.addView(etSearch)
        root.addView(searchBar)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        btnAddWord = MaterialButton(this).apply {
            text = "+ Add Word"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showWordDialog(null) }
        }
        btnExport = MaterialButton(this).apply {
            text = "Export"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showExportDialog() }
        }
        btnImport = MaterialButton(this).apply {
            text = "Import"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { importJsonLauncher.launch(arrayOf("application/json", "*/*")) }
        }
        actionsRow.addView(btnAddWord)
        actionsRow.addView(btnExport)
        actionsRow.addView(btnImport)
        root.addView(actionsRow)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        wordsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(wordsContainer)
        root.addView(scrollView)

        setContentView(root)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderList(s?.toString()?.trim() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        loadSampleData()
        renderList()
    }

    private fun loadSampleData() {
        if (wordList.isEmpty()) {
            wordList.add(WordItem("headland", "/ˈhɛd.lənd/ (noun) — A narrow piece of high land.", mutableListOf("The sea below the headland was rough.")))
            wordList.add(WordItem("fortnight", "/ˈfɔːt.naɪt/ (noun) — A period of two weeks.", mutableListOf("They spent a fortnight travelling.")))
            wordList.add(WordItem("unabridged", "", mutableListOf()))
        }
    }

    private fun renderList(query: String = "") {
        wordsContainer.removeAllViews()
        val filtered = if (query.isEmpty()) wordList else wordList.filter {
            it.term.contains(query, ignoreCase = true) ||
            it.definition.contains(query, ignoreCase = true) ||
            it.examples.any { ex -> ex.contains(query, ignoreCase = true) }
        }

        tvCountBadge.text = "${wordList.size} items"

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (query.isEmpty()) "No words added yet." else "No matches found."
                setTextColor(Color.parseColor("#9C9484"))
                setPadding(0, 40, 0, 0)
            }
            wordsContainer.addView(empty)
            return
        }

        for (item in filtered) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(24, 20, 24, 20)
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, 16)
                layoutParams = params
                isClickable = true
                setOnClickListener { showWordDialog(item) }
                setOnLongClickListener {
                    confirmDelete(item)
                    true
                }
            }

            val tvTerm = TextView(this).apply {
                text = item.term
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#14120E"))
            }
            val tvDef = TextView(this).apply {
                text = if (item.definition.isBlank()) "(No definition recorded)" else item.definition
                textSize = 14f
                setTextColor(if (item.definition.isBlank()) Color.parseColor("#9C9484") else Color.parseColor("#443E33"))
                setPadding(0, 4, 0, 4)
            }

            card.addView(tvTerm)
            card.addView(tvDef)

            if (item.examples.isNotEmpty()) {
                val tvEx = TextView(this).apply {
                    text = "• " + item.examples.joinToString("\n• ")
                    textSize = 13f
                    setTextColor(Color.parseColor("#655E52"))
                }
                card.addView(tvEx)
            }

            wordsContainer.addView(card)
        }
    }

    private fun showWordDialog(existingItem: WordItem?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etT = EditText(this).apply { hint = "Word / Term"; existingItem?.let { setText(it.term) } }
        val etD = EditText(this).apply { hint = "Definition"; existingItem?.let { setText(it.definition) } }
        val etEx = EditText(this).apply {
            hint = "Example sentences (one per line)"
            existingItem?.let { setText(it.examples.joinToString("\n")) }
            minLines = 3
        }

        form.addView(etT)
        form.addView(etD)
        form.addView(etEx)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existingItem == null) "Add Word" else "Edit Word")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val term = etT.text.toString().trim()
                if (term.isEmpty()) {
                    Toast.makeText(this, "Term cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val def = etD.text.toString().trim()
                val exLines = etEx.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

                if (existingItem == null) {
                    wordList.add(WordItem(term, def, exLines))
                } else {
                    existingItem.term = term
                    existingItem.definition = def
                    existingItem.examples = exLines
                }
                renderList(etSearch.text?.toString()?.trim() ?: "")
            }
            .setNegativeButton("Cancel", null)

        if (existingItem != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                confirmDelete(existingItem)
            }
        }

        builder.show()
    }

    private fun confirmDelete(item: WordItem) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete \"" + item.term + "\"?")
            .setPositiveButton("Delete") { _, _ ->
                wordList.remove(item)
                renderList(etSearch.text?.toString()?.trim() ?: "")
                Toast.makeText(this, "Deleted " + item.term, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExportDialog() {
        val incompleteCount = wordList.count { it.definition.isBlank() || it.examples.isEmpty() }
        val options: Array<CharSequence> = arrayOf(
            "Export All Items (" + wordList.size + ")",
            "Export Incomplete Only (" + incompleteCount + " missing def/ex)"
        )

        AlertDialog.Builder(this)
            .setTitle("Export Lexicon")
            .setItems(options) { _, which ->
                if (which == 0) {
                    exportIncompleteOnly = false
                    exportJsonLauncher.launch("lexicon_all.json")
                } else {
                    if (incompleteCount == 0) {
                        Toast.makeText(this, "All items are already complete!", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    exportIncompleteOnly = true
                    exportJsonLauncher.launch("lexicon_incomplete.json")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveExportFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                val targetList = if (exportIncompleteOnly) {
                    wordList.filter { it.definition.isBlank() || it.examples.isEmpty() }
                } else {
                    wordList
                }
                val json = gson.toJson(targetList)
                stream.write(json.toByteArray())
                val mode = if (exportIncompleteOnly) "incomplete items" else "all items"
                Toast.makeText(this, "Exported $mode successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readImportFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val json = reader.readText()
                val itemType = object : TypeToken<MutableList<WordItem>>() {}.type
                val imported: MutableList<WordItem> = gson.fromJson(json, itemType)
                if (imported.isNotEmpty()) {
                    for (imp in imported) {
                        val existing = wordList.find { it.term.equals(imp.term, ignoreCase = true) }
                        if (existing != null) {
                            if (imp.definition.isNotBlank()) existing.definition = imp.definition
                            for (ex in imp.examples) {
                                if (!existing.examples.contains(ex)) existing.examples.add(ex)
                            }
                        } else {
                            wordList.add(imp)
                        }
                    }
                    renderList(etSearch.text?.toString()?.trim() ?: "")
                    Toast.makeText(this, "Imported ${imported.size} items", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to import JSON", Toast.LENGTH_SHORT).show()
        }
    }
}