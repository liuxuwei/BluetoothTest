package com.example.bluetoothtest

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var isComfortListen: Boolean = false
    private var currentCommand: String? = null
    private val MAX_PAYLOAD: Int = 16
    private lateinit var adapter: DeviceAdapter
    var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val TAG = "BlueTest"
    private var btList = arrayListOf<BluetoothDevice>()
    private var mScanning = false

    private val GATT_UUID = "-0000-1000-8000-00805f9b34fb"

    private val CSR_UUID = "-D102-11E1-9B23-00025B00A5A5"

    private val DESCRIPTOR_UUID = "00002902$GATT_UUID"

    private val SERVICE_ID = "00001100$CSR_UUID"

    private val COMMAND_ID = "00001101$CSR_UUID"

    private val RESPONSE_ID = "00001102$CSR_UUID"

    private val DATA_ID = "00001103$CSR_UUID"

    private var isCanScan = false

    val SOF = 0xFF.toByte()

    var bluetoothGatt: BluetoothGatt? = null


    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_COARCH_LOCATION = 3
        const val SCAN_PERIOD: Long = 5000
        const val OFFS_SOF = 0
        const val OFFS_VERSION = 1
        const val OFFS_FLAGS = 2
        const val OFFS_PAYLOAD_LENGTH = 3
        const val OFFS_VENDOR_ID = 4
        const val OFFS_VENDOR_ID_H = OFFS_VENDOR_ID
        const val OFFS_VENDOR_ID_L = OFFS_VENDOR_ID + 1
        const val OFFS_COMMAND_ID = 6
        const val OFFS_COMMAND_ID_H = OFFS_COMMAND_ID
        const val OFFS_COMMAND_ID_L = OFFS_COMMAND_ID + 1
        const val OFFS_PAYLOAD = 8
        const val PROTOCOL_VERSION = 1.toByte()
    }


    private val handler = Handler()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        checkAndRequestPermission()

        checkIfSupportBlueTooth()

        //执行设备发现前，必须查询已配对的设备集，确认所需要的设备是否处于已检测到的状态
        queryBondedDevices()

        initAdapter()

        setListener()
    }


    //开启线程发送数据
    inner class SendDataRunnable: Runnable{
        override fun run() {
            sendData()
        }

    }

    /**
     * 使用 WRITE_CHARACTERISTIC 向蓝牙发送数据
     */
    private fun sendData(): Boolean {
        var gattService: BluetoothGattService? = null
        if (bluetoothGatt != null) {
            Log.d(TAG, "=======1 ")
            gattService = bluetoothGatt!!.getService(UUID.fromString(SERVICE_ID))

        }

        if (gattService == null) {
            Log.d(TAG, "=======2  ")
            return false
        }

        val gattCharacteristic = gattService.getCharacteristic(UUID.fromString(COMMAND_ID))
        if (gattCharacteristic == null) {
            Log.d(TAG, "=== characteristic 为空")
            return false
        }


        var byteArr = byteArrayOf(0x01, 0x42)

        when (currentCommand) {
            "setMode" -> byteArr = if (isComfortListen) byteArrayOf(0x10,0x10) else byteArrayOf(0x10,0x11)
            "getMode" -> byteArr = byteArrayOf(0x01,0x00)
            "hearMakeUp" -> byteArr = byteArrayOf(0x01, 0x10)
            "compareCheck" -> byteArr = byteArrayOf(0x01, 0x40)
            "setLanguage" -> byteArr = byteArrayOf(0x01, 0x42)
        }


        val myFrame = buildFrame(0x000a, 0x0207, byteArr, byteArr.size,0)

        Log.d(TAG, "拼装完成的frame ====== ${myFrame.contentToString()}")


        gattCharacteristic.value = myFrame

        gattCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        return bluetoothGatt!!.writeCharacteristic(gattCharacteristic)
    }

    /**
     * 接收蓝牙数据改变时的数据
     */
    private fun receiveCallback() {
        if (bluetoothGatt == null) {
            return
        }

        val gattService = bluetoothGatt?.getService(UUID.fromString(SERVICE_ID))

        val gattCharacteristicResponse = gattService?.getCharacteristic(UUID.fromString(RESPONSE_ID))

        val descriptorResponse = gattCharacteristicResponse?.getDescriptor(UUID.fromString(DESCRIPTOR_UUID))
        if (descriptorResponse != null) {
            descriptorResponse.value = (BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (bluetoothGatt!!.writeDescriptor(descriptorResponse)) {
                Log.d(TAG, "RESPONSE接收指令特征值不为空-----")
                bluetoothGatt!!.setCharacteristicNotification(gattCharacteristicResponse, true)
            }
        }


        bluetoothGatt!!.readCharacteristic(gattCharacteristicResponse)
    }

    /**
     * 构建发送的数据包
     */
    private fun buildFrame(
        vendor_id: Int,
        command_id: Int,
        payload: ByteArray,
        payload_length: Int,
        flags: Byte
    ): ByteArray {
        if (payload_length > MAX_PAYLOAD) {
            Log.e(TAG, "payload is too long")
        }

        val packet_length = payload_length + OFFS_PAYLOAD
        val data = ByteArray(packet_length)

        data[OFFS_SOF] = SOF
        data[OFFS_VERSION] = PROTOCOL_VERSION
        data[OFFS_FLAGS] = flags
        data[OFFS_PAYLOAD_LENGTH] = payload_length.toByte()
        data[OFFS_VENDOR_ID_H] = 0x00
        data[OFFS_VENDOR_ID_L] = 0x0a
        data[OFFS_COMMAND_ID_H] = 0x02
        data[OFFS_COMMAND_ID_L] = 0x07

        for (idx in 0 until payload_length)
            data[idx + OFFS_PAYLOAD] = payload[idx]





        return data
    }


    /**
     * 扫描Low Energy蓝牙设备
     */
    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed({
                    mScanning = false
                    bluetoothAdapter?.stopLeScan(leScanCallback)
                    bluetoothAdapter?.cancelDiscovery()
                    Toast.makeText(this, "结束扫描", Toast.LENGTH_SHORT).show()
                }, SCAN_PERIOD)
                mScanning = true
                bluetoothAdapter?.startLeScan(leScanCallback)
                Toast.makeText(this, "开始扫描", Toast.LENGTH_SHORT).show()
            }
            false -> {
                mScanning = false
                bluetoothAdapter?.stopLeScan(leScanCallback)
            }
        }
    }

    //ble蓝牙查找设备回调
    private val leScanCallback =
        BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
            val deviceName = device?.name
            val deviceAddress = device?.address
            runOnUiThread {
                if (deviceName != null && !btList.contains(device)) {
                    btList.add(device)
                    adapter.notifyDataSetChanged()
                }
                Log.d(TAG, "find device ---- $deviceName --- $deviceAddress")
            }
        }


    /**
     * 普通蓝牙广播接收器
     */
    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device.name
                    val deviceAddress = device.address
                    if (deviceName != null && !btList.contains(device)) {
                        btList.add(device)
                    }
                    adapter.notifyDataSetChanged()
                    Log.d(TAG, "find device ---- $deviceName --- $deviceAddress")
                }

            }
        }
    }

    private fun setListener() {
        btnSetMode.setOnClickListener(this)
        btnGetMode.setOnClickListener(this)
        btnHearMakeup.setOnClickListener(this)
        btnCompareCheck.setOnClickListener(this)
        btnSetLanguage.setOnClickListener(this)
        btnScan.setOnClickListener(this)
        btnStopScan.setOnClickListener(this)
        btnRegisterCallback.setOnClickListener(this)


        btListView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                var address = btList[position].address
                Log.d(TAG, "select bluetooth Address: $address")

                if (btList[position].bondState != BluetoothDevice.BOND_BONDED) {
                    btList[position].createBond()
                    Log.d(TAG, " 执行配对 ")
                }

                //连接蓝牙GATT服务
                bluetoothGatt = btList[position].connectGatt(
                    applicationContext,
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            }
    }

    //ble设备连接后的回调
    var bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "状态改变: $status ---- $newState")
            if (status == 133) {
                Log.d(TAG, "设备初始连接为133，需要重新进行扫描")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "设备初始连接为133，需要重新进行扫描", Toast.LENGTH_SHORT)
                        .show()
                }
                bluetoothGatt?.close()
                runOnUiThread {
                    btList.clear()
                    adapter.notifyDataSetChanged()
                }
                scanLeDevice(true)
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "设备已连接")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "设备已连接", Toast.LENGTH_SHORT).show()
                }
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d(TAG, "设备正在进行连接...")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "设备连接已断开")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "连接已断开", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            Log.d(TAG, "onReadRemoteRssi ---- $rssi")
        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d(
                TAG,
                "onCharacteristicRead  uuid: ${characteristic?.uuid} ===== ${Arrays.toString(
                    characteristic?.value
                )}"
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            Log.d(TAG, "写入成功: ==== ${Arrays.toString(characteristic?.value)}")
            Log.d(
                TAG,
                "onCharacteristicWrite ---- uuid: ${characteristic?.uuid}  ---- value: ${characteristic?.value.toString()}---- status: $status"
            )
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "onServicesDiscovered ---- $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendBleNotification()
            }

        }


        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            Log.d(TAG, "onPhyUpdate")

        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "onMtuChanged")
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            Log.d(TAG, "onReliableWriteCompleted")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.d(TAG, "onDescriptorWrite -- ${descriptor?.uuid}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.d(
                TAG,
                "onCharacteristicChanged --- ${characteristic?.uuid} ==== ${Arrays.toString(
                    characteristic?.value
                )}"
            )
            super.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            Log.d(TAG, "onDescriptorRead")
        }

        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            Log.d(TAG, "onPhyRead")
        }
    }

    //发现服务后调用
    private fun sendBleNotification() {
        if (bluetoothGatt == null) {
            return
        }

        for (gattService in bluetoothGatt!!.services) {
//            Log.d(TAG, "发现service ====== $gattService")
//            Log.d(TAG, "${gattService.uuid} -----  ${gattService.includedServices}")
            for (characteristic in gattService.characteristics) {
                Log.d(TAG, "特征值： ==== ${characteristic.uuid}")
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    Log.e(TAG, "${characteristic.uuid}的属性为:  可读");
                }

                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    Log.e(TAG, "${characteristic.uuid}的属性为:  可写");
                }

                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    Log.e(TAG, "${characteristic.uuid}的属性为:  有通知");
                }


            }
        }
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btnSetMode -> {
                currentCommand = "setMode"
                isComfortListen = !isComfortListen
            }
            R.id.btnGetMode -> currentCommand = "getMode"
            R.id.btnHearMakeup -> currentCommand = "hearMakeup"
            R.id.btnCompareCheck -> currentCommand = "compareCheck"
            R.id.btnSetLanguage -> currentCommand = "setLanguage"
            R.id.btnScan -> {
                btList.clear()
                adapter.notifyDataSetChanged()
                scanLeDevice(true)
                bluetoothAdapter?.startDiscovery()
            }
            R.id.btnStopScan -> {
                scanLeDevice(false)
                bluetoothAdapter?.cancelDiscovery()
            }
            R.id.btnRegisterCallback -> receiveCallback()
        }
        Thread(SendDataRunnable()).start()
    }

    /**
     * 初始化列表适配器
     */
    private fun initAdapter() {
        adapter = DeviceAdapter(applicationContext, btList)
        btListView.adapter = adapter
    }


    /**
     * 查询已经绑定的蓝牙设备
     */
    private fun queryBondedDevices() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach {
            val deviceName = it.name
            val deviceAddress = it.address
            Log.d(TAG, "device info： -- $deviceName === $deviceAddress")
        }
    }


    /**
     * 检查手机是否支持蓝牙，  如果支持，检查是否蓝牙已经打开
     */
    private fun checkIfSupportBlueTooth() {
        if (bluetoothAdapter == null) {
            Log.d(TAG, "don't support bluetooth")
        } else {
            Log.d(TAG, "support bluetooth")
            checkIfOpenBlueTooth()
        }

    }

    /**
     * 开启蓝牙
     */
    private fun checkIfOpenBlueTooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "enable bt success")
                    isCanScan = true
                    Toast.makeText(this, "蓝牙开启成功", Toast.LENGTH_SHORT).show()
                }
                Activity.RESULT_CANCELED -> Log.d(TAG, "enable bt canceled")
            }
        }
    }


    /**
     * 检查并申请所需要的权限
     */
    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED
                || this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),

                    REQUEST_COARCH_LOCATION
                )
            }
        }
    }

    /**
     * 申请权限回调
     */
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
        if (bluetoothGatt != null) {
            bluetoothGatt!!.disconnect()
        }
    }


}
