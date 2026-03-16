package org.fossify.notes.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.PROTECTION_NONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.notes.R
import org.fossify.notes.databinding.DialogNewNoteBinding
import org.fossify.notes.extensions.config
import androidx.activity.ComponentActivity
import org.fossify.notes.helpers.LocationHelper
import org.fossify.notes.extensions.notesDB
import org.fossify.notes.models.Note
import org.fossify.notes.models.NoteType

class NewNoteDialog(val activity: Activity, title: String? = null, val setChecklistAsDefault: Boolean, callback: (note: Note) -> Unit) {
    init {
        val binding = DialogNewNoteBinding.inflate(activity.layoutInflater).apply {
            val defaultType = when {
                setChecklistAsDefault -> typeChecklist.id
                activity.config.lastCreatedNoteType == NoteType.TYPE_TEXT.value -> typeTextNote.id
                else -> typeChecklist.id
            }

            newNoteType.check(defaultType)
        }

        binding.lockedNoteTitle.setText(title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.new_note) { alertDialog ->
                    alertDialog.showKeyboard(binding.lockedNoteTitle)
                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.lockedNoteTitle.value
                        ensureBackgroundThread {
                            when {
                                newTitle.isEmpty() -> activity.toast(R.string.no_title)
                                activity.notesDB.getNoteIdWithTitle(newTitle) != null -> activity.toast(R.string.title_taken)
                                else -> {
                                    val type = if (binding.newNoteType.checkedRadioButtonId == binding.typeChecklist.id) {
                                        NoteType.TYPE_CHECKLIST
                                    } else {
                                        NoteType.TYPE_TEXT
                                    }

                                    activity.config.lastCreatedNoteType = type.value
                                    val newNote = Note(null, newTitle, "", type, "", PROTECTION_NONE, "")

                                    // If user opted into geotag-by-default and master toggle is enabled,
                                    // attempt to capture a single foreground location and attach it to the note.
                                    if (activity.config.geotagNotesByDefault && activity.config.locationAccess && activity is ComponentActivity) {
                                        // Prefer using MainActivity's registered launcher to request permissions safely.
                                        if (activity is org.fossify.notes.activities.MainActivity) {
                                            (activity as org.fossify.notes.activities.MainActivity).requestForegroundLocation { loc ->
                                                if (loc != null) {
                                                    newNote.latitude = loc.latitude
                                                    newNote.longitude = loc.longitude
                                                    newNote.capturedAt = System.currentTimeMillis()
                                                    activity.toast(R.string.geotag_captured)
                                                } else {
                                                    activity.toast(R.string.geotag_capture_failed)
                                                }
                                                ensureBackgroundThread {
                                                    callback(newNote)
                                                }
                                                alertDialog.dismiss()
                                            }
                                        } else {
                                            // Permission APIs and ActivityResult registration must happen on the main thread.
                                            activity.runOnUiThread {
                                                try {
                                                    val comp = activity
                                                    lateinit var helper: LocationHelper
                                                    helper = LocationHelper(comp) { granted ->
                                                        if (granted && helper.hasPermission()) {
                                                            val loc = helper.getLastKnownLocation(activity)
                                                            if (loc != null) {
                                                                newNote.latitude = loc.latitude
                                                                newNote.longitude = loc.longitude
                                                                newNote.capturedAt = System.currentTimeMillis()
                                                            }
                                                        }
                                                        // Keep callback invocation on background thread like before
                                                        ensureBackgroundThread {
                                                            callback(newNote)
                                                        }
                                                        alertDialog.dismiss()
                                                    }

                                                    // If we already have permission, avoid asking and proceed immediately
                                                    if (helper.hasPermission()) {
                                                        val loc = helper.getLastKnownLocation(activity)
                                                        if (loc != null) {
                                                            newNote.latitude = loc.latitude
                                                            newNote.longitude = loc.longitude
                                                            newNote.capturedAt = System.currentTimeMillis()
                                                        }
                                                        ensureBackgroundThread {
                                                            callback(newNote)
                                                        }
                                                        alertDialog.dismiss()
                                                    } else {
                                                        // Request permission; result handled in helper callback above
                                                        helper.requestPermission()
                                                    }
                                                } catch (ise: IllegalStateException) {
                                                    // Registering ActivityResultLauncher failed because lifecycle is resumed.
                                                    // Fallback: if permission already granted, read last-known location directly;
                                                    // otherwise proceed without requesting permission to avoid crash.
                                                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                        activity,
                                                        android.Manifest.permission.ACCESS_FINE_LOCATION
                                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                                    if (granted) {
                                                        try {
                                                            val lm = activity.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                                                            val providers = lm.getProviders(true)
                                                            var bestLocation: android.location.Location? = null
                                                            for (provider in providers) {
                                                                try {
                                                                    val l = lm.getLastKnownLocation(provider)
                                                                    if (l != null && (bestLocation == null || l.accuracy < bestLocation.accuracy)) {
                                                                        bestLocation = l
                                                                    }
                                                                } catch (_: SecurityException) {
                                                                    bestLocation = null
                                                                    break
                                                                }
                                                            }
                                                            if (bestLocation != null) {
                                                                newNote.latitude = bestLocation.latitude
                                                                newNote.longitude = bestLocation.longitude
                                                                newNote.capturedAt = System.currentTimeMillis()
                                                            }
                                                        } catch (_: Exception) {
                                                            // ignore and proceed without location
                                                        }
                                                    }

                                                    ensureBackgroundThread {
                                                        callback(newNote)
                                                    }
                                                    alertDialog.dismiss()
                                                }
                                            }
                                        }
                                    } else {
                                        callback(newNote)
                                        alertDialog.dismiss()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}
