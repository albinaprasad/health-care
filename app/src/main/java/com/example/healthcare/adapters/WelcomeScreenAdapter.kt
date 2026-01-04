package com.example.healthcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.healthcare.databinding.WelcomescreengridBinding
import com.example.healthcare.dataclasses.GridItem

class WelcomeScreenAdapter(val list: List<GridItem>): RecyclerView.Adapter<WelcomeScreenAdapter.GridViewHolder>(){
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GridViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = WelcomescreengridBinding.inflate(inflater, parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: GridViewHolder,
        position: Int
    ) {
      with(holder.binding){
         RimageView.setImageResource(list[position].icon)
          RtxtDec.text = list[position].title
      }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class GridViewHolder(val binding: WelcomescreengridBinding) : RecyclerView.ViewHolder(binding.root){

    }

}