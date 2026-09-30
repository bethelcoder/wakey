package com.example.wakey

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

class AlarmAdapter(
    private val context: Context,
    private var alarms: MutableList<AlarmModel>,
    private val onToggle: (AlarmModel, Boolean) -> Unit,
    private val onItemClick: (AlarmModel) -> Unit,
    private val onItemLongClick: (AlarmModel) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    fun updateData(newAlarms: List<AlarmModel>) {
        this.alarms.clear()
        this.alarms.addAll(newAlarms)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.bind(alarm)
    }

    override fun getItemCount(): Int = alarms.size

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAlarmLabel: TextView = itemView.findViewById(R.id.tvAlarmLabel)
        private val tvAlarmTime: TextView = itemView.findViewById(R.id.tvAlarmTime)
        private val tvAlarmAmPm: TextView = itemView.findViewById(R.id.tvAlarmAmPm)
        private val tvAlarmDetails: TextView = itemView.findViewById(R.id.tvAlarmDetails)
        private val switchAlarm: MaterialSwitch = itemView.findViewById(R.id.switchAlarm)

        private val chipMon: TextView = itemView.findViewById(R.id.chipMon)
        private val chipTue: TextView = itemView.findViewById(R.id.chipTue)
        private val chipWed: TextView = itemView.findViewById(R.id.chipWed)
        private val chipThu: TextView = itemView.findViewById(R.id.chipThu)
        private val chipFri: TextView = itemView.findViewById(R.id.chipFri)
        private val chipSat: TextView = itemView.findViewById(R.id.chipSat)
        private val chipSun: TextView = itemView.findViewById(R.id.chipSun)

        fun bind(alarm: AlarmModel) {
            tvAlarmLabel.text = alarm.label
            tvAlarmTime.text = alarm.getFormattedTime()
            tvAlarmAmPm.text = alarm.getAmPm()
            tvAlarmDetails.text = "PC Login • ${alarm.sound}"

            // Switch toggle (suppress listener during binding)
            switchAlarm.setOnCheckedChangeListener(null)
            switchAlarm.isChecked = alarm.isEnabled

            // Opacity for disabled alarms
            itemView.alpha = if (alarm.isEnabled) 1.0f else 0.5f

            switchAlarm.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
                itemView.alpha = if (isChecked) 1.0f else 0.5f
            }

            itemView.setOnClickListener {
                onItemClick(alarm)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(alarm)
                true
            }

            // Update Day chips
            updateDayChip(chipMon, alarm.days.contains(Calendar.MONDAY))
            updateDayChip(chipTue, alarm.days.contains(Calendar.TUESDAY))
            updateDayChip(chipWed, alarm.days.contains(Calendar.WEDNESDAY))
            updateDayChip(chipThu, alarm.days.contains(Calendar.THURSDAY))
            updateDayChip(chipFri, alarm.days.contains(Calendar.FRIDAY))
            updateDayChip(chipSat, alarm.days.contains(Calendar.SATURDAY))
            updateDayChip(chipSun, alarm.days.contains(Calendar.SUNDAY))
        }

        private fun updateDayChip(chip: TextView, isActive: Boolean) {
            if (isActive) {
                chip.setBackgroundResource(R.drawable.bg_day_chip_active)
                chip.setTextColor(ContextCompat.getColor(context, R.color.day_chip_active_text))
            } else {
                chip.setBackgroundResource(R.drawable.bg_day_chip_inactive)
                chip.setTextColor(ContextCompat.getColor(context, R.color.day_chip_inactive_text))
            }
        }
    }
}
