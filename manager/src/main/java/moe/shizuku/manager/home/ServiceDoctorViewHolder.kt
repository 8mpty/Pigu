package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeServiceDoctorBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class ServiceDoctorViewHolder(
    binding: HomeServiceDoctorBinding,
    root: View,
) : BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR = Creator<Any?> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeServiceDoctorBinding.inflate(inflater, outer.root, true)
            ServiceDoctorViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener {
            it.context.startActivity(Intent(it.context, ServiceDoctorActivity::class.java))
        }
    }
}
