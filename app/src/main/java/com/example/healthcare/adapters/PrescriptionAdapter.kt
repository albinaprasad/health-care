package com.example.healthcare.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.healthcare.R
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
            val ctx = binding.root.context

            // Medicine name
            binding.tvMedicineName.text = item.medicineName

            // Prescription date subtitle
            val dateDisplay = item.prescriptionDate.substringBefore("T")
            binding.tvPrescribedBy.text = "Prescribed on $dateDisplay"
            binding.tvDate.text = dateDisplay

            // Dosage & frequency
            binding.tvDosage.text    = item.dosage
            binding.tvFrequency.text = item.frequency

            // Notes — hide entire row if empty
            val notes = item.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                binding.layoutNotes.visibility = View.VISIBLE
                binding.tvNotes.text = notes
            } else {
                binding.layoutNotes.visibility = View.GONE
            }

            // Status badge
            if (item.isActive) {
                binding.tvStatusBadge.text = "Active"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(ctx, R.color.primary_blue))
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
                binding.root.alpha = 1f
            } else {
                binding.tvStatusBadge.text = "Inactive"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(ctx, R.color.button_snooze_color))
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
                binding.root.alpha = 0.7f
            }
        }
    }
}
