package com.rama.aichat.data.model

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ChatSession : RealmObject {
    @PrimaryKey
    var id: String = ObjectId().toHexString()
    var title: String = "New Chat"
    var createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = System.currentTimeMillis()
    var messages: RealmList<ChatMessage> = realmListOf()
}
