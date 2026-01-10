package com.example.healthcare.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthcare.R
import com.example.healthcare.databinding.ActivityMainScreenBinding
import com.example.healthcare.databinding.TimeItemBinding
import com.example.healthcare.dataclasses.calenderDay

class CalendarAdapter(
    private val list: List<calenderDay>
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    private var selectedPos = list.indexOfFirst { it.isToday == true }

    inner class VH(val binding: TimeItemBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = TimeItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = list[position]

        with(holder.binding) {

            dayTv.text = item.dayName
            dateTv.text = item.date.toString()



            if (position == selectedPos) {
                dateTv.setBackgroundResource(R.drawable.round_green_circle)
                dateTv.setTextColor(Color.WHITE)
                dateTv.isSelected = true
                root.setBackgroundResource(R.drawable.curved_green_background)
            } else {

                dateTv.setTextColor(Color.BLACK)
                dateTv.isSelected=false
               root.setBackgroundResource(R.drawable.curved_grey_background)
            }

        }
    }
}

