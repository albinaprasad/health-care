package com.example.healthcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthcare.databinding.WelcomescreengridBinding
import com.example.healthcare.dataclasses.GridItem

class WelcomeScreenAdapter(
    val list: List<GridItem>,
    private val onItemClick: (GridItem) -> Unit = {}
) : RecyclerView.Adapter<WelcomeScreenAdapter.GridViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = WelcomescreengridBinding.inflate(inflater, parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val item = list[position]
        with(holder.binding) {
            RimageView.setImageResource(item.icon)
            RtxtDec.text = item.title
            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount() = list.size

    inner class GridViewHolder(val binding: WelcomescreengridBinding) :
        RecyclerView.ViewHolder(binding.root)
}
