package com.example.htmlrunner

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

data class AnnotationItem(
    val term: String,
    val startIndex: Int,
    val endIndex: Int,
    val definition: String,
    val note: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var etSessionTitle: EditText
    private lateinit var etReadingText: EditText
    private lateinit var btnAnnotateSelection: Button
    private lateinit var btnExport: Button

    private val annotations = mutableListOf<AnnotationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSessionTitle = findViewById(R.id.etSessionTitle)
        etReadingText = findViewById(R.id.etReadingText)
        btnAnnotateSelection = findViewById(R.id.btnAnnotateSelection)
        btnExport = findViewById(R.id.btnExport)

        btnAnnotateSelection.setOnClickListener {
            val start = etReadingText.selectionStart
            val end = etReadingText.selectionEnd

            if (start < 0 || end <= start) {
                Toast.makeText(this, "Select a word or phrase in the text first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fullText = etReadingText.text.toString()
            val term = fullText.substring(start, end).trim()

            if (term.isEmpty()) {
                Toast.makeText(this, "Highlighted selection is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showAnnotationDialog(term, start, end)
        }

        btnExport.setOnClickListener {
            val title = etSessionTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a session title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Session '$title' ready with ${annotations.size} annotations!", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAnnotationDialog(term: String, start: Int, end: Int) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_annotation, null)
        dialog.setContentView(view)

        val tvTerm = view.findViewById<TextView>(R.id.tvSelectedTerm)
        val etDefinition = view.findViewById<EditText>(R.id.etDefinition)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val btnSave = view.findViewById<Button>(R.id.btnSaveAnnotation)

        tvTerm.text = term

        btnSave.setOnClickListener {
            val definition = etDefinition.text.toString().trim()
            val note = etNote.text.toString().trim()

            if (definition.isEmpty()) {
                etDefinition.error = "Definition required"
                return@setOnClickListener
            }

            annotations.add(AnnotationItem(term, start, end, definition, note))

            val spannable = etReadingText.text as Spannable
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#FEF08A")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            dialog.dismiss()
            Toast.makeText(this, "Annotated: $term", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
