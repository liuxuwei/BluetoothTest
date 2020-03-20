package com.example.bluetoothtest

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: ArrayAdapter<String>
    var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val TAG = "BlueTest"
    private var btList = arrayListOf<String>()


    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_COARCH_LOCATION = 3
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)


        checkAndRequestPermission()

        checkIfSupportBlueTooth()


        checkIfOpenBlueTooth()


        //执行设备发现前，必须查询已配对的设备集，确认所需要的设备是否处于已检测到的状态
        queryBondedDevices()

        initAdapter()




        bluetoothAdapter?.startDiscovery()


        setListener()
    }

    private fun setListener() {
        btListView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                var address = btList[position].split("---")[1]
                Log.d(TAG, "select bluetooth Address: $address")
            }
    }

    private fun initAdapter() {
        adapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, btList)
        btListView.adapter = adapter

    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_COARCH_LOCATION
                )
            }
        }
    }


    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device.name
                    val deviceAddress = device.address
                    btList.add("$deviceName ---$deviceAddress")
                    adapter.notifyDataSetChanged()
                    Log.d(TAG, "find device ---- $deviceName --- $deviceAddress")
                }

            }
        }
    }

    private fun queryBondedDevices() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach {
            val deviceName = it.name
            val deviceAddress = it.address
            Log.d(TAG, "device info： -- $deviceName === $deviceAddress")
        }
    }

    private fun checkIfOpenBlueTooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT)
        }
    }


    private fun checkIfSupportBlueTooth() {
        if (bluetoothAdapter == null) {
            Log.d(TAG, "don't support bluetooth")
        } else {
            Log.d(TAG, "support bluetooth")
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            when (resultCode) {
                Activity.RESULT_OK -> Log.d(TAG, "enable bt success")
                Activity.RESULT_CANCELED -> Log.d(TAG, "enable bt canceled")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_COARCH_LOCATION -> {
                if (grantResults[0] == PERMISSION_GRANTED) {
                    Log.d(TAG, "request permission success")
                }
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }


    private inner class AcceptThread : Thread() {

//        private val mServerSocket: BluetoothServerSocket? by lazy {
////            bluetoothAdapter?.listenUsingRfcommWithServiceRecord(NAME, )
//        }
    }
}
