package com.example.meeting.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.meeting.DataModel.FileItem
import com.example.meeting.databinding.ItemFileBinding

class FileAdapter(
    private val files: List<FileItem>,
    private val onFileClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>()
{
    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(file: FileItem) {
            binding.tvFileName.text = file.name
          //  binding.tvFileDate.text = file.createdAt
            binding.root.setOnClickListener {
                onFileClick(file)
            }
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }
    override fun onBindViewHolder(
        holder: FileViewHolder,
        position: Int
    ) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int {
        return files.size
    }



}