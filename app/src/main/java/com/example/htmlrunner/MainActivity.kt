package com.example.htmlrunner

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
    private lateinit var tvAnnotationCount: TextView
    private lateinit var btnExport: Button

    private val annotations = mutableListOf<AnnotationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSessionTitle = findViewById(R.id.etSessionTitle)
        etReadingText = findViewById(R.id.etReadingText)
        tvAnnotationCount = findViewById(R.id.tvAnnotationCount)
        btnExport = findViewById(R.id.btnExport)

        setupTextSelectionAction()

        btnExport.setOnClickListener {
            val title = etSessionTitle.text.toString().trim()
            val text = etReadingText.text.toString().trim()

            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(
                this,
                "Ready! Saved ${annotations.size} annotations for: $title",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupTextSelectionAction() {
        etReadingText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.add(0, 1001, 0, "Annotate")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item?.itemId == 1001) {
                    val start = etReadingText.selectionStart
                    val end = etReadingText.selectionEnd

                    if (start >= 0 && end > start) {
                        val selectedTerm = etReadingText.text.toString().substring(start, end)
                        showAnnotationDialog(selectedTerm, start, end)
                    }
                    mode?.finish()
                    return true
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {}
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

        tvTerm.text = "\"$term\""

        btnSave.setOnClickListener {
            val definition = etDefinition.text.toString().trim()
            val note = etNote.text.toString().trim()

            if (definition.isEmpty()) {
                etDefinition.error = "Definition cannot be empty"
                return@setOnClickListener
            }

            annotations.add(AnnotationItem(term, start, end, definition, note))

            val spannable = etReadingText.text as Spannable
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#FFF3CD")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            tvAnnotationCount.text = "${annotations.size} annotations added"

            dialog.dismiss()
        }

        dialog.show()
    }
}
