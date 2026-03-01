package com.example.healthcare.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.healthcare.R
import com.example.healthcare.dataclasses.PrescriptionResponse
import com.example.healthcare.databinding.ItemDateHeaderBinding
import com.example.healthcare.databinding.ItemPrescriptionBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Sealed class representing either a date‑header or a prescription card.
 */
sealed class PrescriptionListItem {
    data class Header(val dateLabel: String) : PrescriptionListItem()
    data class Item(val prescription: PrescriptionResponse) : PrescriptionListItem()
}

class PrescriptionAdapter(
    prescriptions: List<PrescriptionResponse>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM   = 1

        /** ISO → readable format, e.g. "01 Mar 2026" */
        private val isoFormat     = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

        fun formatDate(iso: String): String {
            return try {
                val date = isoFormat.parse(iso.substringBefore("T"))
                if (date != null) displayFormat.format(date) else iso
            } catch (_: Exception) { iso }
        }
    }

    /** Flat list of headers + items, built from the raw prescriptions. */
    private val items: List<PrescriptionListItem>

    init {
        // Group by date (take portion before 'T'), sort newest first
        val grouped = prescriptions
            .groupBy { it.prescriptionDate.substringBefore("T") }
            .toSortedMap(compareByDescending { it })

        val result = mutableListOf<PrescriptionListItem>()
        for ((dateKey, meds) in grouped) {
            result.add(PrescriptionListItem.Header(formatDate(dateKey)))
            meds.sortedBy { it.medicineName }.forEach { med ->
                result.add(PrescriptionListItem.Item(med))
            }
        }
        items = result
    }

    // ─── View‑type routing ──────────────────────────────────────────────────

    override fun getItemViewType(position: Int) = when (items[position]) {
        is PrescriptionListItem.Header -> TYPE_HEADER
        is PrescriptionListItem.Item   -> TYPE_ITEM
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            DateHeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            PrescriptionViewHolder(ItemPrescriptionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = items[position]) {
            is PrescriptionListItem.Header -> (holder as DateHeaderViewHolder).bind(entry)
            is PrescriptionListItem.Item   -> (holder as PrescriptionViewHolder).bind(entry.prescription)
        }
    }

    // ─── ViewHolders ────────────────────────────────────────────────────────

    inner class DateHeaderViewHolder(
        private val binding: ItemDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: PrescriptionListItem.Header) {
            binding.tvDateHeader.text = header.dateLabel
        }
    }

    inner class PrescriptionViewHolder(
        private val binding: ItemPrescriptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PrescriptionResponse) {
            val ctx = binding.root.context

            // Medicine name
            binding.tvMedicineName.text = item.medicineName

            // Prescription date subtitle
            val dateDisplay = formatDate(item.prescriptionDate.substringBefore("T"))
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
