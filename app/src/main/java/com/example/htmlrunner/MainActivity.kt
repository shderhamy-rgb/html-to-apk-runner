package com.example.htmlrunner

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.google.gson.GsonBuilder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

data class LexiExport(
    val schemaVersion: Int = 1,
    val exportedAt: String = "",
    val items: List<LexiItem> = emptyList()
)

data class LexiItem(
    var id: String = "v-" + UUID.randomUUID().toString().substring(0, 8),
    var term: String = "",
    var definition: String = "",
    var examples: MutableList<String> = mutableListOf()
)

class MainActivity : AppCompatActivity() {

    private val wordList = mutableListOf<LexiItem>()
    private val selectedIds = mutableSetOf<String>()
    private var isSelectionMode = false

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val localStoreFileName = "vocab_store.json"
    private var isDarkMode = true

    // Export mode state: 0 = all, 1 = pending only, 2 = custom selected
    private var exportMode = 0

    private lateinit var rootLayout: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvCountBadge: TextView
    private lateinit var btnToggleTheme: MaterialButton
    private lateinit var etSearch: EditText
    private lateinit var actionsRow: LinearLayout
    private lateinit var btnAddWord: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnImport: MaterialButton

    private lateinit var selectionBar: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnExportSelected: MaterialButton
    private lateinit var btnCancelSelection: MaterialButton

    private lateinit var wordsContainer: LinearLayout

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { saveExportFile(it) }
    }

    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { readImportFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 32)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        tvTitle = TextView(this).apply {
            text = "LexiKeep"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvCountBadge = TextView(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 10, 20, 10)
        }
        btnToggleTheme = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "◐ Theme"
            textSize = 12f
            setOnClickListener {
                isDarkMode = !isDarkMode
                applyTheme()
                renderList(etSearch.text?.toString()?.trim() ?: "")
            }
        }
        header.addView(tvTitle)
        header.addView(tvCountBadge)
        header.addView(btnToggleTheme)
        rootLayout.addView(header)

        // Search Bar
        etSearch = EditText(this).apply {
            hint = "Search terms, definitions, examples..."
            textSize = 14f
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        rootLayout.addView(etSearch)

        // Standard Actions
        actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        btnAddWord = MaterialButton(this).apply {
            text = "+ Add Word"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            setOnClickListener { showWordDialog(null) }
        }
        btnExport = MaterialButton(this).apply {
            text = "Export"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(12, 0, 12, 0) }
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
        rootLayout.addView(actionsRow)

        // Selection Action Bar (hidden by default)
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
            visibility = View.GONE
        }
        tvSelectionCount = TextView(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnExportSelected = MaterialButton(this).apply {
            text = "Export (0)"
            setOnClickListener {
                if (selectedIds.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No items selected", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                exportMode = 2
                exportJsonLauncher.launch("lexicon_selected.json")
            }
        }
        btnCancelSelection = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Cancel"
            setOnClickListener {
                exitSelectionMode()
            }
        }
        selectionBar.addView(tvSelectionCount)
        selectionBar.addView(btnExportSelected)
        selectionBar.addView(btnCancelSelection)
        rootLayout.addView(selectionBar)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        wordsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(wordsContainer)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderList(s?.toString()?.trim() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        loadPersistedWords()
        applyTheme()
        renderList()
    }

    private fun loadPersistedWords() {
        val file = File(filesDir, localStoreFileName)
        if (file.exists()) {
            try {
                val json = file.readText()
                val parsed = gson.fromJson(json, LexiExport::class.java)
                if (parsed != null && parsed.items.isNotEmpty()) {
                    wordList.clear()
                    wordList.addAll(parsed.items)
                }
            } catch (e: Exception) { }
        }
    }

    private fun persistWordsLocally() {
        try {
            val file = File(filesDir, localStoreFileName)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val payload = LexiExport(
                schemaVersion = 1,
                exportedAt = sdf.format(Date()),
                items = wordList
            )
            file.writeText(gson.toJson(payload))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save state locally", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme() {
        val bg = if (isDarkMode) Color.parseColor("#12110D") else Color.parseColor("#FAF7F0")
        val cardBg = if (isDarkMode) Color.parseColor("#1C1A14") else Color.parseColor("#FFFFFF")
        val text = if (isDarkMode) Color.parseColor("#ECE5D8") else Color.parseColor("#14120E")
        val accent = if (isDarkMode) Color.parseColor("#E2B755") else Color.parseColor("#B8860B")
        val border = if (isDarkMode) Color.parseColor("#2E2920") else Color.parseColor("#E5DEC9")

        rootLayout.setBackgroundColor(bg)
        tvTitle.setTextColor(text)
        btnToggleTheme.setTextColor(accent)
        tvSelectionCount.setTextColor(text)

        val searchDrawable = GradientDrawable().apply {
            setColor(cardBg)
            cornerRadius = 16f
            setStroke(2, border)
        }
        etSearch.background = searchDrawable
        etSearch.setTextColor(text)
        etSearch.setHintTextColor(if (isDarkMode) Color.parseColor("#80796B") else Color.parseColor("#9C9484"))

        val badgeDrawable = GradientDrawable().apply {
            setColor(if (isDarkMode) Color.parseColor("#2D2413") else Color.parseColor("#FFE9A8"))
            cornerRadius = 24f
        }
        tvCountBadge.background = badgeDrawable
        tvCountBadge.setTextColor(accent)

        val btnTint = ColorStateList.valueOf(if (isDarkMode) Color.parseColor("#ECE5D8") else Color.parseColor("#14120E"))
        val btnTextColor = if (isDarkMode) Color.parseColor("#12110D") else Color.parseColor("#FFFFFF")
        for (b in arrayOf(btnAddWord, btnExport, btnImport, btnExportSelected)) {
            b.backgroundTintList = btnTint
            b.setTextColor(btnTextColor)
            b.cornerRadius = 16
        }
        btnCancelSelection.setTextColor(accent)
    }

    private fun enterSelectionMode(initialSelectedId: String) {
        isSelectionMode = true
        selectedIds.clear()
        selectedIds.add(initialSelectedId)
        actionsRow.visibility = View.GONE
        selectionBar.visibility = View.VISIBLE
        updateSelectionUI()
        renderList(etSearch.text?.toString()?.trim() ?: "")
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        selectionBar.visibility = View.GONE
        actionsRow.visibility = View.VISIBLE
        renderList(etSearch.text?.toString()?.trim() ?: "")
    }

    private fun updateSelectionUI() {
        val count = selectedIds.size
        tvSelectionCount.text = "$count selected"
        btnExportSelected.text = "Export ($count)"
    }

    private fun renderList(query: String = "") {
        wordsContainer.removeAllViews()
        val filtered = if (query.isEmpty()) wordList else wordList.filter {
            it.term.contains(query, ignoreCase = true) ||
            it.definition.contains(query, ignoreCase = true) ||
            it.examples.any { ex -> ex.contains(query, ignoreCase = true) }
        }

        tvCountBadge.text = "${wordList.size} items"

        val cardBg = if (isDarkMode) Color.parseColor("#1C1A14") else Color.parseColor("#FFFFFF")
        val selectedCardBg = if (isDarkMode) Color.parseColor("#2D2513") else Color.parseColor("#FFF3D1")
        val textPrimary = if (isDarkMode) Color.parseColor("#ECE5D8") else Color.parseColor("#14120E")
        val textMuted = if (isDarkMode) Color.parseColor("#9C9484") else Color.parseColor("#655E52")
        val termColor = if (isDarkMode) Color.parseColor("#E2B755") else Color.parseColor("#B8860B")
        val border = if (isDarkMode) Color.parseColor("#2E2920") else Color.parseColor("#E5DEC9")
        val selectedBorder = if (isDarkMode) Color.parseColor("#E2B755") else Color.parseColor("#B8860B")

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (query.isEmpty()) "No words saved. Tap Import or + Add Word." else "No matches found."
                setTextColor(textMuted)
                textSize = 14f
                setPadding(0, 48, 0, 0)
            }
            wordsContainer.addView(empty)
            return
        }

        for (item in filtered) {
            val isChecked = selectedIds.contains(item.id)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 20)
                }
                layoutParams = params
                background = GradientDrawable().apply {
                    setColor(if (isChecked) selectedCardBg else cardBg)
                    cornerRadius = 16f
                    setStroke(if (isChecked) 3 else 2, if (isChecked) selectedBorder else border)
                }
                isClickable = true
                setOnClickListener {
                    if (isSelectionMode) {
                        if (isChecked) selectedIds.remove(item.id) else selectedIds.add(item.id)
                        if (selectedIds.isEmpty()) {
                            exitSelectionMode()
                        } else {
                            updateSelectionUI()
                            renderList(etSearch.text?.toString()?.trim() ?: "")
                        }
                    } else {
                        showWordDialog(item)
                    }
                }
                setOnLongClickListener {
                    if (!isSelectionMode) {
                        enterSelectionMode(item.id)
                    } else {
                        confirmDelete(item)
                    }
                    true
                }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            if (isSelectionMode) {
                val cb = CheckBox(this).apply {
                    this.isChecked = isChecked
                    isClickable = false
                    buttonTintList = ColorStateList.valueOf(termColor)
                }
                topRow.addView(cb)
            }

            val tvTerm = TextView(this).apply {
                text = item.term
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(termColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(tvTerm)
            card.addView(topRow)

            val tvDef = TextView(this).apply {
                text = if (item.definition.isBlank()) "(Pending definition)" else item.definition
                textSize = 14f
                setTextColor(if (item.definition.isBlank()) Color.parseColor("#D9822B") else textPrimary)
                setLineSpacing(0f, 1.25f)
                setPadding(0, 8, 0, 8)
            }
            card.addView(tvDef)

            if (item.examples.isNotEmpty()) {
                val tvEx = TextView(this).apply {
                    text = "• " + item.examples.joinToString("\n• ")
                    textSize = 13f
                    setTextColor(textMuted)
                    setLineSpacing(0f, 1.2f)
                }
                card.addView(tvEx)
            }

            wordsContainer.addView(card)
        }
    }

    private fun showWordDialog(existingItem: LexiItem?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etT = EditText(this).apply { hint = "Word / Term"; existingItem?.let { setText(it.term) } }
        val etD = EditText(this).apply { hint = "Definition"; existingItem?.let { setText(it.definition) }; minLines = 2 }
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
                    wordList.add(0, LexiItem(term = term, definition = def, examples = exLines))
                } else {
                    existingItem.term = term
                    existingItem.definition = def
                    existingItem.examples = exLines
                }
                persistWordsLocally()
                renderList(etSearch.text?.toString()?.trim() ?: "")
            }
            .setNegativeButton("Cancel", null)

        if (existingItem != null) {
            builder.setNeutralButton("Delete") { _, _ -> confirmDelete(existingItem) }
        }
        builder.show()
    }

    private fun confirmDelete(item: LexiItem) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Do you want to delete \"" + item.term + "\"?")
            .setPositiveButton("Delete") { _, _ ->
                wordList.remove(item)
                selectedIds.remove(item.id)
                persistWordsLocally()
                renderList(etSearch.text?.toString()?.trim() ?: "")
                Toast.makeText(this, "Deleted " + item.term, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExportDialog() {
        val incomplete = wordList.count { it.definition.isBlank() || it.examples.isEmpty() }
        val options: Array<CharSequence> = arrayOf(
            "Export All Items (" + wordList.size + ")",
            "Export Pending / Incomplete Only (" + incomplete + " pending)",
            "Enter Multi-Select Mode..."
        )

        AlertDialog.Builder(this)
            .setTitle("Export Lexicon")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        exportMode = 0
                        exportJsonLauncher.launch("lexicon_all.json")
                    }
                    1 -> {
                        exportMode = 1
                        exportJsonLauncher.launch("lexicon_pending.json")
                    }
                    2 -> {
                        if (wordList.isNotEmpty()) {
                            enterSelectionMode(wordList[0].id)
                        } else {
                            Toast.makeText(this, "No words available to select", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveExportFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                val targetList = when (exportMode) {
                    1 -> wordList.filter { it.definition.isBlank() || it.examples.isEmpty() }
                    2 -> wordList.filter { selectedIds.contains(it.id) }
                    else -> wordList
                }
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val exportData = LexiExport(
                    schemaVersion = 1,
                    exportedAt = sdf.format(Date()),
                    items = targetList
                )
                stream.write(gson.toJson(exportData).toByteArray())
                val modeLabel = when (exportMode) {
                    1 -> "pending items"
                    2 -> "selected items"
                    else -> "all items"
                }
                Toast.makeText(this, "Exported " + targetList.size + " " + modeLabel, Toast.LENGTH_SHORT).show()
                if (exportMode == 2) exitSelectionMode()
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
                val parsed = gson.fromJson(json, LexiExport::class.java)
                val incomingItems = parsed?.items ?: emptyList()

                if (incomingItems.isEmpty()) {
                    Toast.makeText(this, "No items found in JSON", Toast.LENGTH_SHORT).show()
                    return
                }

                // Show selective import dialog
                showSelectiveImportDialog(incomingItems)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSelectiveImportDialog(incomingItems: List<LexiItem>) {
        val displayItems = incomingItems.map { it.term + if (it.definition.isNotBlank()) " (defined)" else " (pending)" }.toTypedArray()
        val checkedState = BooleanArray(incomingItems.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Select Items to Import (" + incomingItems.size + ")")
            .setMultiChoiceItems(displayItems, checkedState) { _, which, isChecked ->
                checkedState[which] = isChecked
            }
            .setPositiveButton("Import Selected") { _, _ ->
                val chosenItems = incomingItems.filterIndexed { index, _ -> checkedState[index] }
                applyImportedItems(chosenItems)
            }
            .setNeutralButton("Select All") { dialog, _ ->
                applyImportedItems(incomingItems)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyImportedItems(itemsToMerge: List<LexiItem>) {
        var updatedCount = 0
        var addedCount = 0

        for (imp in itemsToMerge) {
            val existingIndex = wordList.indexOfFirst {
                (it.id.isNotBlank() && it.id == imp.id) ||
                it.term.equals(imp.term, ignoreCase = true)
            }

            if (existingIndex != -1) {
                val target = wordList[existingIndex]
                if (imp.definition.isNotBlank()) target.definition = imp.definition
                if (imp.examples.isNotEmpty()) target.examples = imp.examples
                if (imp.term.isNotBlank()) target.term = imp.term
                updatedCount++
            } else {
                wordList.add(imp)
                addedCount++
            }
        }

        persistWordsLocally()
        renderList(etSearch.text?.toString()?.trim() ?: "")
        Toast.makeText(
            this,
            "Imported: $updatedCount replaced/updated, $addedCount new added",
            Toast.LENGTH_LONG
        ).show()
    }
}