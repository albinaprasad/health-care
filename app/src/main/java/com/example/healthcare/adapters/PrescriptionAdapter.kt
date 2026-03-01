package com.example.healthcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthcare.dataclasses.PrescriptionResponse
import com.example.healthcare.databinding.ItemPrescriptionBinding

class PrescriptionAdapter(
    private val list: List<PrescriptionResponse>
) : RecyclerView.Adapter<PrescriptionAdapter.PrescriptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrescriptionViewHolder {
        val binding = ItemPrescriptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PrescriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PrescriptionViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount() = list.size

    inner class PrescriptionViewHolder(
        private val binding: ItemPrescriptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PrescriptionResponse) {
            binding.tvMedicineName.text = item.medicineName
            binding.tvDosage.text      = "Dosage: ${item.dosage}"
            binding.tvFrequency.text   = item.frequency
            binding.tvNotes.text       = item.notes?.takeIf { it.isNotBlank() } ?: "No notes"

            // Format date: take the date portion before 'T' if ISO format
            val dateDisplay = item.prescriptionDate.substringBefore("T")
            binding.tvDate.text = dateDisplay

            // Dim inactive prescriptions
            binding.root.alpha = if (item.isActive) 1f else 0.5f
        }
    }
}
