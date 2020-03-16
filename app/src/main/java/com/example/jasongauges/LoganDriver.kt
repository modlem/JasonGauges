package com.example.jasongauges

import android.os.SystemClock
import android.renderscript.ScriptGroup
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

class LoganDriver(val inStream: InputStream, val outStream: OutputStream, val monitor: Queue<ByteArray>?= null) {
    var mask = arrayOf(0, 0x7F8, 0, 0x7F8)
        set(value) {
            if(value.size % 2 == 0) field = value
            else field = arrayOf(0, 0x7F8, 0, 0x7F8)
        }
    var filt = arrayOf(0, 0x7E8, 0, 0x7E8, 0, 0x7E8, 0, 0x7E8, 0, 0x7E8, 0, 0x7E8)
        set(value) {
            if(value.size % 2 == 0) field = value
            else field = arrayOf(0, 0x7E8, 0, 0x7E8, 0, 0x7E8, 0, 0x7E8, 0, 0x7E8, 0, 0x7E8)
        }
    private val cmdEscape = "+++"
    private val cmdExit = "AT+Q\r\n"
    private val cmdCanRate = "AT+C=16\r\n"
    private val cmdSetMask = "AT+M="
    private val cmdSetFilt = "AT+F="

    var ioTimeoutMillis: Long = 1000
    var retryCount = 3

    fun inputFlush() {
        try {
            val bytesAvail = (inStream as InputStream).available()
            if(bytesAvail > 0) {
                val tmpByteArr = ByteArray(bytesAvail)
                (inStream as InputStream).read(tmpByteArr)
                if(monitor != null) monitor.add(tmpByteArr)
            }
        } catch (e: Exception) {
        }
    }

    private fun waitForOkay(): Boolean {
        return waitForOkay("OK".toByteArray(Charsets.US_ASCII))
    }

    private fun _indexOf(outerArray: ByteArray, smallerArray: ByteArray): Int {
        for(i in 0 until outerArray.size - smallerArray.size + 1) {
            var found = true
            for(j in 0 until smallerArray.size) {
                if(outerArray[i+j] != smallerArray[j]) {
                    found = false
                    break
                }
            }
            if(found) return i
        }
        return -1
    }

    /**
     * Waiting for first *okStr* occurrence
     *
     * Keep searching for the first *okStr* occurrence until it is found
     *
     * Blocking
     *
     * @param okStr Array to search for
     * @return Index of the first *okStr* position; -1 if not found
     */
    private fun waitForOkay(okStr: ByteArray): Boolean {
        val startTime = SystemClock.elapsedRealtime()
        var byteBufStr: ByteArray ?= null

        try {
            do {
                val bytesAvail = (inStream as InputStream).available()
                if(bytesAvail > 0) {
                    val tmpByteArr = ByteArray(bytesAvail)
                    (inStream as InputStream).read(tmpByteArr)
                    if(byteBufStr == null) byteBufStr = tmpByteArr
                    else byteBufStr += tmpByteArr
                    if(monitor != null) monitor.add(tmpByteArr)

                    if(byteBufStr.size >= okStr.size) {
                        if (_indexOf(byteBufStr, okStr) >= 0) return true
                    }
                }
            } while(SystemClock.elapsedRealtime() - startTime < ioTimeoutMillis)
        } catch (e: Exception) {
        }

        return false
    }

    private fun send(data: ByteArray) {
        if(data.isNotEmpty()) {
            outStream.write(data)
            if(monitor != null) monitor.add(">>> ".toByteArray() + data)
        }
    }

    private fun send(data: String) {
        if(data.isNotEmpty()) send(data.toByteArray(Charsets.US_ASCII))
    }

    private fun getMaskStr(): ArrayList<String> {
        val rtnString = ArrayList<String>()

        for(i in 0 until mask.size/2) {
            rtnString.add("$cmdSetMask[$i][${mask[2 * i]}][${String.format("%08X", mask[2*i+1])}]\r\n")
        }

        return rtnString
    }

    private fun getFiltStr(): ArrayList<String> {
        val rtnString = ArrayList<String>()

        for(i in 0 until filt.size/2) {
            rtnString.add("$cmdSetFilt[$i][${filt[2 * i]}][${String.format("%08X", filt[2*i+1])}]\r\n")
        }

        return rtnString
    }

    private fun sendCmd(cmd: String, okResponse: String): Boolean {
        return sendCmd(cmd.toByteArray(Charsets.US_ASCII), okResponse.toByteArray(Charsets.US_ASCII))
    }

    private fun sendCmd(cmd: ByteArray, okResponse: ByteArray): Boolean {
        inputFlush()
        send(cmd)
        return waitForOkay(okResponse)
    }

    /** Blocking **/
    fun setMask(): Boolean {
        val maskStr = getMaskStr()
        var successCount = 0
        var terminate = false

        if (maskStr.size > 0) {
            maskStr.forEach {
                if (!terminate) {
                    for (i in 1..retryCount) {
                        if(sendCmd(it, ("OK"))) {
                            successCount++
                            break
                        }
                        if (i >= retryCount) {
                            terminate = true
                        }
                    }
                }
            }
        }

        return successCount == maskStr.size
    }

    /** Blocking **/
    fun setFilter(): Boolean {
        val filtStr = getFiltStr()
        var successCount = 0
        var terminate = false

        if (filtStr.size > 0) {
            filtStr.forEach {
                if (!terminate) {
                    for (i in 1..retryCount) {
                        if(sendCmd(it, ("OK"))) {
                            successCount++
                            break
                        }
                        if (i >= retryCount) {
                            terminate = true
                        }
                    }
                }
            }
        }

        return successCount == filtStr.size
    }

    /** Blocking **/
    fun setCanRate(): Boolean {
        for (i in 1..retryCount) {
            if(sendCmd(cmdCanRate, "OK")) {
                return true
            }
        }

        return false
    }

    /** Blocking **/
    fun enterCmdMode(): Boolean {
        for (i in 1..retryCount) {
            if(sendCmd(cmdEscape, "ENTER INTO SETTING MODE")) {
                return true
            }
        }

        return false
    }

    fun exitCmdMode(): Boolean {
        for (i in 1..retryCount) {
            if(sendCmd(cmdExit, "ENTER DATA MODE")) {
                return true
            }
        }

        return false
    }

    fun init(): Boolean {
        if(!enterCmdMode()) return false
        if(!setCanRate()) return false
        if(!setMask()) return false
        if(!setFilter()) return false
        if(!exitCmdMode()) return false

        return true
    }
}