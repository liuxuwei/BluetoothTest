package com.example.bluetoothtest

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class DeviceAdapter(private val context: Context,
                    private val mlist: List<BluetoothDevice>): BaseAdapter() {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = LayoutInflater.from(context).inflate(R.layout.layout_device, parent, false)
        view.findViewById<TextView>(R.id.tvDeviceName).text = mlist[position].name
        view.findViewById<TextView>(R.id.tvDeviceAddress).text = mlist[position].address
        when (mlist[position].type) {
            1 -> view.findViewById<TextView>(R.id.tvDeviceType).text = "DEVICE_TYPE_CLASSIC"
            2 -> view.findViewById<TextView>(R.id.tvDeviceType).text = "DEVICE_TYPE_LE"
            3 -> view.findViewById<TextView>(R.id.tvDeviceType).text = "DEVICE_TYPE_DUAL"
        }
        return view
    }

    override fun getItem(position: Int): Any {
        return mlist[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return mlist.size
    }
}