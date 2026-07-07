package com.rama.aichat.data.model

import io.realm.kotlin.types.EmbeddedRealmObject
import org.mongodb.kbson.ObjectId

class ChatMessage : EmbeddedRealmObject {
    var id: String = ObjectId().toHexString()
    var content: String = ""
    var role: String = "user"
    var timestamp: Long = System.currentTimeMillis()
    var imagePath: String? = null
}
