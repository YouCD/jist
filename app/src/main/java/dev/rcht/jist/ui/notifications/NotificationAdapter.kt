package dev.rcht.jist.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter :
    ListAdapter<NotificationEntity, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: NotificationEntity) {
            binding.appName.text = notification.appName
            binding.title.text = notification.title
            binding.content.text = notification.content
            binding.timestamp.text = formatTimestamp(notification.timestamp)
            binding.packageName.text = "pkg: ${notification.packageName}"
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationEntity>() {
        override fun areItemsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity): Boolean =
            oldItem == newItem
    }
}
