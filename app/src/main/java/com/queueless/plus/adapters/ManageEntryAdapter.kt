package com.queueless.plus.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.queueless.plus.databinding.ItemManageEntryBinding
import com.queueless.plus.models.QueueEntry

class ManageEntryAdapter(
    private val onServed: (QueueEntry) -> Unit,
    private val onRemove: (QueueEntry) -> Unit,
    private val onPreparing: (QueueEntry) -> Unit,
    private val onReady: (QueueEntry) -> Unit
) : ListAdapter<QueueEntry, ManageEntryAdapter.ManageEntryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageEntryViewHolder {
        val binding = ItemManageEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ManageEntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ManageEntryViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class ManageEntryViewHolder(
        private val binding: ItemManageEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: QueueEntry, position: Int) {
            binding.tvPosition.text = "#$position"
            binding.tvUserName.text = entry.userName

            val hasOrder = entry.orderDetails.isNotBlank() || entry.orderId.isNotBlank()
            val orderText = if (entry.orderDetails.isBlank()) "Not placed" else entry.orderDetails
            val statusText = entry.orderStatus.replaceFirstChar { it.uppercase() }

            binding.tvOrder.text = "Order: $orderText"
            binding.tvStatus.text = "Status: $statusText"
            binding.btnServed.text = "Completed"

            binding.btnPreparing.visibility = if (hasOrder) View.VISIBLE else View.GONE
            binding.btnReady.visibility = if (hasOrder) View.VISIBLE else View.GONE
            binding.btnServed.visibility = if (hasOrder) View.VISIBLE else View.GONE

            binding.btnPreparing.setOnClickListener { onPreparing(entry) }
            binding.btnReady.setOnClickListener { onReady(entry) }
            binding.btnServed.setOnClickListener { onServed(entry) }
            binding.btnRemove.setOnClickListener { onRemove(entry) }

            when (entry.orderStatus) {
                QueueEntry.ORDER_WAITING -> {
                    binding.btnPreparing.isEnabled = hasOrder
                    binding.btnReady.isEnabled = false
                    binding.btnServed.isEnabled = false
                }
                QueueEntry.ORDER_PREPARING -> {
                    binding.btnPreparing.isEnabled = false
                    binding.btnReady.isEnabled = hasOrder
                    binding.btnServed.isEnabled = false
                }
                QueueEntry.ORDER_READY -> {
                    binding.btnPreparing.isEnabled = false
                    binding.btnReady.isEnabled = false
                    binding.btnServed.isEnabled = hasOrder
                }
                QueueEntry.ORDER_COMPLETED -> {
                    binding.btnPreparing.isEnabled = false
                    binding.btnReady.isEnabled = false
                    binding.btnServed.isEnabled = false
                }
                else -> {
                    binding.btnPreparing.isEnabled = hasOrder
                    binding.btnReady.isEnabled = false
                    binding.btnServed.isEnabled = false
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<QueueEntry>() {
        override fun areItemsTheSame(old: QueueEntry, new: QueueEntry): Boolean {
            return old.entryId == new.entryId
        }

        override fun areContentsTheSame(old: QueueEntry, new: QueueEntry): Boolean {
            return old == new
        }
    }
}
