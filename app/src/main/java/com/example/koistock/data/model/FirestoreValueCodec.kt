package com.example.koistock.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

object FirestoreValueCodec {
    fun serverTimestamp(): Any = FieldValue.serverTimestamp()

    fun readEpochMillis(raw: Any?): Long {
        return when (raw) {
            null -> 0L
            is Timestamp -> raw.toDate().time
            is Number -> raw.toLong()
            else -> error("Unsupported Firestore timestamp value: ${raw::class.qualifiedName}")
        }
    }
}
