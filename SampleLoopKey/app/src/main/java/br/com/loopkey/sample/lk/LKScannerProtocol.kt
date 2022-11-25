package br.com.loopkey.sample.lk

interface LKScannerProtocol
{
    fun didUpdateVisible(visibleDevices: List<LKScanResult>)
}