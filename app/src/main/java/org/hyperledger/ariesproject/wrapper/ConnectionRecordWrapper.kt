package org.hyperledger.ariesproject.wrapper

import android.os.Parcel
import android.os.Parcelable

import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState

data class ConnectionRecordWrapper(
    val id: String?,
    val state: ConnectionState,
    val role: ConnectionRole,
    //val didDoc: DidDoc,
    val did: String,
    //val theirDidDoc: DidDoc?,
    val theirDid: String?,
    val theirLabel: String?,
//    val invitation: ConnectionInvitationMessage?,
//    val outOfBandInvitation: OutOfBandInvitation?,
    val alias: String?,
    val autoAcceptConnection: Boolean?,
    val imageUrl: String?,
    val multiUseInvitation: Boolean,
    val threadId: String?,
    val mediatorId: String?,
    val errorMessage: String?
) : Parcelable {

    // Constructor to convert from ConnectionRecord
    constructor(connectionRecord: ConnectionRecord) : this(
        id = connectionRecord.id,
        state = connectionRecord.state,
        role = connectionRecord.role,
        did = connectionRecord.did,
        theirDid = connectionRecord.theirDid,
        theirLabel = connectionRecord.theirLabel,
        alias = connectionRecord.alias,
        autoAcceptConnection = connectionRecord.autoAcceptConnection,
        imageUrl = connectionRecord.imageUrl,
        multiUseInvitation = connectionRecord.multiUseInvitation,
        threadId = connectionRecord.threadId,
        mediatorId = connectionRecord.mediatorId,
        errorMessage = connectionRecord.errorMessage
    )

    // Parcelable Implementation
    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        state = ConnectionState.valueOf(parcel.readString()!!),
        role = ConnectionRole.valueOf(parcel.readString()!!),
        did = parcel.readString()!!,
        theirDid = parcel.readString(),
        theirLabel = parcel.readString(),
        alias = parcel.readString(),
        autoAcceptConnection = parcel.readValue(Boolean::class.java.classLoader) as? Boolean,
        imageUrl = parcel.readString(),
        multiUseInvitation = parcel.readByte() != 0.toByte(),
        threadId = parcel.readString(),
        mediatorId = parcel.readString(),
        errorMessage = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(state.name)
        parcel.writeString(role.name)
        parcel.writeString(did)
        parcel.writeString(theirDid)
        parcel.writeString(theirLabel)
        parcel.writeString(alias)
        parcel.writeValue(autoAcceptConnection)
        parcel.writeString(imageUrl)
        parcel.writeByte(if (multiUseInvitation) 1 else 0)
        parcel.writeString(threadId)
        parcel.writeString(mediatorId)
        parcel.writeString(errorMessage)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ConnectionRecordWrapper> {
        override fun createFromParcel(parcel: Parcel) = ConnectionRecordWrapper(parcel)
        override fun newArray(size: Int): Array<ConnectionRecordWrapper?> = arrayOfNulls(size)
    }


    fun printFields(): String {
        return "ID: ${this.id} \n Thread ID: ${this.threadId} \n Mediator ID: ${this.mediatorId} \n Label: ${this.theirLabel} \n Role: ${this.role} \n Did: ${this.did}"
    }
}