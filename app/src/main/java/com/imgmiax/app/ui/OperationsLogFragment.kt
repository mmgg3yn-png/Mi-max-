package com.imgmiax.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.imgmiax.app.R
import com.imgmiax.app.utils.LogEntry
import com.imgmiax.app.utils.OperationLogger

class OperationsLogFragment : Fragment() {

    private lateinit var rvLogs: RecyclerView
    private lateinit var btnClearLogs: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_operations_log, container, false)
        rvLogs = v.findViewById(R.id.rvLogList)
        btnClearLogs = v.findViewById(R.id.btnClearLogs)

        rvLogs.layoutManager = LinearLayoutManager(context)
        refreshLogs()

        btnClearLogs.setOnClickListener {
            OperationLogger.clear()
            refreshLogs()
        }

        return v
    }

    private fun refreshLogs() {
        val list = OperationLogger.getAllLogs()
        rvLogs.adapter = LogAdapter(list)
    }

    class LogAdapter(private val items: List<LogEntry>) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvLogTitle)
            val tvDesc: TextView = v.findViewById(R.id.tvLogDesc)
            val tvTime: TextView = v.findViewById(R.id.tvLogTime)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
            return ViewHolder(v)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = "${if (item.isSuccess) "✅" else "❌"} ${item.title}"
            holder.tvDesc.text = item.description
            holder.tvTime.text = item.timestamp
        }
        override fun getItemCount(): Int = items.size
    }
}
