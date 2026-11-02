package com.example.htmlrunner

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

data class NoteEntry(val word: String, var note: String)

data class MarginProject(
    var title: String = "",
    var subtitle: String = "",
    var passage: String = "",
    var notes: MutableList<NoteEntry> = mutableListOf(),
    var highlight: String = "#ffe9a8"
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvDisplayTitle: TextView
    private lateinit var tvDisplaySubtitle: TextView
    private lateinit var tvPassage: TextView
    private lateinit var btnFloatingAnnotate: Button

    private val gson = Gson()
    private var project = MarginProject(
        title = "The Headland Path",
        subtitle = "Weekly Reading Session",
        passage = "The lamp had burned low by the time she reached the headland, and the sea below was the colour of old pewter.\n\nShe had walked this path every evening for a fortnight, and never once had she seen another soul upon it.",
        notes = mutableListOf(
            NoteEntry("headland", "/ˈhɛd.lənd/ (noun) — A narrow ridge of high land jutting out into the sea."),
            NoteEntry("fortnight", "/ˈfɔːt.naɪt/ (noun) — A period of two weeks.")
        )
    )

    // File pickers for importing and exporting .margin files
    private val openMarginLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importMarginFile(it) }
    }

    private val saveMarginLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { exportMarginFile(it) }
    }

    private val exportHtmlLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        uri?.let { exportHtmlFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDisplayTitle = findViewById(R.id.tvDisplayTitle)
        tvDisplaySubtitle = findViewById(R.id.tvDisplaySubtitle)
        tvPassage = findViewById(R.id.tvPassage)
        btnFloatingAnnotate = findViewById(R.id.btnFloatingAnnotate)

        tvPassage.movementMethod = LinkMovementMethod.getInstance()

        findViewById<ImageButton>(R.id.btnOpenFile).setOnClickListener {
            openMarginLauncher.launch(arrayOf("*/*"))
        }

        findViewById<ImageButton>(R.id.btnSaveMargin).setOnClickListener {
            val defaultName = project.title.ifEmpty { "reading" }.replace("\\s+".toRegex(), "_") + ".margin"
            saveMarginLauncher.launch(defaultName)
        }

        findViewById<ImageButton>(R.id.btnEditPassage).setOnClickListener {
            showEditPassageDialog()
        }

        findViewById<ImageButton>(R.id.btnExportHtml).setOnClickListener {
            val defaultName = project.title.ifEmpty { "reading" }.replace("\\s+".toRegex(), "_") + ".html"
            exportHtmlLauncher.launch(defaultName)
        }

        btnFloatingAnnotate.setOnClickListener {
            val start = tvPassage.selectionStart
            val end = tvPassage.selectionEnd
            if (start >= 0 && end > start) {
                val selected = tvPassage.text.substring(start, end).trim()
                if (selected.isNotEmpty()) {
                    showNoteDialog(selected)
                    return@setOnClickListener
                }
            }
            Toast.makeText(this, "Select a word in the text first", Toast.LENGTH_SHORT).show()
        }

        renderProject()
    }

    private fun renderProject() {
        tvDisplayTitle.text = project.title.ifEmpty { "Untitled Session" }
        tvDisplaySubtitle.text = project.subtitle.ifEmpty { "WEEKLY READING" }

        val text = project.passage
        val spannable = SpannableString(text)

        // Highlight and attach click triggers for all notes
        for (entry in project.notes) {
            val pattern = Pattern.compile("(?i)\\b" + Pattern.quote(entry.word) + "\\b")
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val s = matcher.start()
                val e = matcher.end()

                // Highlight color matching your design (#FFE9A8)
                spannable.setSpan(BackgroundColorSpan(Color.parseColor("#FFE9A8")), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                // Click to view/edit note
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        showNoteDialog(entry.word)
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = Color.parseColor("#14120E")
                    }
                }, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        tvPassage.text = spannable
    }

    private fun showNoteDialog(word: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_annotation, null)
        dialog.setContentView(view)

        val tvWord = view.findViewById<TextView>(R.id.tvAnnotateWord)
        val etNote = view.findViewById<EditText>(R.id.etNoteContent)
        val btnSave = view.findViewById<Button>(R.id.btnSaveNote)
        val btnDelete = view.findViewById<Button>(R.id.btnDeleteNote)

        tvWord.text = word

        val existingIndex = project.notes.indexOfFirst { it.word.equals(word, ignoreCase = true) }
        if (existingIndex >= 0) {
            etNote.setText(project.notes[existingIndex].note)
            btnDelete.visibility = View.VISIBLE
        } else {
            btnDelete.visibility = View.GONE
        }

        btnSave.setOnClickListener {
            val noteText = etNote.text.toString().trim()
            if (noteText.isEmpty()) return@setOnClickListener

            if (existingIndex >= 0) {
                project.notes[existingIndex].note = noteText
            } else {
                project.notes.add(NoteEntry(word, noteText))
            }
            renderProject()
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            if (existingIndex >= 0) {
                project.notes.removeAt(existingIndex)
                renderProject()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditPassageDialog() {
        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etTitle = EditText(this).apply { hint = "Title"; setText(project.title) }
        val etSub = EditText(this).apply { hint = "Subtitle"; setText(project.subtitle) }
        val etText = EditText(this).apply { hint = "Passage text..."; setText(project.passage); minLines = 8; gravity = android.view.Gravity.TOP }

        container.addView(etTitle)
        container.addView(etSub)
        container.addView(etText)

        AlertDialog.Builder(this)
            .setTitle("Edit Reading Passage")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                project.title = etTitle.text.toString().trim()
                project.subtitle = etSub.text.toString().trim()
                project.passage = etText.text.toString()
                renderProject()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importMarginFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val json = reader.readText()
                val imported = gson.fromJson(json, MarginProject::class.java)
                if (imported != null) {
                    project = imported
                    renderProject()
                    Toast.makeText(this, "Loaded: ${project.title}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read .margin file", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportMarginFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                val json = gson.toJson(project)
                stream.write(json.toByteArray())
                Toast.makeText(this, "Saved .margin file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportHtmlFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                val html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>${project.title}</title>
                    <style>
                      body { font-family: Georgia, serif; background: #FAF7F0; color: #14120E; padding: 32px 18px; max-width: 650px; margin: auto; line-height: 1.8; }
                      h1 { margin-bottom: 4px; }
                      .sub { text-transform: uppercase; font-size: 11px; letter-spacing: 1px; color: #655E52; margin-bottom: 24px; }
                      .note-term { background: #FFE9A8; border-bottom: 2px solid #B8860B; cursor: pointer; padding: 0 2px; }
                      .glossary { margin-top: 48px; padding-top: 24px; border-top: 1px solid #E5DEC9; }
                    </style>
                    </head>
                    <body>
                      <h1>${project.title}</h1>
                      <div class="sub">${project.subtitle}</div>
                      <div>${project.passage.replace("\n", "<br><br>")}</div>
                      <div class="glossary">
                        <h3>Glossary</h3>
                        <ul>
                          ${project.notes.joinToString("") { "<li><strong>" + it.word + ":</strong> " + it.note + "</li>" }}
                        </ul>
                      </div>
                    </body>
                    </html>
                """.trimIndent()
                stream.write(html.toByteArray())
                Toast.makeText(this, "Exported Student HTML file!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
}
