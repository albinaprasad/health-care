package com.example.healthcare.dataclasses

import com.google.gson.annotations.SerializedName

data class PrescriptionResponse(
    @SerializedName("prescriptionID") val prescriptionID: Int,
    @SerializedName("elderID")        val elderID: Int,
    @SerializedName("caretakerID")    val caretakerID: Int,
    @SerializedName("medicineName")   val medicineName: String,
    @SerializedName("dosage")         val dosage: String,
    @SerializedName("frequency")      val frequency: String,
    @SerializedName("prescriptionDate") val prescriptionDate: String,
    @SerializedName("notes")          val notes: String?,
    @SerializedName("isActive")       val isActive: Boolean
)
