package moe.shizuku.manager.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.ServiceDoctorItemBinding
import rikka.core.res.resolveColor

enum class DoctorLevel {
    OK,
    WARN,
    ERROR,
    INFO,
}

data class ServiceDoctorItem(
    val title: String,
    val summary: String,
    val level: DoctorLevel,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
)

class ServiceDoctorAdapter : RecyclerView.Adapter<ServiceDoctorAdapter.ViewHolder>() {

    private var items: List<ServiceDoctorItem> = emptyList()

    fun submit(list: List<ServiceDoctorItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ServiceDoctorItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class ViewHolder(private val binding: ServiceDoctorItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServiceDoctorItem) {
            val context = binding.root.context
            binding.title.text = item.title
            binding.summary.text = item.summary

            val (icon, colorAttr) = when (item.level) {
                DoctorLevel.OK -> R.drawable.ic_server_ok_24dp to android.R.attr.colorPrimary
                DoctorLevel.WARN -> R.drawable.ic_warning_24 to android.R.attr.colorAccent
                DoctorLevel.ERROR -> R.drawable.ic_server_error_24dp to android.R.attr.colorError
                DoctorLevel.INFO -> R.drawable.ic_outline_info_24 to android.R.attr.textColorSecondary
            }
            binding.icon.setImageResource(icon)
            binding.icon.imageTintList = ColorStateList.valueOf(
                context.theme.resolveColor(colorAttr)
            )

            if (item.actionLabel != null && item.onAction != null) {
                binding.action.visibility = View.VISIBLE
                binding.action.text = item.actionLabel
                binding.action.setOnClickListener { item.onAction.invoke() }
            } else {
                binding.action.visibility = View.GONE
                binding.action.setOnClickListener(null)
            }

            binding.root.isClickable = item.onClick != null
            binding.root.setOnClickListener { item.onClick?.invoke() }
            binding.action.isFocusable = true
        }
    }
}
