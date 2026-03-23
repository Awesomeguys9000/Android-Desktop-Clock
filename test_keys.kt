package com.dashboard.android

import android.view.KeyEvent
import android.content.DialogInterface
import android.widget.EditText
import android.app.Dialog
import android.os.Bundle

class TestKeys {
    fun setup(dialog: Dialog?, inputReply: EditText) {
        dialog?.setOnKeyListener(DialogInterface.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.deviceId > 0 && event.isPrintingKey) {
                if (!inputReply.hasFocus()) {
                    inputReply.requestFocus()
                    val charCode = event.getUnicodeChar(event.metaState)
                    if (charCode != 0) {
                        inputReply.append(charCode.toChar().toString())
                        inputReply.setSelection(inputReply.text.length)
                        return@OnKeyListener true
                    }
                }
            }
            false
        })

        inputReply.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.deviceId > 0) { // Physical keyboard
                    if (event.isCtrlPressed) {
                        val start = inputReply.selectionStart
                        val end = inputReply.selectionEnd
                        inputReply.text?.replace(Math.min(start, end), Math.max(start, end), "\n", 0, 1)
                        return@setOnKeyListener true
                    } else {
                        // sendReply()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }
}
