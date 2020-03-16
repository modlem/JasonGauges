package com.example.jasongauges

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

import kotlinx.android.synthetic.main.activity_main.*
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    var gauge1:ArcGauge ?= null
    var connectedDevice:BluetoothDevice ?= null
    var btRfcommSocket: BluetoothSocket ?= null
    //var btOutStream:OutputStream ?= null
    //var btInStream:InputStream ?= null
    var btRcvThread:Thread ?= null
    var txtView:TextView ?= null
    var theDriver: LoganDriver ?= null
    val monitorQueue = ArrayDeque<ByteArray>()

    private var bluetoothAdapter: BluetoothAdapter ?= BluetoothAdapter.getDefaultAdapter()
    /*private val profileListener = object : BluetoothProfile.ServiceListener {

        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile..HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
            }
        }
    }*/

    private fun cleanupBtConnect() {
        if(btRcvThread != null) (btRcvThread as Thread).interrupt()
        //if(btOutStream != null) (btOutStream as OutputStream).close()
        //if(btInStream != null) (btInStream as InputStream).close()
        if(btRfcommSocket != null) (btRfcommSocket as BluetoothSocket).close()
        //btOutStream = null
        //btInStream = null
        btRfcommSocket = null
        btRcvThread = null
    }

    fun textAdd(str: String) {
        if(txtView != null) {
            runOnUiThread {
                (txtView as TextView).append("\n" + str)
            }
        }
    }

    private fun beginReceive() {
        btRcvThread = Thread(Runnable {
            while(!Thread.currentThread().isInterrupted) {
                try {
                    /*val inAvailable = (btInStream as InputStream).available()
                    if(inAvailable > 0) {
                        val inByteArr = ByteArray(inAvailable)
                        (btInStream as InputStream).read(inByteArr)
                        textAdd(String(inByteArr))*/
                    while(monitorQueue.isNotEmpty()) {
                        textAdd(String(monitorQueue.remove()))
                    }
                } catch (e: Exception) {

                }
            }
        })

        (btRcvThread as Thread).start()
    }

    override fun onDestroy() {
        cleanupBtConnect()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        gauge1 = findViewById(R.id.arcGauge2)
        txtView = findViewById(R.id.textView2)
        (txtView as TextView).movementMethod = ScrollingMovementMethod()
        val connectButton = findViewById(R.id.button) as Button
        val testButton = findViewById(R.id.button2) as Button

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        connectButton.setOnClickListener { view ->
            if(bluetoothAdapter == null) {
                Snackbar.make(view, "Bluetooth adapter is not available at the moment.", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }
            else {
                if(!(bluetoothAdapter as BluetoothAdapter).isEnabled()) {
                    startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                else {
                    val pairedDevices: Set<BluetoothDevice> = (bluetoothAdapter as BluetoothAdapter).bondedDevices
                    if(pairedDevices.size > 0) {
                        val dialogBuilder = AlertDialog.Builder(this)
                        val deviceList = ArrayList<BluetoothDevice>()
                        val deviceNameList = ArrayList<String>()

                        dialogBuilder.setTitle(R.string.constant_select_btdevice)
                        for(device in pairedDevices) {
                            deviceList.add(device)
                            deviceNameList.add(device.name + "\n" + device.address)
                        }
                        val deviceListArray = arrayOfNulls<String>(deviceNameList.size)
                        deviceNameList.toArray(deviceListArray)

                        dialogBuilder.setItems(deviceListArray) { _, which ->
                            //Toast.makeText(this, deviceList.get(which), Toast.LENGTH_LONG).show()
                            try {
                                cleanupBtConnect()
                                // SPP
                                connectedDevice = deviceList.get(which)
                                btRfcommSocket = (connectedDevice as BluetoothDevice).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
                                (btRfcommSocket as BluetoothSocket).connect()
                                //btOutStream = (btRfcommSocket as BluetoothSocket).outputStream
                                //btInStream = (btRfcommSocket as BluetoothSocket).inputStream
                                if(theDriver == null) theDriver = LoganDriver((btRfcommSocket as BluetoothSocket).inputStream, (btRfcommSocket as BluetoothSocket).outputStream, monitorQueue)
                                beginReceive()
                                if(!(theDriver as LoganDriver).init()) throw Exception("Init failed")
                            } catch (e: Exception) {
                                var deviceName = "(Unknown)"
                                if(connectedDevice != null) deviceName = (connectedDevice as BluetoothDevice).name
                                Snackbar.make(view, "Error connecting device " + deviceName + ":\n" + e.toString(), Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show()
                            }
                        }
                        dialogBuilder.create().show()
                    }
                }
            }
        }

        testButton.setOnClickListener { view ->
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
